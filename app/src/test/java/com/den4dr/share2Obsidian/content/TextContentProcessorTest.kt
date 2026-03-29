package com.den4dr.share2Obsidian.content

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextContentProcessorTest {

    private val processor = TextContentProcessor()

    @Test
    fun `process returns body and title unchanged`() = runBlocking {
        val content = ShareContent.Text(text = "サンプルテキスト", title = "タイトル")
        val result = processor.process(content)
        assertEquals("サンプルテキスト", result.body)
        assertEquals("タイトル", result.title)
        assertEquals(ContentKind.TEXT, result.contentType)
    }

    @Test
    fun `process with null title returns null title`() = runBlocking {
        val content = ShareContent.Text(text = "テキスト", title = null)
        val result = processor.process(content)
        assertEquals("テキスト", result.body)
        assertNull(result.title)
        assertEquals(ContentKind.TEXT, result.contentType)
    }

    @Test
    fun `process with empty text does not crash`() = runBlocking {
        val content = ShareContent.Text(text = "", title = null)
        val result = processor.process(content)
        assertEquals("", result.body)
        assertEquals(ContentKind.TEXT, result.contentType)
    }
}
