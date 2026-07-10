# TDD Redフェーズ - TASK-0071: TemplateEditViewModel/TemplateEditScreen/FieldAddDialog へのLLM UI追加

- **機能名**: template-edit-llm-ui（llm-memo-rewrite）
- **タスクID**: TASK-0071
- **要件名**: llm-memo-rewrite
- **作成日**: 2026-07-09
- **テストファイル**:
  - `app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt`（単体テスト、追記）
  - `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreenTest.kt`（統合テスト、追記）

---

## 1. 作成したテストケース一覧

`docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-testcases.md` に定義された全12ケースをすべて実装した（正常系6・異常系3・境界値3）。

| TC ID | 分類 | レベル | テスト名（メソッド名） | 信頼性 |
|-------|------|--------|------------------------|--------|
| TC-N-01 | 正常系 | 単体 | `updateBodyLlmPrompt_updatesUiState` | 🔵 |
| TC-N-02 | 正常系 | 単体 | `save_reflectsBodyLlmPromptAndFieldLlmPrompt` | 🔵 |
| TC-N-03 | 正常系 | 単体 | `loadTemplate_restoresBodyLlmPromptAndFieldLlmPrompt` | 🔵 |
| TC-N-04 | 正常系 | 統合 | `llmSource_showsLlmPromptField` | 🔵 |
| TC-N-05 | 正常系 | 統合 | `bodyLlmPromptInput_updatesViewModel` | 🔵 |
| TC-N-06 | 正常系 | 統合 | `saveAndReload_restoresBodyLlmPromptAndFieldLlmPrompt` | 🔵 |
| TC-E-01 | 異常系 | 単体 | `loadTemplate_missingTemplate_bodyLlmPromptStaysDefault` | 🔵 |
| TC-E-02 | 異常系 | 統合 | `nonLlmSource_discardsLlmPrompt` | 🟡 |
| TC-E-03 | 異常系（退行確認） | 単体 | `save_withBodyLlmPrompt_stillSetsIsSavedRegression` | 🔵 |
| TC-B-01 | 境界値 | 単体 | `save_bodyLlmPromptEmptyByDefault_savesEmptyString` | 🔵 |
| TC-B-02 | 境界値 | 単体 | `save_llmFieldWithEmptyPrompt_preservesEmptyLlmPrompt` | 🟡 |
| TC-B-03 | 境界値 | 単体 | `save_multipleFieldsWithLlmMixed_preservesLlmPromptMapping` | 🟡 |

信頼性分布: 🔵 8件（67%）、🟡 4件（33%）、🔴 0件。

---

## 2. テストコード

### 単体テスト（追記部分）

`app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt` の末尾（`save_persistsBody()` の後）に追記した。既存インポートに加え、以下を追加した。

```kotlin
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.TemplateField
```

追加した各テストの要点:

