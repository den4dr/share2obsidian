package com.den4dr.share2Obsidian.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToMarkdownConverterTest {

    @Test
    fun `h1 converts to markdown heading`() {
        val result = HtmlToMarkdownConverter.convert("<h1>タイトル</h1>")
        assertTrue(result.contains("# タイトル"))
    }

    @Test
    fun `h2 converts to markdown heading`() {
        val result = HtmlToMarkdownConverter.convert("<h2>見出し2</h2>")
        assertTrue(result.contains("## 見出し2"))
    }

    @Test
    fun `h3 converts to markdown heading`() {
        val result = HtmlToMarkdownConverter.convert("<h3>見出し3</h3>")
        assertTrue(result.contains("### 見出し3"))
    }

    @Test
    fun `strong and em convert to bold and italic`() {
        val result = HtmlToMarkdownConverter.convert("<p><strong>太字</strong>と<em>斜体</em></p>")
        assertTrue(result.contains("**太字**"))
        assertTrue(result.contains("*斜体*"))
    }

    @Test
    fun `b and i convert to bold and italic`() {
        val result = HtmlToMarkdownConverter.convert("<b>太字</b><i>斜体</i>")
        assertTrue(result.contains("**太字**"))
        assertTrue(result.contains("*斜体*"))
    }

    @Test
    fun `a tag converts to markdown link`() {
        val result = HtmlToMarkdownConverter.convert("<a href=\"https://example.com\">リンク</a>")
        assertTrue(result.contains("[リンク](https://example.com)"))
    }

    @Test
    fun `li tags convert to list items`() {
        val result = HtmlToMarkdownConverter.convert("<ul><li>項目1</li><li>項目2</li></ul>")
        assertTrue(result.contains("- 項目1"))
        assertTrue(result.contains("- 項目2"))
    }

    @Test
    fun `empty html returns empty string`() {
        val result = HtmlToMarkdownConverter.convert("")
        assertEquals("", result)
    }

    @Test
    fun `p tag adds newlines`() {
        val result = HtmlToMarkdownConverter.convert("<p>段落</p>")
        assertTrue(result.contains("段落"))
    }

    @Test
    fun `br tag converts to newline`() {
        val result = HtmlToMarkdownConverter.convert("行1<br>行2")
        assertTrue(result.contains("行1"))
        assertTrue(result.contains("行2"))
    }
}
