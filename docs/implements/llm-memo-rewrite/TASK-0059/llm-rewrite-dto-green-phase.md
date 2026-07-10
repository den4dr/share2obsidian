# TDD Greenフェーズ記録: LlmRewriteResult・ChatCompletion DTO実装

**機能名**: llm-rewrite-dto
**タスクID**: TASK-0059
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装方針

- Redフェーズで作成された14件のテスト（`ChatCompletionDtoTest.kt` 9件, `LlmRewriteResultTest.kt` 5件）を通すために、要件定義書・タスク定義書に明記済みの型をそのまま実装した。推測による設計変更は行っていない（🔵優勢）。
- `LlmRewriteResult` は sealed class とし、`Success(text: String)` と `sealed class Failure`（`abstract val messageResId: Int` を各サブクラスで override）を定義。
- DTO 4種（`ChatCompletionRequestDto` / `ChatMessageDto` / `ChatCompletionResponseDto` / `ChoiceDto`）はすべて `@Serializable` data class とし、プロパティ名をそのまま JSON キーとして使用する最小構成にした。
- 「とりあえず動く」レベルの最小実装というより、要件定義書に確定した型定義がそのまま最終形であるため、複雑化の余地はほぼない。

## 2. 実装コード全文

### 2.1 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt`

```kotlin
package com.den4dr.share2Obsidian.data.llm

/**
 * 【機能概要】: LLM呼び出し（メモ本文の書き換え等）の結果を表す sealed class
 * 【実装方針】: 成功時はテキストを保持する Success、失敗時は種別ごとに表示用 messageResId を
 *   保持する Failure サブクラスを定義する。sealed class にすることで when 式が exhaustive
 *   （分岐漏れをコンパイル時に検出可能）になる。
 * 【テスト対応】: LlmRewriteResultTest.kt の TC-N-04〜06, TC-B-04〜05 を通すための実装
 * 🔵 信頼性レベル: interfaces.kt「LLM 呼び出し結果（新規）」・requirements.md 2.1 に基づく（推測なし）
 */
sealed class LlmRewriteResult {

    /**
     * 【機能概要】: LLM呼び出しが成功したことを表し、書き換え/生成されたテキストを保持する
     * 【テスト対応】: TC-N-04（text保持）, TC-B-04（空文字保持）, TC-B-05（等価性）
     * 🔵 信頼性レベル: interfaces.kt 130行・REQ-003 に基づく
     */
    data class Success(val text: String) : LlmRewriteResult()

    /**
     * 【機能概要】: LLM呼び出しが失敗したことを表す sealed class
     * 【実装方針】: 全ての失敗種別が表示用 string resource ID（messageResId）を保持する
     *   共通契約として abstract val を宣言し、各サブクラスで override する
     * 【テスト対応】: TC-N-05（各サブクラスのmessageResId保持）, TC-N-06（exhaustive when）
     * 🔵 信頼性レベル: interfaces.kt 133-149行・note.md 243-247行に基づく
     */
    sealed class Failure : LlmRewriteResult() {
        // 【共通プロパティ】: 表示用 string resource ID（R.string.*）。実際の値割当はTASK-0060で行う
        abstract val messageResId: Int

        // 【失敗種別】: ネットワーク未接続・接続エラー（EDGE-001） 🔵
        data class NetworkError(override val messageResId: Int) : Failure()

        // 【失敗種別】: APIキー不正・認証エラー（HTTP 401/403）（EDGE-002） 🔵
        data class AuthError(override val messageResId: Int) : Failure()

        // 【失敗種別】: 30秒タイムアウト（EDGE-003, NFR-001） 🔵
        data class Timeout(override val messageResId: Int) : Failure()

        // 【失敗種別】: 空応答・パース不能なレスポンス（EDGE-004） 🟡
        data class EmptyOrInvalidResponse(override val messageResId: Int) : Failure()

        // 【失敗種別】: 上記以外の予期しないエラー 🟡 一般的なエラーハンドリング方針からの推測
        data class Unknown(override val messageResId: Int) : Failure()
    }
}
```

### 2.2 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`

