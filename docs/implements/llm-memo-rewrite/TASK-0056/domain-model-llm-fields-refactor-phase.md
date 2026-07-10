# TASK-0056 Refactorフェーズ記録: Template/TemplateField/FieldValueSource ドメインモデル変更

**機能名（feature_name）**: domain-model-llm-fields
**タスクID**: TASK-0056
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. リファクタ前の状態（Greenフェーズ実装）

Greenフェーズの実装記録ファイル（`domain-model-llm-fields-green-phase.md`）は作成されていなかったため、実コードから状態を確認した。以下の最小実装が完了しており、`mise exec -- ./gradlew test` は BUILD SUCCESSFUL（全171テスト成功）だった。

- `Template.kt`: `body` の直後に `val bodyLlmPrompt: String = ""` を追加（コメントなし）
- `TemplateField.kt`: `metaKey` の直後に `val llmPrompt: String = ""` を追加（コメントなし）
- `FieldValueSource.kt`: `enum class FieldValueSource { FIXED, HTML_META, URL, EMPTY, LLM }`（コメントなし）
- `TemplateApplicator.kt`: `buildCustomFields()` の `when` 式に `FieldValueSource.LLM -> ""` を追加（コメントなし、exhaustive対応のための暫定値）
- `TemplateTest.kt`: TC1, TC3, TC5, TC-ERR1, TC4, TC2-B, TC-EDGE1 の8テストケースを追加/更新済み（日本語コメント済み・変更対象外）

機能追加はすべて完了しており、**本フェーズでは可読性向上（コメント充実）のみを行い、ロジック・シグネチャの変更は一切行っていない。**

---

## 2. レビュー結果

### 2.1 セキュリティレビュー

- 🔵 本タスクは Domain 層のデータクラス・enum へのプロパティ／値追加のみであり、外部入力の検証・SQL・HTML描画・認証認可のいずれにも関与しない。
- 🔵 `bodyLlmPrompt` / `llmPrompt` は本タスクでは永続化・外部送信されない（TASK-0057でDB永続化、TASK-0058以降でLLM API送信が実装される）。現時点でAPIキー等の機微情報を扱うコードパスはない。
- **結論**: 重大な脆弱性なし。本タスクのスコープでは対象外の項目（入力検証・APIキー暗号化等）は後続タスク（TASK-0058, TASK-0070〜0072）で改めてレビューが必要。

### 2.2 パフォーマンスレビュー

- 🔵 `String` プロパティ1つの追加、enum値1つの追加はいずれも O(1) のメモリ・処理オーバーヘッドであり、既存の計算量に影響しない。
- 🔵 `TemplateApplicator.buildCustomFields()` は既存どおり `fields` に対する O(n) の `map` 処理であり、追加した `when` 分岐も O(1)。
- **結論**: 重大な性能課題なし。

---

## 3. 実施した改善（すべて🔵: 元資料に基づく、推測なし）

機能的な変更は行わず、コメント追加による可読性向上のみを実施。各改善はテスト実行で回帰がないことを確認済み。

### 3.1 `Template.kt` — `bodyLlmPrompt` の意図を明示

```kotlin
data class Template(
    val id: Long = 0,
    val name: String,
    val body: String = "",
    // 【LLM本文リライト用プロンプト】: 空文字は「未設定」を表し、後続UIでの「メモを更改」ボタン非活性判定に使う（REQ-101, REQ-102）
    // 🔵 信頼性レベル: TASK-0056 要件定義・interfaces.kt に基づく（推測なし）
    val bodyLlmPrompt: String = "",
    val fields: List<TemplateField>,
    val isDefault: Boolean = false,
)
```

**改善理由**: `bodyLlmPrompt` は空文字が「未設定」を意味するという後続タスク（REQ-102）依存の意味論を持つ。コメントなしでは呼び出し元での意図が読み取れないため明示した。

### 3.2 `TemplateField.kt` — `llmPrompt` の使用条件を明示

```kotlin
data class TemplateField(
    val id: Long = 0,
    val templateId: Long = 0,
    val key: String,
    val valueSource: FieldValueSource,
    val valueType: FieldValueType,
    val defaultValue: String = "",
    val metaKey: HtmlMetaKey? = null,
    // 【フィールド単位のLLM生成用プロンプト】: valueSource == LLM の場合のみ使用。空文字は「未設定」（REQ-104）
    // 🔵 信頼性レベル: TASK-0056 要件定義・interfaces.kt に基づく（推測なし）
    val llmPrompt: String = "",
    val sortOrder: Int = 0,
)
```

**改善理由**: `llmPrompt` は `valueSource == LLM` の場合にのみ意味を持つ条件付きプロパティであり、その前提を読み手が誤解しないようコメントで明示した。

### 3.3 `FieldValueSource.kt` — 各値の意味とLLM追加の背景をKDoc化

