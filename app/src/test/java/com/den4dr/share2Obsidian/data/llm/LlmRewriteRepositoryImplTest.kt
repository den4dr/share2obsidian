package com.den4dr.share2Obsidian.data.llm

import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.data.llm.dto.ChatCompletionRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException

/**
 * [LlmRewriteRepositoryImpl] の `rewrite()` メソッドを検証するユニットテスト（TASK-0060）。
 *
 * 【テスト対象】: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`（未実装）
 * このファイルは Red フェーズの時点では [LlmRewriteRepository] / [LlmRewriteRepositoryImpl] が
 * 存在しないためコンパイルに失敗する。これは意図した Red フェーズの状態である
 * （TASK-0058 Red フェーズと同様の方針）。
 *
 * 【モック方式】: testcases.md の設計判断に基づき、Ktor `HttpClient` の拡張関数・inline関数の
 * 多用により MockK での安定モックが困難なため、単体テストでも Ktor `MockEngine` を採用する。
 *
 * 【Robolectric採用理由】: TC-09（APIキーのログ非出力確認）で `android.util.Log` の出力有無を
 * `ShadowLog` で検証するため、クラス全体で Robolectric を使用する（既存 sdk=[34] 明示規約に従う）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LlmRewriteRepositoryImplTest {

    private val defaultSettings = LlmSettings(
        endpointUrl = "https://api.example.com/v1/chat/completions",
        apiKey = "sk-test",
        model = "gpt-4o-mini",
    )

    /** 【ヘルパー】: MockEngineのレスポンスハンドラから HttpClient を組み立てる共通処理 */
    private fun buildClient(
        expectSuccess: Boolean = false,
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(request: io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): HttpClient {
        val engine = MockEngine(handler)
        return HttpClient(engine) {
            this.expectSuccess = expectSuccess
            install(ContentNegotiation) { json() }
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // ------------------------------------------------------------------
    // 1. 正常系テストケース
    // ------------------------------------------------------------------

    // TC-01: 正常応答時に Success(text) を返す
    @Test
    fun `正常応答で書き換え結果テキストをSuccessとして返す`() = runTest {
        // 【テスト目的】: choices[0].message.content を Success として返すことを確認
        // 【テスト内容】: 200応答＋有効contentを返すMockEngineでrewrite()を呼び出す
        // 【期待される動作】: LlmRewriteResult.Success("書き換え結果") が返る
        // 🔵 信頼性レベル: TASK-0060.md 単体テストケース1・testcases.md TC-01

        // 【テストデータ準備】: 本文リライトの代表的成功応答を用意する
        // 【初期条件設定】: 有効なendpointUrl/apiKey/modelを持つLlmSettings
        val json = """{"choices":[{"message":{"role":"assistant","content":"書き換え結果"}}]}"""
        val httpClient = buildClient { respond(json, HttpStatusCode.OK, jsonHeaders) }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        // 【処理内容】: prompt/contentをmessagesに載せてPOSTし、レスポンスを解析する
        val result = repository.rewrite(defaultSettings, "以下の本文を読みやすく整形してください", "元の本文テキスト")

        // 【結果検証】: 返却型とtextを確認する
        assertEquals(LlmRewriteResult.Success("書き換え結果"), result) // 【確認内容】: 正常系マッピングの正確性
        httpClient.close()
    }

    // TC-02: タグ提案用途でも同一経路でSuccessを返す
    @Test
    fun `タグ提案用のプロンプトでも同じ経路でSuccessを返す`() = runTest {
        // 【テスト目的】: promptの用途に関わらず、応答contentをそのままSuccessに包むことを確認
        // 【テスト内容】: タグ提案用固定プロンプトでrewrite()を呼び出す
        // 【期待される動作】: プロンプト内容で分岐せずSuccess(応答content)が返る
        // 🟡 信頼性レベル: requirements.md 4.1「タグ提案」より妥当な推測

        // 【テストデータ準備】: タグ提案の代表的な応答（カンマ区切りタグ文字列）を用意
        val json = """{"choices":[{"message":{"role":"assistant","content":"kotlin, android, tdd"}}]}"""
        val httpClient = buildClient { respond(json, HttpStatusCode.OK, jsonHeaders) }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: タグ提案用プロンプトでrewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "この文章に適したタグをカンマ区切りで提案してください", "元コンテンツ")

        // 【結果検証】: プロンプト用途に依存せず応答contentがそのまま返ること
        assertEquals(LlmRewriteResult.Success("kotlin, android, tdd"), result) // 【確認内容】: プロンプト差異で分岐しないこと
        httpClient.close()
    }

    // ------------------------------------------------------------------
    // 2. 異常系テストケース
    // ------------------------------------------------------------------

    // TC-03: 401応答時にAuthErrorを返す
    @Test
    fun `401Unauthorized応答でAuthErrorを返す`() = runTest {
        // 【テスト目的】: 401応答（ClientRequestException）がAuthErrorへマッピングされることを確認
        // 【テスト内容】: expectSuccess=trueのHttpClientで401応答を返すMockEngineを使用
        // 【期待される動作】: Failure.AuthError(R.string.error_llm_auth)が返る
        // 🔵 信頼性レベル: TASK-0060.md 単体テストケース2・EDGE-002・testcases.md TC-03

        // 【テストデータ準備】: APIキー誤りを想定した401応答を用意
        val httpClient = buildClient(expectSuccess = true) {
            respond("""{"error":"invalid_api_key"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: AuthErrorへの分類とmessageResIdの一致を確認
        assertTrue(result is LlmRewriteResult.Failure.AuthError) // 【確認内容】: 401がAuthErrorへ分類されること
        assertEquals(
            R.string.error_llm_auth,
            (result as LlmRewriteResult.Failure.AuthError).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_authであること
        httpClient.close()
    }

    // TC-04: 403応答時にAuthErrorを返す
    @Test
    fun `403Forbidden応答でAuthErrorを返す`() = runTest {
        // 【テスト目的】: 403応答も401と同一のAuthErrorへ集約されることを確認
        // 【テスト内容】: expectSuccess=trueのHttpClientで403応答を返すMockEngineを使用
        // 【期待される動作】: Failure.AuthError(R.string.error_llm_auth)が返る
        // 🔵 信頼性レベル: api-endpoints.md表「401/403 → AuthError」・testcases.md TC-04

        // 【テストデータ準備】: 権限不足を想定した403応答を用意
        val httpClient = buildClient(expectSuccess = true) {
            respond("""{"error":"forbidden"}""", HttpStatusCode.Forbidden, jsonHeaders)
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: 403もAuthErrorへ分類されることを確認
        assertTrue(result is LlmRewriteResult.Failure.AuthError) // 【確認内容】: 403もAuthErrorに分類される分岐網羅
        assertEquals(
            R.string.error_llm_auth,
            (result as LlmRewriteResult.Failure.AuthError).messageResId,
        ) // 【確認内容】: 401と同一のmessageResIdであること
        httpClient.close()
    }

    // TC-05: HttpRequestTimeoutException発生時にTimeoutを返す
    @Test
    fun `HttpRequestTimeoutException発生時にTimeoutを返す`() = runTest {
        // 【テスト目的】: タイムアウト例外がTimeoutへマッピングされることを確認
        // 【テスト内容】: MockEngineがHttpRequestTimeoutExceptionを直接送出する状況を再現する
        //   （実際の30秒遅延によるタイムアウト実効性はIT-02の統合テストで担保する）
        // 【期待される動作】: Failure.Timeout(R.string.error_llm_timeout)が返る
        // 🔵 信頼性レベル: TASK-0060.md 単体テストケース3・EDGE-003・NFR-001・testcases.md TC-05

        // 【テストデータ準備】: HttpRequestTimeoutExceptionを送出するMockEngineを用意
        val httpClient = buildClient { throw HttpRequestTimeoutException(HttpRequestBuilder()) }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: catch順序によりHttpRequestTimeoutExceptionがIOException分岐より先に捕捉されること
        assertTrue(result is LlmRewriteResult.Failure.Timeout) // 【確認内容】: タイムアウト例外がTimeoutへ分類されること
        assertEquals(
            R.string.error_llm_timeout,
            (result as LlmRewriteResult.Failure.Timeout).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_timeoutであること
        httpClient.close()
    }

    // TC-06: IOException発生時にNetworkErrorを返す
    @Test
    fun `IOException発生時にNetworkErrorを返す`() = runTest {
        // 【テスト目的】: 接続不能（IOException）がNetworkErrorへマッピングされることを確認
        // 【テスト内容】: MockEngineがIOExceptionを直接送出する状況を再現する
        // 【期待される動作】: Failure.NetworkError(R.string.error_llm_network)が返る
        // 🔵 信頼性レベル: TASK-0060.md 単体テストケース4・EDGE-001・testcases.md TC-06

        // 【テストデータ準備】: 名前解決失敗・接続拒否を想定したIOExceptionを送出するMockEngineを用意
        val httpClient = buildClient { throw IOException("Unable to resolve host") }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: IOExceptionがNetworkErrorへ分類されることを確認
        assertTrue(result is LlmRewriteResult.Failure.NetworkError) // 【確認内容】: 接続不能がNetworkErrorへ分類されること
        assertEquals(
            R.string.error_llm_network,
            (result as LlmRewriteResult.Failure.NetworkError).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_networkであること
        httpClient.close()
    }

    // TC-07: 500応答（ServerResponseException）時にUnknownを返す
    @Test
    fun `500InternalServerError応答でUnknownを返す`() = runTest {
        // 【テスト目的】: 認証・ネットワーク・タイムアウト以外の予期しない失敗がUnknownへ集約されることを確認
        // 【テスト内容】: expectSuccess=trueのHttpClientで500応答を返すMockEngineを使用
        // 【期待される動作】: Failure.Unknown(R.string.error_llm_unknown)が返る
        // 🟡 信頼性レベル: api-endpoints.md「その他の予期しない例外→Unknown」・testcases.md TC-07より妥当な推測

        // 【テストデータ準備】: LLMサーバー内部エラーを想定した500応答を用意
        val httpClient = buildClient(expectSuccess = true) {
            respond("""{"error":"internal_error"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: 5xxがAuthError等に誤分類されずUnknownへ落ちることを確認
        assertTrue(result is LlmRewriteResult.Failure.Unknown) // 【確認内容】: 5xxがUnknownへ分類されること
        assertEquals(
            R.string.error_llm_unknown,
            (result as LlmRewriteResult.Failure.Unknown).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_unknownであること
        httpClient.close()
    }

    // TC-08: 429応答（レート制限）時にUnknownを返す
    @Test
    fun `429TooManyRequests応答でUnknownを返す`() = runTest {
        // 【テスト目的】: 401/403以外の4xx（429）がAuthErrorではなくUnknownへ入る分岐を確認
        // 【テスト内容】: expectSuccess=trueのHttpClientで429応答を返すMockEngineを使用
        // 【期待される動作】: Failure.Unknown(R.string.error_llm_unknown)が返る
        // 🟡 信頼性レベル: api-endpoints.md「レート制限」節（429→Unknown）・testcases.md TC-08より妥当な推測

        // 【テストデータ準備】: レート制限超過を想定した429応答を用意
        val httpClient = buildClient(expectSuccess = true) {
            respond("""{"error":"rate_limited"}""", HttpStatusCode.TooManyRequests, jsonHeaders)
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: 429がAuthErrorと誤認されずUnknownへ分類されることを確認
        assertTrue(result is LlmRewriteResult.Failure.Unknown) // 【確認内容】: status==401||403以外の4xxがUnknown分岐に入ること
        assertEquals(
            R.string.error_llm_unknown,
            (result as LlmRewriteResult.Failure.Unknown).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_unknownであること
        httpClient.close()
    }

    // TC-09: 失敗時にAPIキーを含む機微情報をログへ出力しない（NFR-102）
    @Test
    fun `認証失敗時にAPIキーを含む機微情報をログへ出力しない`() = runTest {
        // 【テスト目的】: NFR-102（機微情報のログ出力禁止）の遵守を確認する
        // 【テスト内容】: apiKeyに識別可能な文字列を設定して認証失敗（401）を発生させ、
        //   Robolectric ShadowLogに記録された全ログ（メッセージ・例外メッセージ）にapiKeyが含まれないことを確認する
        // 【期待される動作】: Failure.AuthErrorが返り、かつログにapiKey文字列が一切出現しない
        // 🟡 信頼性レベル: NFR-102・requirements.md 3.2・testcases.md TC-09より
        //   （検証手段はログが存在しない前提の「非包含」確認に置換。要件に検証手段の直接規定なし）

        // 【テスト前準備】: 前のテストのログ蓄積の影響を受けないようクリアする
        ShadowLog.clear()

        // 【テストデータ準備】: 露出してはならない一意なAPIキー文字列を設定
        val secretSettings = defaultSettings.copy(apiKey = "sk-secret-should-not-leak")
        val httpClient = buildClient(expectSuccess = true) {
            respond("""{"error":"invalid_api_key"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出し認証エラーを発生させる
        val result = repository.rewrite(secretSettings, "prompt", "content")

        // 【結果検証】: 戻り値の型とログへのAPIキー非包含を確認
        assertTrue(result is LlmRewriteResult.Failure.AuthError) // 【確認内容】: 想定通りAuthErrorが返ること
        val leaked = ShadowLog.getLogs().any { logItem ->
            logItem.msg?.contains("sk-secret-should-not-leak") == true ||
                logItem.throwable?.message?.contains("sk-secret-should-not-leak") == true
        }
        assertFalse(leaked) // 【確認内容】: logcatにAPIキー文字列が一切出力されていないこと
        httpClient.close()
    }

    // ------------------------------------------------------------------
    // 3. 境界値テストケース
    // ------------------------------------------------------------------

    // TC-10: choicesが空配列のときEmptyOrInvalidResponseを返す
    @Test
    fun `choices空配列応答でEmptyOrInvalidResponseを返す`() = runTest {
        // 【テスト目的】: 200成功でも中身が空なら失敗として扱う一貫性を確認
        // 【テスト内容】: choices:[]を含む200応答でrewrite()を呼び出す
        // 【期待される動作】: Failure.EmptyOrInvalidResponse(R.string.error_llm_empty_response)が返る
        // 🟡 信頼性レベル: TASK-0060.md 単体テストケース5・EDGE-004・testcases.md TC-10

        // 【テストデータ準備】: choicesが空のプロバイダー異常応答を用意
        val httpClient = buildClient { respond("""{"choices":[]}""", HttpStatusCode.OK, jsonHeaders) }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: choices.firstOrNull()がnullとなりEmptyOrInvalidResponseへ分類されることを確認
        assertTrue(result is LlmRewriteResult.Failure.EmptyOrInvalidResponse) // 【確認内容】: 空choicesが失敗として扱われること
        assertEquals(
            R.string.error_llm_empty_response,
            (result as LlmRewriteResult.Failure.EmptyOrInvalidResponse).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_empty_responseであること
        httpClient.close()
    }

    // TC-11: content が null のとき EmptyOrInvalidResponse を返す
    @Test
    fun `messageContentがnullのときEmptyOrInvalidResponseを返す`() = runTest {
        // 【テスト目的】: choicesは存在するがcontentがnullという境界を失敗として扱うことを確認
        // 【テスト内容】: content:nullを含む200応答でrewrite()を呼び出す
        // 【期待される動作】: Failure.EmptyOrInvalidResponseが返り、NPEを発生させない
        // 🟡 信頼性レベル: TASK-0060.md 実装詳細5・ChatCompletionDtoのcontent:String?より妥当な推測

        // 【テストデータ準備】: content:nullを含むプロバイダー異常応答を用意
        val httpClient = buildClient {
            respond("""{"choices":[{"message":{"role":"assistant","content":null}}]}""", HttpStatusCode.OK, jsonHeaders)
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: null contentがSuccessとして扱われずEmptyOrInvalidResponseへ分類されることを確認
        assertTrue(result is LlmRewriteResult.Failure.EmptyOrInvalidResponse) // 【確認内容】: null contentを失敗として扱うこと
        assertEquals(
            R.string.error_llm_empty_response,
            (result as LlmRewriteResult.Failure.EmptyOrInvalidResponse).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_empty_responseであること
        httpClient.close()
    }

    // TC-12: content が空文字のとき EmptyOrInvalidResponse を返す
    @Test
    fun `messageContentが空文字のときEmptyOrInvalidResponseを返す`() = runTest {
        // 【テスト目的】: nullではないが長さ0という空文字境界を失敗として扱うことを確認
        // 【テスト内容】: content:""を含む200応答でrewrite()を呼び出す
        // 【期待される動作】: Failure.EmptyOrInvalidResponseが返り、Success("")にならない
        // 🟡 信頼性レベル: TASK-0060.md 実装詳細5・api-endpoints.md「content が null/空文字→EmptyOrInvalidResponse」より

        // 【テストデータ準備】: content:""を含むプロバイダー異常応答を用意
        val httpClient = buildClient {
            respond("""{"choices":[{"message":{"role":"assistant","content":""}}]}""", HttpStatusCode.OK, jsonHeaders)
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "prompt", "content")

        // 【結果検証】: 空文字contentがSuccess("")として扱われずEmptyOrInvalidResponseへ分類されることを確認
        assertTrue(result is LlmRewriteResult.Failure.EmptyOrInvalidResponse) // 【確認内容】: 空文字contentを失敗として扱うこと（null:TC-11と同一結果）
        assertEquals(
            R.string.error_llm_empty_response,
            (result as LlmRewriteResult.Failure.EmptyOrInvalidResponse).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_empty_responseであること
        httpClient.close()
    }

    // TC-13: 入力contentが空文字でもそのまま送信しSuccessを返す（EDGE-101）
    @Test
    fun `入力contentが空文字でも検証せずそのまま送信しSuccessを返す`() = runTest {
        // 【テスト目的】: リポジトリは入力content（引数）を検証・加工しない責務境界を確認
        // 【テスト内容】: content=""でrewrite()を呼び出し、送信された実際のリクエストボディを検査する
        // 【期待される動作】: Success(応答content)が返り、送信ボディのmessages[1].contentが""のまま送られる
        // 🔵 信頼性レベル: requirements.md 4.2・EDGE-101・testcases.md TC-13

        // 【テストデータ準備】: 送信されたリクエストボディを捕捉するMockEngineを用意
        var capturedBody: String? = null
        val httpClient = buildClient { request ->
            val content = request.body
            capturedBody = if (content is OutgoingContent.ByteArrayContent) {
                content.bytes().decodeToString()
            } else {
                null
            }
            respond("""{"choices":[{"message":{"role":"assistant","content":"生成結果"}}]}""", HttpStatusCode.OK, jsonHeaders)
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: content=""でrewrite()を呼び出す
        val result = repository.rewrite(defaultSettings, "何か生成してください", "")

        // 【結果検証】: 応答Successと、送信ボディのuserメッセージcontentが空文字であることを確認
        assertEquals(LlmRewriteResult.Success("生成結果"), result) // 【確認内容】: 入力空文字でも応答をSuccessとして返すこと
        val requestDto = Json.decodeFromString<ChatCompletionRequestDto>(requireNotNull(capturedBody))
        assertEquals("", requestDto.messages[1].content) // 【確認内容】: 入力content=""がバリデーションされず、そのまま送信ボディに反映されること
        httpClient.close()
    }
}