```kotlin
package com.den4dr.share2Obsidian.data.llm.dto

import kotlinx.serialization.Serializable

/**
 * 【機能概要】: OpenAI互換 Chat Completions API のリクエストボディを表す DTO
 * 【実装方針】: kotlinx.serialization でJSONへエンコード可能な最小構成とする。
 *   プロパティ名をそのままJSONキーとして使用する（snake_case変換なし）。
 *   temperature 等の生成パラメータは含めない。
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-01, TC-B-02 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「リクエスト仕様」に基づく（推測なし）
 */
@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
)

/**
 * 【機能概要】: Chat Completions のメッセージ（role/content のペア）を表す DTO
 * 【実装方針】: リクエスト・レスポンスの双方で共通利用する最小構成の型とする
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-01〜03, TC-B-03 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「リクエスト仕様」表に基づく（推測なし）
 */
@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

/**
 * 【機能概要】: OpenAI互換 Chat Completions API のレスポンスボディを表す DTO
 * 【実装方針】: choices のみを持つ最小構成とする。未使用フィールド（id/usage等）はデコード時に
 *   Json { ignoreUnknownKeys = true } 設定側で無視される前提のため、DTOには含めない。
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-02, TC-E-01〜03, TC-B-01 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「レスポンス仕様」に基づく（推測なし）
 */
@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto>,
)

/**
 * 【機能概要】: Chat Completions レスポンス内の1候補（choice）を表す DTO
 * 【実装方針】: message のみを持つ最小構成とする（finish_reason 等は使用しない）
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-02, TC-E-01, TC-E-03 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「レスポンス仕様」に基づく（推測なし）
 */
@Serializable
data class ChoiceDto(
    val message: ChatMessageDto,
)
```

---

## 3. テスト実行結果

### 実行コマンド

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.data.llm.ChatCompletionDtoTest" --tests "com.den4dr.share2Obsidian.data.llm.LlmRewriteResultTest"
```

### 結果

`BUILD SUCCESSFUL`。テストレポート（`app/build/test-results/testDebugUnitTest/`）確認済み。

| テストクラス | tests | failures | errors | skipped |
|---|---|---|---|---|
| `ChatCompletionDtoTest` | 9 | 0 | 0 | 0 |
| `LlmRewriteResultTest` | 5 | 0 | 0 | 0 |

合計14件全て成功（Redフェーズで定義した TC-N-01〜06, TC-E-01〜03, TC-B-01〜05 を全件通過）。

コンパイル時の警告（機能に影響なし）:
- `LlmRewriteResultTest.kt:65:20` / `:114:20`: `Check for instance is always 'true'`（`is LlmRewriteResult` の判定が静的に自明であることによる警告。テストコード側の意図的な明示チェックであり実装側の問題ではない）

---

## 4. 課題・改善点（Refactorフェーズで対応candidate）

- 現時点で要件定義に忠実な最小実装であり、大きな重複やコード臭は見当たらない。
- Refactorフェーズで検討し得る点:
  - `LlmRewriteResult.Failure` の5サブクラスは構造がほぼ同一（`messageResId: Int` のみ）であり、将来的にメッセージ種別を enum 化する設計もあり得るが、TASK-0060 で例外→Failureマッピングの実装内容を見てから判断するのが適切（現段階での変更は時期尚早）。
  - KDoc コメントと日本語運用コメント（`// 【...】`）が両方存在しやや冗長。プロジェクト全体の既存コメント密度と揃える程度の軽微な整理は可能。
  - ファイルサイズ: `LlmRewriteResult.kt` 47行、`ChatCompletionDto.kt` 53行。800行制限に対し十分小さく、分割不要。
- 実装コードにモック・スタブは含まれていない（型定義のみのため該当なし）。

## 5. 品質判定

| 観点 | 評価 |
|------|------|
| テスト結果 | ✅ 14件全て成功（failures=0, errors=0） |
| 実装のシンプルさ | ✅ 要件定義書の型定義をそのまま反映した最小構成 |
| リファクタ箇所 | ✅ 明確（コメント整理程度、構造変更は不要） |
| 機能的問題 | なし |
| ファイルサイズ | ✅ 800行以下（47行・53行） |
| モック使用 | ✅ 実装コードにモック・スタブなし |

**総合判定**: ✅ 高品質