```kotlin
// TC-N-01
@Test
fun updateBodyLlmPrompt_updatesUiState() {
    val viewModel = createViewModel()
    viewModel.updateBodyLlmPrompt("要約してください")
    assertEquals("要約してください", viewModel.uiState.value.bodyLlmPrompt)
}

// TC-N-02
@Test
fun save_reflectsBodyLlmPromptAndFieldLlmPrompt() {
    val viewModel = createViewModel()
    viewModel.updateName("テスト")
    viewModel.updateBodyLlmPrompt("本文用プロンプト")
    viewModel.addField(
        TemplateFieldEditState(
            key = "category",
            valueSource = FieldValueSource.LLM,
            llmPrompt = "フィールド用プロンプト",
        )
    )
    val slot = mutableListOf<Template>()
    coEvery { repository.saveTemplate(capture(slot)) } returns 1L

    viewModel.save()
    Thread.sleep(200)

    assertEquals("本文用プロンプト", slot.last().bodyLlmPrompt)
    assertEquals("フィールド用プロンプト", slot.last().fields[0].llmPrompt)
    assertEquals(FieldValueSource.LLM, slot.last().fields[0].valueSource)
}

// TC-N-03
@Test
fun loadTemplate_restoresBodyLlmPromptAndFieldLlmPrompt() {
    val template = Template(
        id = 1L,
        name = "既存テンプレート",
        body = "## 記事\n{{content}}",
        bodyLlmPrompt = "保存済み本文プロンプト",
        isDefault = true,
        fields = listOf(
            TemplateField(
                id = 1L,
                templateId = 1L,
                key = "category",
                valueSource = FieldValueSource.LLM,
                valueType = FieldValueType.STRING,
                llmPrompt = "保存済みフィールドプロンプト",
            )
        ),
    )
    coEvery { repository.getTemplateById(1L) } returns template

    val viewModel = createViewModel(templateId = 1L)
    Thread.sleep(200)

    assertEquals("保存済み本文プロンプト", viewModel.uiState.value.bodyLlmPrompt)
    assertEquals("保存済みフィールドプロンプト", viewModel.uiState.value.fields[0].llmPrompt)
    assertEquals(FieldValueSource.LLM, viewModel.uiState.value.fields[0].valueSource)
}

// TC-E-01
@Test
fun loadTemplate_missingTemplate_bodyLlmPromptStaysDefault() {
    coEvery { repository.getTemplateById(99L) } returns null
    val viewModel = createViewModel(templateId = 99L)
    Thread.sleep(200)
    assertEquals("", viewModel.uiState.value.bodyLlmPrompt)
    assertTrue(viewModel.uiState.value.fields.isEmpty())
}

// TC-E-03
@Test
fun save_withBodyLlmPrompt_stillSetsIsSavedRegression() {
    val viewModel = createViewModel()
    viewModel.updateName("テスト")
    viewModel.updateBodyLlmPrompt("p")
    coEvery { repository.saveTemplate(any()) } returns 1L

    viewModel.save()
    Thread.sleep(200)

    coVerify { repository.saveTemplate(any()) }
    assertTrue(viewModel.uiState.value.isSaved)
}

// TC-B-01
@Test
fun save_bodyLlmPromptEmptyByDefault_savesEmptyString() {
    val viewModel = createViewModel()
    viewModel.updateName("テスト")
    val slot = mutableListOf<Template>()
    coEvery { repository.saveTemplate(capture(slot)) } returns 1L

    viewModel.save()
    Thread.sleep(200)

    assertEquals("", slot.last().bodyLlmPrompt)
    assertTrue(viewModel.uiState.value.isSaved)
}

// TC-B-02
@Test
fun save_llmFieldWithEmptyPrompt_preservesEmptyLlmPrompt() {
    val viewModel = createViewModel()
    viewModel.updateName("テスト")
    viewModel.addField(TemplateFieldEditState(key = "k", valueSource = FieldValueSource.LLM, llmPrompt = ""))
    val slot = mutableListOf<Template>()
    coEvery { repository.saveTemplate(capture(slot)) } returns 1L

    viewModel.save()
    Thread.sleep(200)

    assertEquals(FieldValueSource.LLM, slot.last().fields[0].valueSource)
    assertEquals("", slot.last().fields[0].llmPrompt)
}

// TC-B-03
@Test
fun save_multipleFieldsWithLlmMixed_preservesLlmPromptMapping() {
    val viewModel = createViewModel()
    viewModel.updateName("テスト")
    viewModel.addField(TemplateFieldEditState(key = "src", valueSource = FieldValueSource.URL))
    viewModel.addField(TemplateFieldEditState(key = "cat", valueSource = FieldValueSource.LLM, llmPrompt = "推測して"))
    val slot = mutableListOf<Template>()
    coEvery { repository.saveTemplate(capture(slot)) } returns 1L

    viewModel.save()
    Thread.sleep(200)

    assertEquals("src", slot.last().fields[0].key)
    assertEquals("", slot.last().fields[0].llmPrompt)
    assertEquals("cat", slot.last().fields[1].key)
    assertEquals("推測して", slot.last().fields[1].llmPrompt)
    assertEquals(FieldValueSource.LLM, slot.last().fields[1].valueSource)
    assertEquals(0, slot.last().fields[0].sortOrder)
    assertEquals(1, slot.last().fields[1].sortOrder)
}
```

