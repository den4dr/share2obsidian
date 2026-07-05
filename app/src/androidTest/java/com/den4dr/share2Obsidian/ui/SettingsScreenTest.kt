package com.den4dr.share2Obsidian.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.den4dr.share2Obsidian.data.datastore.NoteSettings
import com.den4dr.share2Obsidian.data.datastore.NoteSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun createViewModel(
        settings: NoteSettings = NoteSettings(),
    ): SettingsViewModel = SettingsViewModel(FakeNoteSettingsRepository(settings))

    // TC-1: 「テンプレート管理」タップで onNavigateToTemplates が呼ばれる
    @Test
    fun templateManagementItem_callsOnNavigateToTemplates() {
        var called = false
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = { called = true },
                viewModel = createViewModel(),
            )
        }
        composeTestRule.onNodeWithText("テンプレート管理").performClick()
        assertTrue("onNavigateToTemplates が呼ばれること", called)
    }

    // SettingsScreen にテンプレート管理メニューが表示される
    @Test
    fun settingsScreen_showsTemplateManagementItem() {
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(),
            )
        }
        composeTestRule.onNodeWithText("テンプレート管理").assertIsDisplayed()
    }

    // vault/folder の初期値が DataStore 設定で表示される（REQ-021）
    @Test
    fun settingsScreen_showsVaultAndFolderValues() {
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(NoteSettings(vault = "MyVault", folder = "Notes")),
            )
        }
        composeTestRule.onNodeWithText("MyVault").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notes").assertIsDisplayed()
    }
}

private class FakeNoteSettingsRepository(
    private val settings: NoteSettings = NoteSettings(),
) : NoteSettingsRepository {
    override fun getSettings(): Flow<NoteSettings> = flowOf(settings)
    override suspend fun saveVault(vault: String) {}
    override suspend fun saveFolder(folder: String) {}
}
