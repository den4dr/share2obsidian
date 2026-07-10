# TDD Greenフェーズ - TASK-0072: EditScreenViewModel generateCustomFieldValue()・EditScreen UI「生成」ボタン追加

- **機能名**: edit-custom-field-llm-generation（EditScreenViewModel カスタムフィールドLLM生成）
- **タスクID**: TASK-0072
- **要件名**: llm-memo-rewrite
- **作成日**: 2026-07-09

## 1. 実装方針

Redフェーズで作成した `EditScreenViewModelGenerateCustomFieldValueTest.kt`（ViewModel単体テスト13件）を通すための最小実装を行った。加えて、TASK-0072.md の完了条件に含まれる EditScreen 側のUI実装（`valueSource == LLM` の場合のみ「生成」ボタンを表示）も本フェーズで実施した（単体テストの対象外だが、完了条件必須項目のため）。

- `generateCustomFieldValue(index)` は既存の `rewriteBody()`/`updateCustomField()` パターンをそのまま踏襲。
- `generatingFieldIndex`（`Int?`）は `isRewritingBody`/`isSuggestingTags`（`Boolean`）と型が異なるため、既存の `runLlmRequest()` ヘルパー（`setLoading: (Boolean) -> Unit`）は再利用せず、専用の実装とした。
- EditScreen 側は既存の `LoadingButton` 抽出パターンとは別に、フィールド単位で `IconButton` + `CircularProgressIndicator` を配置する構成にした（フィールドごとに対象 index が異なるため、単純な共通コンポーネント抽出は行わず Row 内にインライン実装）。

## 2. 実装コード

### 2.1 `EditFormState.kt`（`generatingFieldIndex` 追加）

```kotlin
data class EditFormState(
    // ...(既存フィールド)
    val isSuggestingTags: Boolean = false,
    // 【フィールド定義】: generateCustomFieldValue(index) 実行中のローディング対象インデックス（REQ-104/REQ-304）。
    //              null は非生成中、非nullはそのインデックスのカスタムフィールドが生成中であることを示す。
    //              isRewritingBody/isSuggestingTags と異なりフィールド単位のため Boolean ではなく Int? で保持する 🟡
    // 【テスト対応】: TC-0072-N04
    val generatingFieldIndex: Int? = null,
)
```

### 2.2 `EditScreenViewModel.kt`（`generateCustomFieldValue(index: Int)` 追加）

```kotlin
/**
 * 【機能概要】: 指定インデックスのカスタムフィールド（`valueSource == FieldValueSource.LLM`）について、
 *              そのフィールドの `llmPrompt` と `sourceContent` を入力に LLM 呼び出しを行い、
 *              該当インデックスの `value` のみを応答テキストで更新する
 * 🔵 信頼性レベル: TASK-0072.md 実装詳細1・要件定義書 §2/§3 より（既存 rewriteBody/updateCustomField パターンの組合せ）
 */
fun generateCustomFieldValue(index: Int) {
    viewModelScope.launch {
        // 【対象プロンプト取得】: 対象インデックスの llmPrompt を取得する 🔵
        val prompt = formState.value.customFields[index].llmPrompt

        // 【ローディング開始】: フィールド単位のローディング状態に対象 index を設定する 🟡
        _formState.update { it.copy(generatingFieldIndex = index) }

        // 【設定取得】: LLM API 接続設定を取得する 🔵
        val settings = llmSettingsRepository.getSettings().first()

        // 【LLM呼び出し】: 入力は常に sourceContent を使用する（REQ-002, REQ-406）。空文字でもガードしない（EDGE-101）🔵
        when (val result = llmRewriteRepository.rewrite(settings, prompt, sourceContent)) {
            is LlmRewriteResult.Success ->
                // 【成功時】: 既存 updateCustomField(index, value) を再利用し、対象インデックスのみ更新する 🔵
                updateCustomField(index, result.text)
            is LlmRewriteResult.Failure ->
                // 【失敗時】: 対象フィールドの値は変更せず、errorEvents に messageResId を発行する（NFR-201）🔵
                _errorEvents.emit(result.messageResId)
        }

        // 【ローディング終了】: 成功・失敗いずれの場合も generatingFieldIndex を null に戻す 🟡
        _formState.update { it.copy(generatingFieldIndex = null) }
    }
}
```