（各テストは実ファイル上では日本語コメント「【テスト目的】/【テスト内容】/【期待される動作】/信頼性レベル/【テストデータ準備】/【実際の処理実行】/【結果検証】」を全項目に付与済み。詳細は実ファイル参照。）

### 統合テスト（追記部分）

`app/src/androidTest/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreenTest.kt` の末尾（`fixedSource_showsDefaultValueField()` の後）に追記した。既存インポートに加え、以下を追加した。

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
```

追加した各テストの要点:

```kotlin
// TC-N-04 / TC-104-01
@Test
fun llmSource_showsLlmPromptField() {
    composeTestRule.setContent {
        TemplateEditScreen(templateId = null, viewModel = createViewModel(), onNavigateBack = {})
    }
    composeTestRule.onNodeWithText("フィールドを追加").performClick()
    composeTestRule.onNodeWithText("LLM生成").performClick()
    composeTestRule.onNodeWithText("LLMプロンプト").assertExists()
}

// TC-N-05
@Test
fun bodyLlmPromptInput_updatesViewModel() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
        TemplateEditScreen(templateId = null, viewModel = viewModel, onNavigateBack = {})
    }
    composeTestRule.onNodeWithText("本文用LLMプロンプト").performTextInput("要約して")
    assertEquals("要約して", viewModel.uiState.value.bodyLlmPrompt)
}

// TC-N-06（保存→再読込の往復。templateId の状態変化で LaunchedEffect(templateId) を経由した再読込を誘発）
@Test
fun saveAndReload_restoresBodyLlmPromptAndFieldLlmPrompt() {
    val repository = PersistingFakeEditRepository()
    val viewModel = TemplateEditViewModel(
        templateRepository = repository,
        savedStateHandle = SavedStateHandle(mapOf("templateId" to TemplateEditViewModel.NEW_TEMPLATE_ID)),
    )
    var currentTemplateId by mutableStateOf<Long?>(null)

    composeTestRule.setContent {
        TemplateEditScreen(templateId = currentTemplateId, viewModel = viewModel, onNavigateBack = {})
    }

    composeTestRule.onNodeWithText("テンプレート名").performTextInput("記事テンプレート")
    composeTestRule.onNodeWithText("本文用LLMプロンプト").performTextInput("要約して")
    composeTestRule.onNodeWithText("フィールドを追加").performClick()
    composeTestRule.onNodeWithText("フィールドキー名").performTextInput("category")
    composeTestRule.onNodeWithText("LLM生成").performClick()
    composeTestRule.onNodeWithText("LLMプロンプト").performTextInput("カテゴリを推測して")
    composeTestRule.onNodeWithText("追加").performClick()
    composeTestRule.onNodeWithText("保存").performClick()
    composeTestRule.waitUntil(timeoutMillis = 2000) { viewModel.uiState.value.isSaved }

    currentTemplateId = repository.lastSavedId
    composeTestRule.waitUntil(timeoutMillis = 2000) { viewModel.uiState.value.bodyLlmPrompt == "要約して" }

    assertEquals("要約して", viewModel.uiState.value.bodyLlmPrompt)
    assertEquals("カテゴリを推測して", viewModel.uiState.value.fields[0].llmPrompt)
}

