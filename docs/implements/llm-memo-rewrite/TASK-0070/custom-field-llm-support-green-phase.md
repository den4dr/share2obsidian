# TDD Greenフェーズ記録: CustomFieldState拡張・TemplateApplicator.buildCustomFields()のLLM対応

- **機能名**: custom-field-llm-support
- **タスクID**: TASK-0070
- **要件名**: llm-memo-rewrite
- **フェーズ**: Green（最小実装）
- **作成日**: 2026-07-09

---

## 1. 実装方針

Redフェーズで特定された「Greenフェーズで実装すべき内容」（`custom-field-llm-support-red-phase.md` 4節）をそのまま最小実装として反映した。

- 仕様（要件定義書・テストケース定義書）と現在の実装との間に差異はなく、AskUserQuestionによる確認は不要と判断した。
- `CustomFieldState` にデフォルト値付きの `valueSource`/`llmPrompt` を追加し、既存の3引数コンストラクタ呼び出し（`NoteComposerTest`, `EditScreenViewModelTest`, `EditScreenTest`, `EdgeCaseIntegrationTest`, `TemplateTest` 等）との後方互換性を維持した。
- `TemplateApplicator.buildCustomFields()` は `FIXED`/`HTML_META`/`URL`/`EMPTY`/`LLM` の `value` 算出ロジックを変更せず、`CustomFieldState` 生成時の引数に `field.valueSource`, `field.llmPrompt` を追加しただけの差分にとどめた（回帰リスク最小化）。

---

## 2. 実装コード全文

### 2-1. `app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt`

```kotlin
package com.den4dr.share2Obsidian.domain.model

/**
 * 【機能概要】: EditScreen上で各カスタムフィールドの編集状態を表すドメインモデル
 * 【実装方針】: valueSource/llmPrompt にデフォルト値を付与し、既存の3引数コンストラクタ呼び出しとの
 * 後方互換性を維持したまま、LLM生成ボタンの表示判定・生成実行に必要なメタ情報を保持できるようにする
 * 【テスト対応】: TC-0070-N03（3引数コンストラクタ互換）、buildCustomFields 系の各テストケースで参照される
 * 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」・architecture.mdからの妥当な推測
 */
data class CustomFieldState(
    val key: String,
    val value: String,
    val valueType: FieldValueType,
    // 【フィールド値取得元】: EditScreen側でLLM生成ボタンの表示判定（valueSource == LLM）に使用する
    // 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」に基づく
    val valueSource: FieldValueSource = FieldValueSource.FIXED,
    // 【LLM呼び出し用プロンプト】: valueSource == LLM の場合にLLM呼び出しへ渡すプロンプト。未設定時は空文字
    // 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」に基づく
    val llmPrompt: String = "",
)
```

### 2-2. `app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt`（`buildCustomFields()` のみ抜粋）

```kotlin
/**
 * 【機能概要】: テンプレートのカスタムフィールド定義（TemplateField）を、EditScreenの編集状態
 * （CustomFieldState）へ変換する
 * 【実装方針】: FIXED/HTML_META/URL/EMPTY の既存 value 算出ロジックは変更せず、CustomFieldState
 * 生成時に valueSource/llmPrompt を新たに渡すことで、LLM生成ボタンの表示判定に必要な情報を橋渡しする
 * 【テスト対応】: TC-0070-N01〜N03, E01〜E02, B01〜B03（テンプレート適用時のLLM対応・回帰確認）
 * 🔵 信頼性レベル: 要件定義書 2-2 値算出テーブル・REQ-304 に基づく（推測なし）
 * @param template カスタムフィールド定義を持つテンプレート（null可）
 * @param processed 共有コンテンツの処理結果
 * @returns 各 TemplateField を変換した編集状態のリスト（template が null の場合は空リスト）
 */
fun buildCustomFields(
    template: Template?,
    processed: ProcessedContent,
): List<CustomFieldState> = template?.fields?.map { field ->
    // 【値算出】: valueSource ごとに value を算出する（既存ロジックを維持） 🔵
    val value = when (field.valueSource) {
        FieldValueSource.FIXED -> field.defaultValue
        FieldValueSource.HTML_META -> processed.metadata[field.metaKey] ?: ""
        FieldValueSource.URL -> processed.sourceUrl ?: ""
        FieldValueSource.EMPTY -> ""
        // 【LLM生成は本関数では行わない】: テンプレート適用時点ではLLM呼び出しを行わず、値は空文字のまま
        // EditScreen上のボタン押下時（TASK-0072/0073）に生成する（REQ-304の設計判断）
        // 🔵 信頼性レベル: 要件定義書 制約条件・design-interview.md Q2 に基づく（推測なし）
        FieldValueSource.LLM -> ""
    }
    // 【CustomFieldState生成】: valueSource/llmPrompt を渡すことで、EditScreen側でLLM生成ボタンの
    // 表示・活性判定が可能になる 🔵
    CustomFieldState(field.key, value, field.valueType, field.valueSource, field.llmPrompt)
} ?: emptyList()
```

`buildConfig()` / `buildBody()` は変更なし。

---

## 3. テスト実行結果

### 実行コマンド

```bash
mise exec -- ./gradlew :app:testDebugUnitTest \
  --tests "com.den4dr.share2Obsidian.TemplateApplicatorTest" \
  --tests "com.den4dr.share2Obsidian.domain.model.TemplateTest" \
  --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest" \
  --tests "com.den4dr.share2Obsidian.format.NoteComposerTest" \
  --tests "com.den4dr.share2Obsidian.integration.EdgeCaseIntegrationTest"
```

### 結果: BUILD SUCCESSFUL

- `TemplateApplicatorTest`: 18 tests, failures=0, errors=0（既存10＋TASK-0070新規8）
- `CustomFieldState` の3引数コンストラクタを使用する他テスト（`TemplateTest`, `EditScreenViewModelTest`, `NoteComposerTest`, `EdgeCaseIntegrationTest`）もすべて成功し、後方互換性を実運用で確認した。

---

## 4. 品質判定

- ✅ **テスト結果**: 全テスト成功（実測18/18、関連既存テストも全成功）
- ✅ **実装品質**: シンプル。既存の `when` 式の分岐と `CustomFieldState` 生成箇所への引数追加のみ
- ✅ **リファクタ箇所**: 現時点で明確な改善点なし（差分が最小、既存パターン踏襲）。強いて挙げれば、テストコード内のテンプレート/ProcessedContent構築が重複しており、ヘルパー関数化の余地はあるが本タスクの範囲外
- ✅ **機能的問題**: なし
- ✅ **コンパイルエラー**: なし
- ✅ **ファイルサイズ**: `CustomFieldState.kt` 20行、`TemplateApplicator.kt` 65行。800行制限に対し十分小さい
- ✅ **モック使用**: 実装コード（`CustomFieldState.kt`, `TemplateApplicator.kt`）にモック・スタブは含まれていない。テストコードのみで検証している

**総合判定**: ✅ 高品質。Refactorフェーズへ進行可能。

---

## 5. 課題・改善点（Refactorフェーズ候補）

- 現時点で本質的なリファクタリング対象はない。TASK-0070の差分は要件定義書どおりの最小変更で完結している。
- 後続タスク（TASK-0071〜0073）でEditScreenViewModel/EditScreen/実際のLLM呼び出しが追加された際、`CustomFieldState.valueSource == LLM` の判定ロジックが複数箇所に分散しないよう設計時に留意する（本タスクの範囲外の申し送り事項）。