### 2.3 `EditScreen.kt`（「生成」ボタン UI 追加）

```kotlin
formState.customFields.forEachIndexed { index, field ->
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = field.value,
            onValueChange = { viewModel.updateCustomField(index, it) },
            label = { Text(field.key) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        // 【表示条件】: valueSource == LLM の場合のみ「生成」ボタンを表示する（完了条件）🔵
        if (field.valueSource == FieldValueSource.LLM) {
            val isGenerating = formState.generatingFieldIndex == index
            IconButton(
                onClick = { viewModel.generateCustomFieldValue(index) },
                enabled = !isGenerating,
                modifier = Modifier.testTag("field_generate_button_$index"),
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).testTag("field_generate_progress_$index"),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.field_generate_button),
                    )
                }
            }
        }
    }
}
```

### 2.4 `strings.xml`

```xml
<!-- カスタムフィールドLLM生成機能（TASK-0072） -->
<string name="field_generate_button">生成</string>
<string name="field_generating">生成中...</string>
```

## 3. テスト実行結果

### 3.1 対象ユニットテスト（新規）

```
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelGenerateCustomFieldValueTest"
BUILD SUCCESSFUL
```

全13件（TC-0072-N01〜N04, E01〜E05, B01〜B04）成功。

### 3.2 `ui` パッケージ全体のユニットテスト（回帰確認）

```
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.*"
BUILD SUCCESSFUL
```

### 3.3 全ユニットテスト

```
mise exec -- ./gradlew testDebugUnitTest
BUILD SUCCESSFUL
```

### 3.4 実機（エミュレータ emulator-5554）での回帰確認

EditScreen の Compose UI 変更（customFields ループを `Row` でラップし、生成ボタンを追加）による既存 `EditScreenTest` への影響がないことを確認するため、実機（`Medium_Phone_API_36.1(AVD)`, emulator-5554）で `connectedDebugAndroidTest` を実行した。

```
mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.den4dr.share2Obsidian.ui.EditScreenTest
Finished 32 tests on Medium_Phone_API_36.1(AVD) - 16
BUILD SUCCESSFUL
```

32件全て成功（0 failed）。既存テスト（TC-CUSTOM-UI-001, TC-CUSTOM-UI-002 含む）は `valueSource` 未指定＝デフォルト `FIXED` のフィールドのみを扱うため、生成ボタン追加・Row化による表示崩れ等の回帰は発生していない。

なお、生成ボタン自体（`field_generate_button_$index` 等）を検証する新規 UI テストは、テストケース定義書で「本タスクの単体テスト範囲外・別途実装」と明記されているため本フェーズでは追加していない。

## 4. 品質判定

- ✅ テスト結果: ViewModel単体テスト13件・ui全体・全体テスト全て成功。実機での既存 EditScreenTest 32件も全て成功（回帰なし）
- ✅ 実装品質: 既存 `rewriteBody()`/`updateCustomField()` パターンを踏襲したシンプルな実装
- ✅ 機能的問題: なし
- ✅ コンパイルエラー: なし
- ✅ ファイルサイズ: `EditScreenViewModel.kt` 410行、`EditScreen.kt` 307行、`EditFormState.kt` 72行（いずれも800行制限内）
- ✅ モック使用: 実装コードにモック・スタブは含まれていない（モックはテストコードのみ）

**総合判定**: ✅ 高品質

## 5. Refactorフェーズへの申し送り事項

- `EditScreen.kt` の生成ボタン部分は `LoadingButton`（既存の再利用コンポーネント）と統一感を持たせられる余地がある（ただし対象フィールドが `index` 単位で異なるため、単純な共通化は難しく Refactor フェーズで要検討）。
- `generateCustomFieldValue()` と `runLlmRequest()` のローディング型（`Boolean` vs `Int?`）の違いによるコード重複（設定取得・成功/失敗分岐の構造が類似）は、Refactorフェーズで共通化の余地があるか検討する。
