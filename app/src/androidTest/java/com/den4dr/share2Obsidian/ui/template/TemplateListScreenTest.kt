package com.den4dr.share2Obsidian.ui.template

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.den4dr.share2Obsidian.data.repository.TemplateRepository
import com.den4dr.share2Obsidian.domain.model.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val template1 = Template(
        id = 1L, name = "テスト1", isDefault = false, fields = emptyList()
    )
    private val template2 = Template(
        id = 2L, name = "デフォルトテンプレート", isDefault = true, fields = emptyList()
    )

    // TC-1: テンプレートリストが表示される
    @Test
    fun templateList_displayedCorrectly() {
        val viewModel = TemplateListViewModel(FakeTemplateRepository(listOf(template1, template2)))
        composeTestRule.setContent {
            TemplateListScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToEdit = {},
            )
        }
        composeTestRule.onNodeWithText("テスト1").assertIsDisplayed()
        composeTestRule.onNodeWithText("デフォルトテンプレート").assertIsDisplayed()
        composeTestRule.onNodeWithText("デフォルト").assertIsDisplayed()
    }

    // TC-2: + FAB タップで onNavigateToEdit(null) が呼ばれる
    @Test
    fun fab_click_callsOnNavigateToEditWithNull() {
        var wasCalled = false
        var capturedId: Long? = -1L
        val viewModel = TemplateListViewModel(FakeTemplateRepository(emptyList()))
        composeTestRule.setContent {
            TemplateListScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToEdit = { id ->
                    wasCalled = true
                    capturedId = id
                },
            )
        }
        composeTestRule.onNodeWithContentDescription("テンプレートを追加").performClick()
        assertTrue("onNavigateToEdit が呼ばれること", wasCalled)
        assertNull("null パラメータで呼ばれること", capturedId)
    }

    // TC-3: 削除ボタンタップで確認ダイアログが表示される
    @Test
    fun deleteButton_showsConfirmDialog() {
        val viewModel = TemplateListViewModel(FakeTemplateRepository(listOf(template1)))
        composeTestRule.setContent {
            TemplateListScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToEdit = {},
            )
        }
        composeTestRule.onAllNodesWithContentDescription("削除")[0].performClick()
        composeTestRule.onNodeWithText("テンプレートを削除").assertIsDisplayed()
    }
}

private class FakeTemplateRepository(
    private val templates: List<Template> = emptyList(),
) : TemplateRepository {
    override fun getAllTemplates(): Flow<List<Template>> = flowOf(templates)
    override suspend fun getDefaultTemplate(): Template? = templates.firstOrNull { it.isDefault }
    override suspend fun getTemplateById(id: Long): Template? = templates.firstOrNull { it.id == id }
    override suspend fun saveTemplate(template: Template): Long = template.id
    override suspend fun deleteTemplate(template: Template) {}
}
