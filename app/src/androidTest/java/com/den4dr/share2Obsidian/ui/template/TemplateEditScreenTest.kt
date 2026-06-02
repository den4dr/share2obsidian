package com.den4dr.share2Obsidian.ui.template

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.den4dr.share2Obsidian.data.repository.TemplateRepository
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
        composeTestRule.onNodeWithText("デフォルト値").assertIsDisplayed()
    }
}

private class FakeEditRepository : TemplateRepository {
    override fun getAllTemplates(): Flow<List<Template>> = flowOf(emptyList())
    override suspend fun getDefaultTemplate(): Template? = null
    override suspend fun getTemplateById(id: Long): Template? = null
    override suspend fun saveTemplate(template: Template): Long = 1L
    override suspend fun deleteTemplate(template: Template) {}
}
