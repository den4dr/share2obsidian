package com.den4dr.share2Obsidian.content

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentProcessorTest {

    private val processor = HtmlContentProcessor()

    @Test
    fun `html is converted to markdown`() = runBlocking {
        val content = ShareContent.Html(html = "<h1>タイトル</h1>", fallbackText = "タイトル")
        val result = processor.process(content)
        assertTrue(result.body.contains("# タイトル"))
        assertEquals(ContentKind.HTML, result.contentType)
    }

    @Test
    fun `null html uses fallbackText`() = runBlocking {
        val content = ShareContent.Html(html = null, fallbackText = "フォールバックテキスト")
        val result = processor.process(content)
        assertEquals("フォールバックテキスト", result.body)
        assertEquals(ContentKind.HTML, result.contentType)
    }

    @Test
    fun `title is preserved`() = runBlocking {
        val content = ShareContent.Html(html = "<p>本文</p>", fallbackText = "", title = "タイトル")
        val result = processor.process(content)
        assertEquals("タイトル", result.title)
    }

    @Test
    fun `empty html returns empty body`() = runBlocking {
        val content = ShareContent.Html(html = "", fallbackText = "fallback")
        val result = processor.process(content)
        assertEquals("", result.body)
    }

    @Test
    fun `contentType is always HTML`() = runBlocking {
        val content = ShareContent.Html(html = null, fallbackText = "")
        val result = processor.process(content)
        assertEquals(ContentKind.HTML, result.contentType)
    }
}
