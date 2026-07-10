# TDD Refactorフェーズ記録: LlmRewriteResult・ChatCompletion DTO実装

**機能名**: llm-rewrite-dto
**タスクID**: TASK-0059
**要件名**: llm-memo-rewrite
**実施日**: 2026-07-06

---

## 1. リファクタリング方針

Greenフェーズの実装は要件定義書に確定済みの型をそのまま反映した最小構成であり、構造上の重複・責務混在・パフォーマンス上の懸念は見当たらなかった（`llm-rewrite-dto-green-phase.md` 4章）。そのため本フェーズでは**構造変更は行わず**、以下の観点でコメント品質のみを改善した。

- Green時点の課題候補: 「KDocコメントと日本語運用コメント（`// 【...】`）が両方存在しやや冗長」
- 対応方針: プロパティ単位の説明を KDoc の `@property` タグに統合し、単発の `//` コメントを `/** */` docコメントへ揃えることで、重複を減らしつつ既存の兄弟実装（`LlmSettings.kt`, TASK-0058）の `@property` スタイルに合わせた。
- 機能的な変更は一切行っていない（クラス構造・プロパティ・シリアライズ契約は Green フェーズと完全に同一）。

## 2. 改善内容（コメントのみ）

### 2.1 `LlmRewriteResult.kt`

- 🔵 クラスKDocに【保守性】の観点を追加し、「新規失敗種別追加時も exhaustive when が未対応分岐を検出する」という設計意図を明記した（`refactoring_guidelines` 1.可読性向上 に基づく改善、interfaces.kt の設計思想の言い換えであり推測ではない）。
- 🔵 `Success.text` / `Failure.messageResId` の説明を、クラスKDoc内 `@property` タグに統合。従来 `abstract val messageResId: Int` の直前にあった単独行コメント `// 【共通プロパティ】: ...` を削除し、情報をクラスKDocの `@property messageResId` に集約（同一情報の二重掲載を解消）。
- 🔵 各 `Failure` サブクラス（`NetworkError`/`AuthError`/`Timeout`/`EmptyOrInvalidResponse`/`Unknown`）のコメントを `//` 単行コメントから `/** */` docコメントに統一。IDEのクイックドキュメント表示との親和性を高めた。内容（EDGE番号・信頼性レベル）は変更なし。

### 2.2 `ChatCompletionDto.kt`

- 🔵 4つのDTO（`ChatCompletionRequestDto` / `ChatMessageDto` / `ChatCompletionResponseDto` / `ChoiceDto`）それぞれのクラスKDocに `@property` タグを追加し、各プロパティの意味・関連テストケース（TC-B-01〜03等）・エッジケース（EDGE-004）を明記した。`LlmSettings.kt`（TASK-0058, 同一パッケージ）の `@property` スタイルと統一。
- 変更前は個々のプロパティに説明コメントがなく、クラス単位のKDocのみだった。プロパティ単位の説明を追加したことで可読性が向上した（新規追加であり削除・簡略化ではない）。

## 3. リファクタリング後のコード全文

### 3.1 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt`

```kotlin
package com.den4dr.share2Obsidian.data.llm

/**
 * 【機能概要】: LLM呼び出し（メモ本文の書き換え等）の結果を表す sealed class
 * 【設計方針】: 成功時はテキストを保持する [Success]、失敗時は種別ごとに表示用 messageResId を
 *   保持する [Failure] サブクラスを定義する。sealed class にすることで when 式が exhaustive
 *   （分岐漏れをコンパイル時に検出可能）になる。
 * 【保守性】: 新たな失敗種別が必要になった場合は Failure のサブクラスを追加するだけでよく、
 *   既存の exhaustive な when 式は未対応の分岐をコンパイルエラーとして検出できる。
 * 【テスト対応】: LlmRewriteResultTest.kt の TC-N-04〜06, TC-B-04〜05 を通すための実装
 * 🔵 信頼性レベル: interfaces.kt「LLM 呼び出し結果（新規）」・requirements.md 2.1 に基づく（推測なし）
 */
sealed class LlmRewriteResult {

    /**
     * 【機能概要】: LLM呼び出しが成功したことを表し、書き換え/生成されたテキストを保持する
     * 【テスト対応】: TC-N-04（text保持）, TC-B-04（空文字保持）, TC-B-05（等価性）
     * 🔵 信頼性レベル: interfaces.kt 130行・REQ-003 に基づく
     *
     * @property text LLMによる書き換え/生成後のテキスト（REQ-003）。空文字も許容する（TC-B-04）
     */
    data class Success(val text: String) : LlmRewriteResult()

    /**
     * 【機能概要】: LLM呼び出しが失敗したことを表す sealed class
     * 【設計方針】: 全ての失敗種別が表示用 string resource ID を共通契約として持つよう
     *   abstract val messageResId を宣言し、各サブクラスで override する
     * 【テスト対応】: TC-N-05（各サブクラスのmessageResId保持）, TC-N-06（exhaustive when）
     * 🔵 信頼性レベル: interfaces.kt 133-149行・note.md 243-247行に基づく
     *
     * @property messageResId エラー表示用 string resource ID（`R.string.*`）。具体的な値の
     *   割当はTASK-0060（`LlmRewriteRepositoryImpl` の例外マッピング）で行う
     */
    sealed class Failure : LlmRewriteResult() {
        abstract val messageResId: Int

        /** 【失敗種別】: ネットワーク未接続・接続エラー（EDGE-001） 🔵 */
        data class NetworkError(override val messageResId: Int) : Failure()

        /** 【失敗種別】: APIキー不正・認証エラー（HTTP 401/403）（EDGE-002） 🔵 */
        data class AuthError(override val messageResId: Int) : Failure()

        /** 【失敗種別】: 30秒タイムアウト（EDGE-003, NFR-001） 🔵 */
        data class Timeout(override val messageResId: Int) : Failure()

        /** 【失敗種別】: 空応答・パース不能なレスポンス（EDGE-004） 🟡 */
        data class EmptyOrInvalidResponse(override val messageResId: Int) : Failure()

        /** 【失敗種別】: 上記以外の予期しないエラー 🟡 一般的なエラーハンドリング方針からの推測 */
        data class Unknown(override val messageResId: Int) : Failure()
    }
}
```

