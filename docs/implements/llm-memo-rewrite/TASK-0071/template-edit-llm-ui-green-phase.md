# TDD Greenフェーズ記録: TemplateEditViewModel/TemplateEditScreen/FieldAddDialog へのLLM UI追加

- **機能名**: template-edit-llm-ui
- **タスクID**: TASK-0071
- **要件名**: llm-memo-rewrite
- **フェーズ**: Green（最小実装）
- **作成日**: 2026-07-09（tdd-verify-complete 実行時に事後補完）

---

## 0. 本ファイルについての補足（事後補完である旨）

- 本ファイルは `tdd-green` フェーズの実施タイミングでは作成されず、`template-edit-llm-ui-memo.md` にも「Greenフェーズの記録ファイルは本セッション開始時点で未作成」と記録されていた。
- `template-edit-llm-ui-refactor-phase.md` にも同様に欠落が明記されており、Refactorフェーズは「実装済みコード＋Redフェーズで追加した全12テストが成功する状態」を実質的なGreenフェーズ成果物とみなして進められた。
- `tdd-verify-complete` 実行時点で実装コード（`TemplateEditViewModel.kt`／`TemplateEditScreen.kt`／`strings.xml`）とテスト実行結果（後述）を突合し、Greenフェーズの記録として本ファイルを事後的に補完した。
- 🟡 信頼性レベル: 実装方針・実装意図の記述は、実コード中の日本語コメント（【機能概要】【実装方針】【テスト対応】等、Red/Refactorフェーズ記録と同一の信頼性タグ）をそのまま引用・要約したものであり、Greenフェーズ実施当時の一次記録そのものではない。

---

## 1. 実装方針

Redフェーズで特定された「Greenフェーズで実装すべき内容」（`template-edit-llm-ui-red-phase.md` §5）をそのまま最小実装として反映した。

- ドメインモデル（`Template.bodyLlmPrompt`／`TemplateField.llmPrompt`／`FieldValueSource.LLM`）はTASK-0056で実装済みのため変更不要。
- `TemplateEditViewModel.kt`: 既存の `updateBody()`／`loadTemplate()`／`save()` パターンをそのまま踏襲し、`bodyLlmPrompt`／`llmPrompt` の往復マッピングを追加しただけの差分にとどめた（回帰リスク最小化）。
- `TemplateEditScreen.kt`: 本文用LLMプロンプト欄は既存の `template_body_field` と同型の複数行 `OutlinedTextField` を追加。`FieldAddDialog` はLLM選択肢の追加、条件付きプロンプト入力欄の表示、`onAdd` 時の非LLM選択時プロンプト破棄（TC-E-02）を実装。
- `strings.xml`: `field_source_llm`／`field_llm_prompt_label`／`template_body_llm_prompt_label` の3文字列を追加。

---

## 2. 実装コード（抜粋）

### 2-1. `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModel.kt`

```kotlin
data class TemplateEditUiState(
    val templateId: Long? = null,
    val name: String = "",
    val body: String = "",
    // 【本文用LLMプロンプト】: 空文字は「未設定」を表す。updateBodyLlmPrompt()で更新され、save()/loadTemplate()でTemplate.bodyLlmPromptと往復する
    // 🔵 信頼性レベル: 要件定義書 2.1・TASK-0056で実装済みのTemplate.bodyLlmPromptに対応
    val bodyLlmPrompt: String = "",
    val isDefault: Boolean = false,
    val fields: List<TemplateFieldEditState> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
)

data class TemplateFieldEditState(
    val id: Long = 0,
    val key: String = "",
    val valueSource: FieldValueSource = FieldValueSource.EMPTY,
    val valueType: FieldValueType = FieldValueType.STRING,
    val defaultValue: String = "",
    val metaKey: HtmlMetaKey? = null,
    // 【フィールド用LLMプロンプト】: valueSource == LLM の場合のみ使用。空文字は「未設定」
    // 🔵 信頼性レベル: 要件定義書 2.1・TASK-0056で実装済みのTemplateField.llmPromptに対応
    val llmPrompt: String = "",
    val sortOrder: Int = 0,
)

fun updateBodyLlmPrompt(prompt: String) = _uiState.update { it.copy(bodyLlmPrompt = prompt) }
```

