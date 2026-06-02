package com.den4dr.share2Obsidian.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewExtractionResultExtensionTest {

    // TC-2: 新フィールドのデフォルト値確認
    @Test
    fun webViewExtractionResult_newFieldsDefaultToEmpty() {
        val result = WebViewExtractionResult(bodyText = "test")
        assertEquals("", result.ogTitle)
        assertEquals("", result.ogDescription)
        assertEquals("", result.publishedTime)
        assertEquals("", result.modifiedTime)
        assertEquals("", result.author)
    }

    // TC-2b: 新フィールドに値を設定できる
    @Test
    fun webViewExtractionResult_canSetNewFields() {
        val result = WebViewExtractionResult(
            bodyText = "test",
            ogTitle = "My Title",
            ogDescription = "Description",
            publishedTime = "2026-01-01",
            modifiedTime = "2026-06-01",
            author = "Author",
        )
        assertEquals("My Title", result.ogTitle)
        assertEquals("Description", result.ogDescription)
        assertEquals("2026-01-01", result.publishedTime)
        assertEquals("2026-06-01", result.modifiedTime)
        assertEquals("Author", result.author)
    }
}
