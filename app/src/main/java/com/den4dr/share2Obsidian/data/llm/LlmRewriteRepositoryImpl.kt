package com.den4dr.share2Obsidian.data.llm

import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.data.llm.dto.ChatCompletionRequestDto
import com.den4dr.share2Obsidian.data.llm.dto.ChatCompletionResponseDto
import com.den4dr.share2Obsidian.data.llm.dto.ChatMessageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException

/**
 * 【機能概要】: Ktor Client (CIOエンジン) を用いて OpenAI互換 Chat Completions API を呼び出し、
 *              成功/失敗を [LlmRewriteResult] にマッピングするリポジトリ実装
 * 【改善内容】: Green フェーズ実装から、(1) リクエスト構築 (2) 応答解析 (3) 認証ステータス判定の
 *              3責務をそれぞれ専用のプライベート関数へ抽出し、`rewrite()` 本体を
 *              「送信して結果をマッピングする」制御フローのみに単純化した
 *              （機能・戻り値・catch順序は変更なし、可読性のみ向上）
 * 【設計方針】: 例外は「より具体的な例外を先に捕捉する」順序（Timeout → ClientRequestException →
 *              IOException → Exception）で catch し、それぞれ対応する Failure サブクラスへ変換する。
 *              `HttpRequestTimeoutException` は `IOException` を継承するため、IOException の
 *              catch節より前に配置する必要がある（Red フェーズ記録・note.md「開発ルール」より）。
 * 【パフォーマンス】: 各呼び出しはHTTP1往復のみで、ループや重い計算は行わない。
 *              例外分岐はいずれもO(1)の比較のみで、通信遅延（ネットワークI/O）が支配的コストである。
 * 【保守性】: 例外オブジェクト・APIキーを一切ログ出力しない実装とし、NFR-102の回帰を防止している。
 *              新たな認証対象ステータスコードの追加は [isAuthErrorStatus] の一箇所を変更するだけでよい。
 * 【テスト対応】: LlmRewriteRepositoryImplTest.kt（TC-01〜TC-13。リファクタ後も全13件成功を確認済み）・
 *              LlmRewriteRepositoryIntegrationTest.kt（IT-01, IT-02。コンパイル成功で暫定確認）
 * 🔵 信頼性レベル: requirements.md 2.1〜2.5・testcases.md・note.md「実装パターン」「エラーマッピング例」に基づく
 *
 * @param httpClient リクエスト送信に使用する HttpClient（DI側で生成・タイムアウト設定済みのものを注入する想定。
 *   TASK-0061 の責務。本クラスは HttpClient の生成・タイムアウト設定を行わない）
 */
