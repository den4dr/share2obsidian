package com.den4dr.share2Obsidian.ui.template

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.den4dr.share2Obsidian.data.repository.TemplateRepository
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateEditScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun createViewModel(templateId: Long = TemplateEditViewModel.NEW_TEMPLATE_ID) =
        TemplateEditViewModel(
            templateRepository = FakeEditRepository(),
            savedStateHandle = SavedStateHandle(mapOf("templateId" to templateId)),
        )

    // TC-1: テンプレート名入力が ViewModel に反映
    @Test
    fun nameInput_updatesViewModel() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = viewModel,
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithText("テンプレート名").performTextInput("Web記事")
        assertEquals("Web記事", viewModel.uiState.value.name)
    }

    // TC-2: 保存ボタンで save() が実行され isSaved=true になる
    @Test
    fun saveButton_triggersSaveAndNavigatesBack() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = viewModel,
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithText("保存").performClick()
        composeTestRule.waitUntil(timeoutMillis = 2000) { viewModel.uiState.value.isSaved }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    // 新規モードで「テンプレート作成」タイトルが表示される
    @Test
    fun newMode_showsCreateTitle() {
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = createViewModel(),
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithText("テンプレート作成").assertIsDisplayed()
    }

    // TC-TASK0033-1: フィールド追加後に一覧に表示される
    @Test
    fun addField_appearsInFieldList() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = viewModel,
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithText("フィールドを追加").performClick()
        composeTestRule.onNodeWithText("フィールドキー名").performTextInput("source")
        composeTestRule.onNodeWithText("追加").performClick()
        composeTestRule.onNodeWithText("source").assertIsDisplayed()
    }

    // TC-051-01/02: vault/folder 入力欄が存在しない
    @Test
    fun vaultAndFolderFields_notPresent() {
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = createViewModel(),
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithText("Vault").assertDoesNotExist()
    }

    // TC-051-03/04: 本文テンプレート入力が ViewModel に反映される
    @Test
    fun bodyInput_updatesViewModel() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = viewModel,
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithText("本文テンプレート").performTextInput("## 記事\n{{content}}")
        assertEquals("## 記事\n{{content}}", viewModel.uiState.value.body)
    }

    // TC-TASK0033-2: valueSource=FIXED 選択時に defaultValue フィールドが表示
    @Test
    fun fixedSource_showsDefaultValueField() {
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = createViewModel(),
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithText("フィールドを追加").performClick()
        composeTestRule.onNodeWithText("固定値").performClick()
        // 固定値選択で defaultValue 欄が composition に追加されることを確認する
        // （ダイアログのビューポートに収まらない場合があるため assertExists で検証）
        composeTestRule.onNodeWithText("デフォルト値").assertExists()
    }

    // ==================== TASK-0071: LLM UI追加 Redフェーズ追加テスト ====================

    // TC-N-04 / TC-104-01: FieldAddDialogで「LLM生成」を選択するとLLMプロンプト入力欄が表示される
    @Test
    fun llmSource_showsLlmPromptField() {
        // 【テスト目的】: 値取得方法「LLM生成」選択でプロンプト入力欄が表示されることを確認する（受け入れ基準 TC-104-01）
        // 【テスト内容】: FieldAddDialog を開き「LLM生成」を選択し、"LLMプロンプト" ラベル欄の存在を確認する
        // 【期待される動作】: field_source_llm 選択で field_llm_prompt_label ラベルの OutlinedTextField が composition に追加される
        // 🔵 信頼性レベル: 要件定義書 6.2-1・TC-104-01・既存 fixedSource_showsDefaultValueField() 踏襲

        // 【環境初期化】: Fake リポジトリで新規モードの画面を構築する
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = createViewModel(),
                onNavigateBack = {},
            )
        }

        // 【実際の処理実行】: フィールド追加ダイアログを開き「LLM生成」を選択する
        // 【操作内容】: 既存 fixedSource_showsDefaultValueField() と同じ操作パターンのLLM版
        composeTestRule.onNodeWithText("フィールドを追加").performClick()
        composeTestRule.onNodeWithText("LLM生成").performClick()

        // 【結果検証】: プロンプト入力欄が存在することを確認する（ダイアログのビューポート外可能性のため assertExists）
        // 【期待値確認】: REQ-104「当該フィールド用のプロンプト入力欄を表示しなければならない」に対応
        composeTestRule.onNodeWithText("LLMプロンプト").assertExists() // 【確認内容】: LLM選択でプロンプト欄が現れること
    }

    // TC-N-05: 本文用LLMプロンプト入力が ViewModel に反映される
    @Test
    fun bodyLlmPromptInput_updatesViewModel() {
        // 【テスト目的】: 本文用LLMプロンプト欄への入力が viewModel.uiState.bodyLlmPrompt に反映されることを確認する
        // 【テスト内容】: "本文用LLMプロンプト" ラベルの入力欄にテキストを入力し状態を検証する
        // 【期待される動作】: onValueChange が viewModel.updateBodyLlmPrompt() を呼ぶ
        // 🔵 信頼性レベル: 要件定義書 2.3・既存 bodyInput_updatesViewModel() 踏襲

        // 【環境初期化】: Fake リポジトリで新規モードの画面を構築する
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = viewModel,
                onNavigateBack = {},
            )
        }

        // 【実際の処理実行】: 本文用LLMプロンプト欄にテキストを入力する
        // 【操作内容】: 本文プロンプト欄のUI配線を検証する代表入力（既存 bodyInput_updatesViewModel() のLLM版）
        composeTestRule.onNodeWithText("本文用LLMプロンプト").performTextInput("要約して")

        // 【結果検証】: viewModel.uiState.bodyLlmPrompt が入力値に一致することを確認する
        // 【期待値確認】: UI入力→ViewModel状態のバインドが要件2.3で規定されているため
        assertEquals("要約して", viewModel.uiState.value.bodyLlmPrompt) // 【確認内容】: 本文用LLMプロンプト欄の入力が状態に反映されること
    }

    // TC-N-06: 保存→再読込で本文用・フィールド用LLMプロンプトが復元表示される
    @Test
    fun saveAndReload_restoresBodyLlmPromptAndFieldLlmPrompt() {
        // 【テスト目的】: 本文用LLMプロンプトとLLM生成フィールドのプロンプトを入力・保存し、同一テンプレートIDで再表示した際に両値が復元されることを確認する
        // 【テスト内容】: 保存→再読込の往復で両プロンプトがUIに復元されるかを検証する
        // 【期待される動作】: save() で永続化された値が loadTemplate() 経由でUIに戻る
        // 🔵 信頼性レベル: 要件定義書 6.2-2・note.md 統合テスト2

        // 【環境初期化】: 保存内容を保持する Fake リポジトリを使用し、templateId の変化で再読込を誘発する
        val repository = PersistingFakeEditRepository()
        val viewModel = TemplateEditViewModel(
            templateRepository = repository,
            savedStateHandle = SavedStateHandle(mapOf("templateId" to TemplateEditViewModel.NEW_TEMPLATE_ID)),
        )
        var currentTemplateId by mutableStateOf<Long?>(null)

        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = currentTemplateId,
                viewModel = viewModel,
                onNavigateBack = {},
            )
        }

        // 【実際の処理実行】: 名称・本文用LLMプロンプト・LLM生成フィールドを入力して保存する
        // 【操作内容】: 要件の主要ユースケース（4.1 基本的な使用パターン）を再現する
        composeTestRule.onNodeWithText("テンプレート名").performTextInput("記事テンプレート")
        composeTestRule.onNodeWithText("本文用LLMプロンプト").performTextInput("要約して")
        composeTestRule.onNodeWithText("フィールドを追加").performClick()
        composeTestRule.onNodeWithText("フィールドキー名").performTextInput("category")
        composeTestRule.onNodeWithText("LLM生成").performClick()
        composeTestRule.onNodeWithText("LLMプロンプト").performTextInput("カテゴリを推測して")
        composeTestRule.onNodeWithText("追加").performClick()
        composeTestRule.onNodeWithText("保存").performClick()
        composeTestRule.waitUntil(timeoutMillis = 2000) { viewModel.uiState.value.isSaved }

        // 【実際の処理実行】: 保存済みIDへ切り替え、TemplateEditScreen の LaunchedEffect(templateId) 経由で再読込させる
        // 【処理内容】: Fake リポジトリが保存した Template（bodyLlmPrompt/llmPrompt 含む）を getTemplateById で返す
        currentTemplateId = repository.lastSavedId
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            viewModel.uiState.value.bodyLlmPrompt == "要約して"
        }

        // 【結果検証】: 本文用LLMプロンプト・フィールドのLLMプロンプトが保存した値のまま復元されていることを確認する
        // 【期待値確認】: 要件6.2-2・完了条件「loadTemplate() で復元される」の統合レベル担保
        assertEquals("要約して", viewModel.uiState.value.bodyLlmPrompt) // 【確認内容】: 本文用LLMプロンプトが復元されること
        assertEquals("カテゴリを推測して", viewModel.uiState.value.fields[0].llmPrompt) // 【確認内容】: フィールド用LLMプロンプトが復元されること
    }

    // TC-E-02: LLM以外の値取得方法で追加すると llmPrompt が空文字になる
    @Test
    fun nonLlmSource_discardsLlmPrompt() {
        // 【テスト目的】: FieldAddDialog でLLMを一旦選びプロンプト入力後、別ソースへ切替えて追加した場合、保存される llmPrompt が空文字になることを確認する
        // 【テスト内容】: LLM選択→プロンプト入力→URLへ切替→追加、という操作を再現し追加されたフィールドの llmPrompt を検証する
        // 【期待される動作】: onAdd に渡る TemplateFieldEditState の llmPrompt が "" になる（if (valueSource == LLM) llmPromptInput else "" の分岐）
        // 🟡 信頼性レベル: 要件定義書 4.3 EDGE（TASK-0071実装詳細7の分岐からの妥当推測）

        // 【環境初期化】: Fake リポジトリで新規モードの画面を構築する
        val viewModel = createViewModel()
        composeTestRule.setContent {
            TemplateEditScreen(
                templateId = null,
                viewModel = viewModel,
                onNavigateBack = {},
            )
        }

        // 【実際の処理実行】: LLMを選択しプロンプトを入力した後、URLへ切り替えて追加する
        // 【操作内容】: ユーザーが選択を迷って切り替えるUI操作を再現する
        composeTestRule.onNodeWithText("フィールドを追加").performClick()
        composeTestRule.onNodeWithText("フィールドキー名").performTextInput("source")
        composeTestRule.onNodeWithText("LLM生成").performClick()
        composeTestRule.onNodeWithText("LLMプロンプト").performTextInput("捨てられるはずのプロンプト")
        composeTestRule.onNodeWithText("URL").performClick()
        composeTestRule.onNodeWithText("追加").performClick()

        // 【結果検証】: 追加されたフィールドの llmPrompt が空文字であることを確認する
        // 【期待値確認】: 不要データが混入しないことを保証する（後続 TASK-0072/0073 の入力健全性）
        assertEquals("", viewModel.uiState.value.fields[0].llmPrompt) // 【確認内容】: 非LLMソース追加時に llmPrompt が破棄されること
        assertEquals(FieldValueSource.URL, viewModel.uiState.value.fields[0].valueSource) // 【確認内容】: 値取得方法が URL で保存されること
    }
}

private class FakeEditRepository : TemplateRepository {
    override fun getAllTemplates(): Flow<List<Template>> = flowOf(emptyList())
    override suspend fun getDefaultTemplate(): Template? = null
    override suspend fun getTemplateById(id: Long): Template? = null
    override suspend fun saveTemplate(template: Template): Long = 1L
    override suspend fun deleteTemplate(template: Template) {}
}

// TASK-0071統合テスト用: 保存内容を保持し getTemplateById で復元できる Fake リポジトリ
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