// TC-E-02
@Test
fun nonLlmSource_discardsLlmPrompt() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
        TemplateEditScreen(templateId = null, viewModel = viewModel, onNavigateBack = {})
    }
    composeTestRule.onNodeWithText("フィールドを追加").performClick()
    composeTestRule.onNodeWithText("フィールドキー名").performTextInput("source")
    composeTestRule.onNodeWithText("LLM生成").performClick()
    composeTestRule.onNodeWithText("LLMプロンプト").performTextInput("捨てられるはずのプロンプト")
    composeTestRule.onNodeWithText("URL").performClick()
    composeTestRule.onNodeWithText("追加").performClick()

    assertEquals("", viewModel.uiState.value.fields[0].llmPrompt)
    assertEquals(FieldValueSource.URL, viewModel.uiState.value.fields[0].valueSource)
}
```

TC-N-06 用に、保存内容を保持し `getTemplateById` で復元できる `PersistingFakeEditRepository` を新規追加した（`FakeEditRepository` の下に配置）。

```kotlin
private class PersistingFakeEditRepository : TemplateRepository {
    private val templates = mutableMapOf<Long, Template>()
    private var nextId = 1L
    var lastSavedId: Long = 0L
        private set

    override fun getAllTemplates(): Flow<List<Template>> = flowOf(templates.values.toList())
    override suspend fun getDefaultTemplate(): Template? = null
    override suspend fun getTemplateById(id: Long): Template? = templates[id]
    override suspend fun saveTemplate(template: Template): Long {
        val id = if (template.id == 0L) nextId++ else template.id
        templates[id] = template.copy(id = id)
        lastSavedId = id
        return id
    }
    override suspend fun deleteTemplate(template: Template) {
        templates.remove(template.id)
    }
}
```

---

## 3. テスト実行コマンド

```bash
# 単体テスト
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditViewModelTest"

# 統合テスト（コンパイル確認。実機/エミュレータでの実行は Green フェーズ実装後）
mise exec -- ./gradlew compileDebugAndroidTestKotlin
mise exec -- ./gradlew connectedAndroidTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditScreenTest"
```

---

## 4. 実際の失敗結果（確認済み）

### 単体テスト: `TemplateEditViewModelTest.kt`

`TemplateEditViewModel.updateBodyLlmPrompt()`、`TemplateEditUiState.bodyLlmPrompt`、`TemplateFieldEditState.llmPrompt` が未実装のため、**コンパイルエラー**で失敗することを確認した。

```
> Task :app:compileDebugUnitTestKotlin FAILED
e: TemplateEditViewModelTest.kt:137:19 Unresolved reference 'updateBodyLlmPrompt'.
e: TemplateEditViewModelTest.kt:141:58 Unresolved reference 'bodyLlmPrompt'.
e: TemplateEditViewModelTest.kt:156:19 Unresolved reference 'updateBodyLlmPrompt'.
e: TemplateEditViewModelTest.kt:161:17 No parameter with name 'llmPrompt' found.
e: TemplateEditViewModelTest.kt:215:61 Unresolved reference 'bodyLlmPrompt'.
e: TemplateEditViewModelTest.kt:216:74 Unresolved reference 'llmPrompt'.
e: TemplateEditViewModelTest.kt:239:50 Unresolved reference 'bodyLlmPrompt'.
e: TemplateEditViewModelTest.kt:255:19 Unresolved reference 'updateBodyLlmPrompt'.
e: TemplateEditViewModelTest.kt:308:83 No parameter with name 'llmPrompt' found.
e: TemplateEditViewModelTest.kt:338:85 No parameter with name 'llmPrompt' found.

FAILURE: Build failed with an exception.
> Task :app:compileDebugUnitTestKotlin FAILED
```

### 統合テスト: `TemplateEditScreenTest.kt`

同様に `viewModel.uiState.value.bodyLlmPrompt` / `fields[0].llmPrompt` が未実装のため、**コンパイルエラー**で失敗することを確認した。

```
> Task :app:compileDebugAndroidTestKotlin FAILED
e: TemplateEditScreenTest.kt:196:54 Unresolved reference 'bodyLlmPrompt'.
e: TemplateEditScreenTest.kt:239:37 Unresolved reference 'bodyLlmPrompt'.
e: TemplateEditScreenTest.kt:244:54 Unresolved reference 'bodyLlmPrompt'.
e: TemplateEditScreenTest.kt:245:69 Unresolved reference 'llmPrompt'.
e: TemplateEditScreenTest.kt:277:60 Unresolved reference 'llmPrompt'.