class LlmRewriteRepositoryImpl(
    private val httpClient: HttpClient,
) : LlmRewriteRepository {

    /**
     * 【機能概要】: settings/prompt/content から OpenAI互換リクエストを構築して送信し、
     *              応答テキストを [LlmRewriteResult] として返す
     * 【実装方針】: 送信（[postChatCompletion]）・応答解析（[toRewriteResult]）を専用関数に委譲し、
     *              本関数は例外マッピングの制御フローに専念する。入力 content の検証・加工は行わない
     *              （EDGE-101, TC-13）。
     * 【テスト対応】: TC-01〜TC-13, IT-01, IT-02
     * 🔵 信頼性レベル: requirements.md 2.4/2.5・testcases.md エラーマッピング表に基づく
     */
    override suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult {
        return try {
            // 【リクエスト送信】: OpenAI互換 Chat Completions API へ POST し、応答をSuccess/Failureへ変換する 🔵
            postChatCompletion(settings, prompt, content).toRewriteResult()
        } catch (_: HttpRequestTimeoutException) {
            // 【タイムアウト捕捉】: HttpRequestTimeoutExceptionはIOExceptionを継承するため、
            // 下のIOException分岐より必ず先に配置する 🔵（EDGE-003, NFR-001, TC-05）
            LlmRewriteResult.Failure.Timeout(R.string.error_llm_timeout)
        } catch (e: ClientRequestException) {
            // 【認証エラー捕捉】: 401/403のみAuthErrorとし、それ以外の4xx（429等）はUnknownへ集約する 🔵（EDGE-002, TC-03/04/08）
            if (isAuthErrorStatus(e.response.status)) {
                LlmRewriteResult.Failure.AuthError(R.string.error_llm_auth)
            } else {
                LlmRewriteResult.Failure.Unknown(R.string.error_llm_unknown)
            }
        } catch (_: IOException) {
            // 【ネットワークエラー捕捉】: 接続不能・名前解決失敗等 🔵（EDGE-001, TC-06）
            LlmRewriteResult.Failure.NetworkError(R.string.error_llm_network)
        } catch (_: Exception) {
            // 【予期しない例外の捕捉】: 5xx（ServerResponseException）等、上記以外はUnknownへ集約する 🟡（TC-07）
            // 【セキュリティ】: 例外オブジェクト自体・APIキーはログ出力しない（NFR-102, TC-09）
            LlmRewriteResult.Failure.Unknown(R.string.error_llm_unknown)
        }
    }

    /**
     * 【ヘルパー関数】: OpenAI互換 Chat Completions API へリクエストを送信し、レスポンスDTOを取得する
     * 【再利用性】: rewrite() からのみ呼び出される想定だが、リクエスト構築の詳細を分離することで
     *              rewrite() 本体の見通しを良くする
     * 【単一責任】: 「HTTPリクエストを構築して送信する」責務のみを持ち、例外マッピングは呼び出し側に委ねる
     * 🔵 信頼性レベル: note.md「リクエスト構築パターン」・api-endpoints.md「リクエスト仕様」に基づく
     */
    private suspend fun postChatCompletion(
        settings: LlmSettings,
        prompt: String,
        content: String,
    ): ChatCompletionResponseDto =
        httpClient.post(settings.endpointUrl) {
            header(HttpHeaders.Authorization, "Bearer ${settings.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(
                ChatCompletionRequestDto(
                    model = settings.model,
                    messages = listOf(
                        // 【メッセージ構築】: system=prompt, user=content の固定順序（api-endpoints.md） 🟡
                        ChatMessageDto(role = "system", content = prompt),
                        ChatMessageDto(role = "user", content = content),
                    ),
                )
            )
        }.body()

    /**
     * 【ヘルパー関数】: レスポンスDTOから応答テキストを取り出し、[LlmRewriteResult] へ変換する
     * 【再利用性】: postChatCompletion() の戻り値に対してのみ使用する拡張関数として定義し、
     *              呼び出し元で `response.toRewriteResult()` と自然に読める形にする
     * 【単一責任】: 「choices が空、または content が null/空文字なら失敗として扱う」判定のみを担う
     * 🟡 信頼性レベル: api-endpoints.md「レスポンス仕様」・EDGE-004（空応答の扱いは直接確認していない）
     */
    private fun ChatCompletionResponseDto.toRewriteResult(): LlmRewriteResult {
        val text = choices.firstOrNull()?.message?.content
        return if (text.isNullOrEmpty()) {
            LlmRewriteResult.Failure.EmptyOrInvalidResponse(R.string.error_llm_empty_response)
        } else {
            LlmRewriteResult.Success(text)
        }
    }

    /**
     * 【ヘルパー関数】: HTTPステータスコードが認証エラー（401/403）に該当するかを判定する
     * 【再利用性】: ClientRequestException のハンドリングから利用する。将来的に認証対象の
     *              ステータスコードを追加・変更する場合はこの関数のみを修正すればよい
     * 【単一責任】: 「ステータスコード→認証エラーか否か」の判定のみを担う
     * 🔵 信頼性レベル: api-endpoints.md表「401/403 → AuthError」に基づく
     */
    private fun isAuthErrorStatus(status: HttpStatusCode): Boolean =
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden
}