`loadTemplate()` では `TemplateEditUiState` 構築時に `bodyLlmPrompt = template.bodyLlmPrompt` を、`fields.map {}` 内で `llmPrompt = field.llmPrompt` を追加した。`save()` では `Template` 構築時に `bodyLlmPrompt = state.bodyLlmPrompt` を、`fields.mapIndexed {}` 内で `llmPrompt = field.llmPrompt` を追加した（既存の `mapIndexed(sortOrder = index)` ロジックは変更なし）。

### 2-2. `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreen.kt`（抜粋）

本文テンプレート入力欄（`template_body_field`）の直下に追加した本文用LLMプロンプト欄:

```kotlin
OutlinedTextField(
    value = uiState.bodyLlmPrompt,
    onValueChange = { viewModel.updateBodyLlmPrompt(it) },
    label = { Text(stringResource(R.string.template_body_llm_prompt_label)) },
    modifier = Modifier
        .fillMaxWidth()
        .testTag("template_body_llm_prompt_field"),
    minLines = 2,
)
```

`FieldAddDialog` の値取得方法選択肢へのLLM追加、条件付きプロンプト入力欄:

```kotlin
FieldValueSource.LLM to stringResource(R.string.field_source_llm) // 選択肢リストに追加

if (valueSource == FieldValueSource.LLM) {
    OutlinedTextField(
        value = llmPromptInput,
        onValueChange = { llmPromptInput = it },
        label = { Text(stringResource(R.string.field_llm_prompt_label)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("field_llm_prompt_field"),
        minLines = 2,
    )
}
```

`onAdd` 時の非LLM選択時プロンプト破棄（TC-E-02）:

```kotlin
onAdd(
    TemplateFieldEditState(
        key = key.trim(),
        valueSource = valueSource,
        valueType = valueType,
        defaultValue = if (valueSource == FieldValueSource.FIXED) defaultValue else "",
        metaKey = if (valueSource == FieldValueSource.HTML_META) metaKey else null,
        // 【非LLM選択時のプロンプト破棄】: valueSource != LLM の場合は空文字にする（TC-E-02）
        llmPrompt = if (valueSource == FieldValueSource.LLM) llmPromptInput else "",
    )
)
```

### 2-3. `app/src/main/res/values/strings.xml`

```xml
<string name="field_source_llm">LLM生成</string>
<string name="field_llm_prompt_label">LLMプロンプト</string>
<string name="template_body_llm_prompt_label">本文用LLMプロンプト</string>
```

---

## 3. テスト実行結果（事後確認、tdd-verify-complete step3にて再実行）

### 実行コマンド

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditViewModelTest"
mise exec -- ./gradlew testDebugUnitTest
mise exec -- ./gradlew connectedDebugAndroidTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditScreenTest"
```

### 結果: すべて BUILD SUCCESSFUL

- `TemplateEditViewModelTest`（単体）: 14 tests / 0 failures（既存6＋TASK-0071新規8）
- プロジェクト全体単体テスト（`testDebugUnitTest`）: 271 tests / 0 failures
- `TemplateEditScreenTest`（統合、emulator-5554実機）: 11 tests / 0 failures（既存7＋TASK-0071新規4）

`template-edit-llm-ui-testcases.md` に定義した全12ケース（正常系6・異常系3・境界値3）がいずれも成功しており、Greenフェーズのゴール（Redフェーズ記録の実装後にすべて成功すること）を満たしていることを確認した。

---

## 4. 品質判定

- ✅ **テスト結果**: 全テスト成功（単体14/14、統合11/11、全体271/271）
- ✅ **実装品質**: シンプル。既存の `updateXxx()`／`loadTemplate()`／`save()` パターンへの追加のみ
- ✅ **機能的問題**: なし
- ✅ **コンパイルエラー**: なし
- ✅ **ファイルサイズ**: `TemplateEditViewModel.kt` 160行／`TemplateEditScreen.kt` 419行、いずれも500行制限内
- ⚠️ **モック使用**: 実装コードにモック・スタブは含まれていない（テストコードのみでMockK使用）

**総合判定**: ✅ 高品質。Refactorフェーズ（実施済み）へ進行可能な状態だった。

---

## 5. 課題・改善点（Refactorフェーズで対応済み）

- `FieldAddDialog` の値取得方法選択肢リストが再コンポーズのたびに再構築される点は、Refactorフェーズで `remember` 化により改善済み（詳細は `template-edit-llm-ui-refactor-phase.md` §3）。