FAILURE: Build failed with an exception.
> Task :app:compileDebugAndroidTestKotlin FAILED
```

すべて「未実装のプロパティ・メソッド呼び出し」に起因する失敗であり、想定通りの Red 状態である（TASK-0068 の前例と同様のパターン）。

統合テストはコンパイル段階で失敗するため、実機/エミュレータ（`emulator-5554`）での実行確認は Green フェーズでの実装完了後に行う。

---

## 5. Greenフェーズで実装すべき内容

TASK-0071.md「実装詳細」に基づき、以下をそのまま実装対象とする。ドメインモデル（`Template.bodyLlmPrompt`、`TemplateField.llmPrompt`、`FieldValueSource.LLM`）はTASK-0056で実装済みのため変更不要。

1. **`TemplateEditViewModel.kt`**:
   - `TemplateEditUiState` に `bodyLlmPrompt: String = ""` を追加
   - `TemplateFieldEditState` に `llmPrompt: String = ""` を追加
   - `updateBodyLlmPrompt(prompt: String)` を追加（`updateBody()` パターン踏襲）
   - `loadTemplate()`: `TemplateEditUiState` 構築時に `bodyLlmPrompt = template.bodyLlmPrompt` を追加、`fields.map {}` 内で `llmPrompt = field.llmPrompt` を追加
   - `save()`: `Template` 構築時に `bodyLlmPrompt = state.bodyLlmPrompt` を追加、`fields.mapIndexed {}` 内で `llmPrompt = field.llmPrompt` を追加

2. **`TemplateEditScreen.kt`**:
   - 本文テンプレート入力欄（`template_body_field`）の下に「本文用LLMプロンプト」ラベルの複数行 `OutlinedTextField`（`minLines = 2` 程度）を追加し、`onValueChange` で `viewModel.updateBodyLlmPrompt()` を呼ぶ
   - `FieldAddDialog` の値取得方法リストに `FieldValueSource.LLM to stringResource(R.string.field_source_llm)` を追加
   - `valueSource == FieldValueSource.LLM` の場合、`field_llm_prompt_label` ラベルの `OutlinedTextField` を表示する状態変数（`llmPromptInput`）を追加
   - `onAdd` 呼び出し時、`llmPrompt = if (valueSource == FieldValueSource.LLM) llmPromptInput else ""` を設定した `TemplateFieldEditState` を渡す（TC-E-02 の要件）

3. **`strings.xml`**: 以下3つの文字列リソースを追加
   ```xml
   <string name="field_source_llm">LLM生成</string>
   <string name="field_llm_prompt_label">LLMプロンプト</string>
   <string name="template_body_llm_prompt_label">本文用LLMプロンプト</string>
   ```

実装後、以下がすべて成功することを Green フェーズのゴールとする。

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditViewModelTest"
mise exec -- ./gradlew connectedAndroidTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditScreenTest"
```

---

## 6. 品質判定

- ✅ テスト実行: 実行可能でコンパイルエラーにより失敗することを確認済み（単体・統合の両方）
- ✅ 期待値: 明確で具体的（bodyLlmPrompt/llmPrompt の厳密文字列・valueSource・sortOrder を明記）
- ✅ アサーション: 適切（既存テストパターン踏襲、capture slot / assertExists / waitUntil を使い分け）
- ✅ 実装方針: 明確（TASK-0071.md 実装詳細1〜8と1:1対応）
- 信頼性レベル分布: 🔵 8件（67%）、🟡 4件（33%）、🔴 0件

**総合判定**: 高品質
