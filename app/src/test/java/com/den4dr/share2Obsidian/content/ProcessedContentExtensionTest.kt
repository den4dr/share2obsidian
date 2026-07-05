package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessedContentExtensionTest {

    // TC-1: 既存 API でのデフォルト値確認
    @Test
    fun processedContent_defaultMetadataAndSourceUrl() {
        val content = ProcessedContent(
            body = "テスト本文",
            contentType = ContentKind.TEXT,
        )
        assertTrue(content.metadata.isEmpty())
        assertNull(content.sourceUrl)
    }

    // TC-1b: metadata と sourceUrl に値を設定できる
    @Test
    fun processedContent_withMetadataAndSourceUrl() {
        val content = ProcessedContent(
            body = "本文",
            contentType = ContentKind.URL,
            metadata = mapOf(HtmlMetaKey.OG_TITLE to "タイトル"),
            sourceUrl = "https://example.com",
        )
        assertEquals("タイトル", content.metadata[HtmlMetaKey.OG_TITLE])
        assertEquals("https://example.com", content.sourceUrl)
    }
}