### 3.2 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`

```kotlin
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
 * 【設計方針】: リクエスト・レスポンスの双方で共通利用する最小構成の型とする
 * 【テスト対応】: ChatCompletionDtoTest.kt の TC-N-01〜03, TC-B-03 を通すための実装
 * 🔵 信頼性レベル: api-endpoints.md「リクエスト仕様」表に基づく（推測なし）
 *
 * @property role メッセージの役割（`"system"` / `"user"` / `"assistant"`）
 * @property content メッセージ本文。空文字でも欠落せず往復で保持される（TC-B-03）
 */
@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
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
```

---

## 4. セキュリティレビュー

| 観点 | 結果 |
|------|------|
| 入力値検証 | 該当なし。本タスクの型は純粋なデータ保持クラスであり、値の生成・検証ロジックを持たない（検証はTASK-0060の呼び出し側で実施） |
| 機微情報の扱い | `Failure` は例外オブジェクト・APIキー等を保持せず `messageResId: Int` のみを持つ設計を維持（NFR-102準拠）。DTOにも認証情報を保持するフィールドはない |
| SQLインジェクション / XSS / CSRF | 該当なし（DB・Web UIに関わらない純粋な型定義） |
| 認証・認可 | 該当なし（本タスクの範囲外、TASK-0060で扱う） |
| デシリアライズの安全性 | kotlinx.serialization の型安全なデコードのみを使用しており、任意コード実行等のリスクを持つ機構（Javaのネイティブ直列化等）は使用していない |

**総合判定**: 重大な脆弱性なし。リファクタリングはコメントのみのためセキュリティ特性に変化なし。

## 5. パフォーマンスレビュー

| 観点 | 結果 |
|------|------|
| 計算量 | 全て O(1) のプロパティアクセス・data class 生成。`List<ChatMessageDto>` / `List<ChoiceDto>` のシリアライズは要素数に対して線形（O(n)）で、想定件数（数件のメッセージ・choice）に対し問題なし |
| メモリ使用量 | immutable data class のみで状態を持たず、キャッシュ・シングルトン等の考慮は不要 |
| 不要な処理 | なし |
| リファクタによる影響 | コメント変更のみのため実行時性能への影響はゼロ |

**総合判定**: 重大な性能課題なし。

## 6. テスト実行結果

### リファクタ前（ベースライン確認）

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.data.llm.ChatCompletionDtoTest" --tests "com.den4dr.share2Obsidian.data.llm.LlmRewriteResultTest"
```

`BUILD SUCCESSFUL`。`ChatCompletionDtoTest` 9件（time=0.091s）、`LlmRewriteResultTest` 5件（time=0.003s）、計14件全て成功。個別テストの最大実行時間は0.045秒であり、2秒を超える遅いテストは検出されなかった。

### リファクタ後（コメント改善適用後）

同一コマンドを再実行。`:app:kspDebugKotlin` / `:app:compileDebugKotlin` が再コンパイルされたことを確認（UP-TO-DATEキャッシュの再利用ではない）。`BUILD SUCCESSFUL in 3s`。

| テストクラス | tests | failures | errors |
|---|---|---|---|
| `ChatCompletionDtoTest` | 9 | 0 | 0 |
| `LlmRewriteResultTest` | 5 | 0 | 0 |

新規コンパイル警告（未解決KDoc参照等）は発生しなかった。

## 7. コード・テスト除外チェック

- `describe.skip` / `@Ignore` / `@Disabled` 等によるテスト無効化: なし（`data/llm` 配下のテストファイルを確認済み）
- `app/build.gradle.kts` にテスト除外フィルタ（`exclude`/`filter`等）の設定なし
- `.gitignore` に本来対象とすべきソース・テストファイルを除外する記述なし
- 開発中生成ファイル（`debug-*`, `temp-*`, `*.bak` 等）の残存: 検出なし

## 8. 品質判定

| 観点 | 評価 |
|------|------|
| テスト結果 | ✅ 14件全て成功（failures=0, errors=0）、リファクタ前後で変化なし |
| セキュリティ | ✅ 重大な脆弱性なし |
| パフォーマンス | ✅ 重大な性能課題なし |
| リファクタ品質 | ✅ コメント冗長性を解消しつつ`@property`タグで可読性向上（目標達成） |
| コード品質 | ✅ 兄弟実装（`LlmSettings.kt`）とドキュメントスタイルを統一 |
| ファイルサイズ | ✅ 500行制限に対し十分小さい（`LlmRewriteResult.kt` 53行、`ChatCompletionDto.kt` 63行） |
| 日本語コメント品質 | ✅ 重複解消・IDE表示との親和性向上・信頼性レベル表記を維持 |
| モック使用 | ✅ 実装コードにモック・スタブなし |
| 機能的変更 | ✅ なし（構造・シリアライズ契約は Green フェーズと同一） |

**総合判定**: ✅ 高品質

---

## 次のステップ

`/tsumiki:tdd-verify-complete llm-memo-rewrite TASK-0059` で完全性検証を実行する。
