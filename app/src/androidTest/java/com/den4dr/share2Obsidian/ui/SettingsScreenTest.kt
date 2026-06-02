package com.den4dr.share2Obsidian.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // TC-1: 「テンプレート管理」タップで onNavigateToTemplates が呼ばれる
    @Test
    fun templateManagementItem_callsOnNavigateToTemplates() {
        var called = false
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = { called = true },
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
            )
        }
        composeTestRule.onNodeWithText("テンプレート管理").assertIsDisplayed()
    }
}
