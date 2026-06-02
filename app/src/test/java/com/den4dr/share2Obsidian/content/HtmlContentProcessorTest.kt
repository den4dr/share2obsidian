package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // TC-TASK0037-1: og:title タグからメタデータが抽出される
    @Test
    fun `og_title is extracted from meta tag`() = runBlocking {
        val html = """
            <html><head>
            <meta property="og:title" content="テスト記事">
            </head><body>本文</body></html>
        """.trimIndent()
        val result = processor.process(ShareContent.Html(html = html, fallbackText = ""))
        assertEquals("テスト記事", result.metadata[HtmlMetaKey.OG_TITLE])
    }

    // TC-TASK0037-2: og:title がない場合は <title> タグを使用
    @Test
    fun `title_tag used when og_title absent`() = runBlocking {
        val html = """
            <html><head><title>ページタイトル</title></head><body>本文</body></html>
        """.trimIndent()
        val result = processor.process(ShareContent.Html(html = html, fallbackText = ""))
        assertEquals("ページタイトル", result.metadata[HtmlMetaKey.OG_TITLE])
    }

    // TC-TASK0037-3: メタデータが一切ない場合はキーが存在しない
    @Test
    fun `no metadata results in absent keys`() = runBlocking {
        val html = "<html><body>本文のみ</body></html>"
        val result = processor.process(ShareContent.Html(html = html, fallbackText = ""))
        assertNull(result.metadata[HtmlMetaKey.OG_DESCRIPTION])
        assertNull(result.metadata[HtmlMetaKey.AUTHOR])
    }
}