```kotlin
/**
 * 【カスタムフィールドの値取得元】: TemplateField.valueSource が示す値の生成方法を表す。
 * - FIXED: テンプレートに保存された固定値（defaultValue）をそのまま使用
 * - HTML_META: 共有元ページの HTML メタデータ（metaKey で指定）から取得
 * - URL: 共有元ページの URL を使用
 * - EMPTY: 常に空文字（値なし）
 * - LLM: LLM 生成結果を使用（llmPrompt を元にLLM APIへ問い合わせる。REQ-303）
 *        🔵 信頼性レベル: TASK-0056 要件定義・REQ-303 に基づく（推測なし）
 *        本タスクでは enum 値の追加のみで、実際のLLM呼び出し・値の生成ロジックは TASK-0070/0071/0072 で実装する。
 */
enum class FieldValueSource { FIXED, HTML_META, URL, EMPTY, LLM }
```

**改善理由**: 変更前は各値の意味を説明するコメントが一切なく、`LLM` が何を表すか・いつ実装されるかが読み取れなかった。既存4値の意味も含めてKDocにまとめ、新規追加の `LLM` が後続タスク（TASK-0070〜0072）で実装される暫定状態であることを明示した。

### 3.4 `TemplateApplicator.kt` — `LLM -> ""` 分岐が暫定実装であることを明示

```kotlin
val value = when (field.valueSource) {
    FieldValueSource.FIXED -> field.defaultValue
    FieldValueSource.HTML_META -> processed.metadata[field.metaKey] ?: ""
    FieldValueSource.URL -> processed.sourceUrl ?: ""
    FieldValueSource.EMPTY -> ""
    // 【暫定対応】: LLM生成は本タスク（TASK-0056）の対象外。when式の網羅性を保つための仮実装で、
    // 実際のLLM呼び出し・値生成ロジックは TASK-0070（カスタムフィールドLLM生成）で実装する。
    // 🔵 信頼性レベル: note.md「後続タスクとの関係」・TASK-0056 完了条件に基づく（推測なし）
    FieldValueSource.LLM -> ""
}
```

**改善理由**: `FieldValueSource.LLM -> ""` は `when` の網羅性（exhaustiveness）を満たすための暫定値であり、恒久的な仕様ではない。コメントなしでは「LLM選択時は常に空文字を返す」という誤った仕様として読まれるリスクがあったため、暫定実装であることと対応タスクを明記した。

---

## 4. 見送った改善（スコープ外・過剰実装回避）

- 🟡 **既存の他テスト（`Template defaults`, `Template body defaults to empty string` 等）へのコメント追加**: TASK-0056の変更対象外であり、「機能的な変更は行わない」原則および既存コードベースの一貫性（他の単純なテストにも同様のコメントは付与されていない）を踏まえ見送った。過剰なコメント付与はかえって可読性を損なうと判断。
- 🟡 **`FieldValueSource.LLM` の実際の値生成ロジック実装**: TASK-0070/0071/0072のスコープであり、本タスクで先行実装すると「機能的な変更は行わない」というRefactor原則に反する。
- 🟡 **Android Lint (`./gradlew lint`) の実行**: 本タスクはリソース・マニフェスト変更を伴わない純粋なKotlinドメインモデル変更のため、影響範囲外と判断し実行を見送った。ユニットテスト（`./gradlew test`）による検証で十分と判断。

---

## 5. テスト実行結果

**実行コマンド**:
```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*TemplateTest*" --tests "*TemplateApplicatorTest*"
mise exec -- ./gradlew test
```

**結果**: いずれも `BUILD SUCCESSFUL`。

- 全171テスト（27テストクラス）成功・失敗0・エラー0・スキップ0（リファクタ前後で変化なし）
- `TemplateTest.kt` 単体: 14件全てPASS
- 実行時間2秒以上のテストなし（最長は無関係な `MainActivityEditFlowTest` の1.741秒、`TemplateRepositoryImplTest` の1.05秒。いずれも本タスクの変更対象外）
- `@Ignore` / `.skip` / コメントアウトされたテスト、`build.gradle.kts` でのテスト除外設定は確認されず
- 一時ファイル（`*.tmp`, `*.bak`, `*.orig`, `~`, `.DS_Store` 等）の残留なし

---

## 6. 品質判定

| 項目 | 結果 |
|------|------|
| テスト結果 | ✅ 全171テスト成功（リファクタ前後で変化なし） |
| セキュリティ | ✅ 重大な脆弱性なし（本タスクスコープ内） |
| パフォーマンス | ✅ 重大な性能課題なし |
| リファクタ目標 | ✅ 達成（可読性向上のコメント充実。機能変更なし） |
| コード品質 | ✅ 適切なレベル（既存コードベースの簡潔なスタイルを維持しつつ、意味論が非自明な箇所にのみコメント追加） |
| ファイルサイズ | ✅ 全対象ファイル500行未満（最大でも `TemplateTest.kt` 248行） |
| 日本語コメント | ✅ 新規プロパティ・enum値・暫定実装箇所に信頼性レベル付きコメントを追加 |

**総合判定**: ✅ 高品質

---

## 7. 変更ファイル一覧

| ファイル | 変更内容 |
|---------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt` | `bodyLlmPrompt` にコメント追加（機能変更なし） |
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt` | `llmPrompt` にコメント追加（機能変更なし） |
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt` | KDocコメント追加（機能変更なし） |
| `app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt` | `LLM -> ""` 分岐に暫定実装であることを明示するコメント追加（機能変更なし） |

---

## 8. 次のステップ

次のお勧めステップ: `/tsumiki:tdd-verify-complete` で完全性検証を実行します。
