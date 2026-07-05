package com.den4dr.share2Obsidian.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LoadingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingScreen_showsProgressAndMessage() {
        composeTestRule.setContent {
            LoadingScreen()
        }
        composeTestRule.onNodeWithText("URLの内容を取得中...").assertIsDisplayed()
    }
}
