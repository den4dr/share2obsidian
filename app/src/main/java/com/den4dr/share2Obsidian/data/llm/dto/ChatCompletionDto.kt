package com.den4dr.share2Obsidian.data.llm.dto

import kotlinx.serialization.Serializable

/**
 * 【機能概要】: OpenAI互換 Chat Completions API のリクエストボディを表す DTO
 * 【設計方針】: kotlinx.serialization でJSONへエンコード可能な最小構成とする。
 *   プロパティ名をそのままJSONキーとして使用する（snake_case変換なし）。
 *   temperature 等の生成パラメータは含めない。
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-01, TC-B-02 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「リクエスト仕様」に基づく（推測なし）
 *
 * @property model 使用するLLMモデル名（`LlmSettings.model` 由来）
 * @property messages system/userロールのメッセージ列。空リストでもエンコード可能（TC-B-02）
 */
@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
)

/**
 * 【機能概要】: Chat Completions のメッセージ（role/content のペア）を表す DTO
 * 【設計方針】: リクエスト・レスポンスの双方で共通利用する最小構成の型とする。
 *   `content` は nullable とする。リクエスト送信時は常に非null文字列を設定するが、
 *   レスポンス側では一部プロバイダーが `content: null` を返す挙動があり得るため
 *   （TASK-0060 TC-11・EDGE-004）、null を許容してデシリアライズ時の例外を防ぐ。
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-01〜03, TC-B-03 / LlmRewriteRepositoryImplTest.kt の TC-11 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「リクエスト仕様」表に基づく（推測なし）。
 *   content の nullable 化は note.md 初期設計（`MessageDto(content: String?)`）・TASK-0060 TC-11 より 🟡
 *
 * @property role メッセージの役割（`"system"` / `"user"` / `"assistant"`）
 * @property content メッセージ本文。空文字でも欠落せず往復で保持される（TC-B-03）。
 *   レスポンス側で `null` が返る場合がある（TC-11）
 */
@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String?,
)

/**
 * 【機能概要】: OpenAI互換 Chat Completions API のレスポンスボディを表す DTO
 * 【設計方針】: choices のみを持つ最小構成とする。未使用フィールド（id/usage等）はデコード時に
 *   Json { ignoreUnknownKeys = true } 設定側で無視される前提のため、DTOには含めない。
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-02, TC-E-01〜03, TC-B-01 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「レスポンス仕様」に基づく（推測なし）
 *
 * @property choices LLMからの応答候補一覧。空リストになり得る（TC-B-01, EDGE-004）
 */
@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto>,
)

/**
 * 【機能概要】: Chat Completions レスポンス内の1候補（choice）を表す DTO
 * 【設計方針】: message のみを持つ最小構成とする（finish_reason 等は使用しない）
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-02, TC-E-01, TC-E-03 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「レスポンス仕様」に基づく（推測なし）
 *
 * @property message この候補のメッセージ本体（role/content）
 */
@Serializable
data class ChoiceDto(
    val message: ChatMessageDto,
)
