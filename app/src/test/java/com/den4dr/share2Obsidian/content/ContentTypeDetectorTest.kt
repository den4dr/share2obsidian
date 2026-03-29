package com.den4dr.share2Obsidian.content

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentTypeDetectorTest {

    private fun makeIntent(
        mimeType: String,
        text: String? = null,
        htmlText: String? = null,
        subject: String? = null,
        stream: Uri? = null
    ): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        htmlText?.let { putExtra(Intent.EXTRA_HTML_TEXT, it) }
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        stream?.let { putExtra(Intent.EXTRA_STREAM, it) }
    }

    @Test
    fun `text plain with URL returns ShareContent Url`() {
        val intent = makeIntent("text/plain", text = "https://example.com")
        val result = ContentTypeDetector.detect(intent)
        assertTrue(result is ShareContent.Url)
        assertEquals("https://example.com", (result as ShareContent.Url).url)
    }

    @Test
    fun `text plain with http URL returns ShareContent Url`() {
        val intent = makeIntent("text/plain", text = "http://example.com/path?q=1")
        val result = ContentTypeDetector.detect(intent)
        assertTrue(result is ShareContent.Url)
    }

    @Test
    fun `text plain with non URL returns ShareContent Text`() {
        val intent = makeIntent("text/plain", text = "普通のテキスト")
        val result = ContentTypeDetector.detect(intent)
        assertTrue(result is ShareContent.Text)
        assertEquals("普通のテキスト", (result as ShareContent.Text).text)
    }

    @Test
    fun `text html returns ShareContent Html with both html and fallback`() {
        val intent = makeIntent("text/html", text = "Bold", htmlText = "<b>Bold</b>")
        val result = ContentTypeDetector.detect(intent)
        assertTrue(result is ShareContent.Html)
        val html = result as ShareContent.Html
        assertEquals("<b>Bold</b>", html.html)
        assertEquals("Bold", html.fallbackText)
    }

    @Test
    fun `image mime returns ShareContent File`() {
        val uri = Uri.parse("content://media/external/images/1")
        val intent = makeIntent("image/png", stream = uri)
        val result = ContentTypeDetector.detect(intent)
        assertTrue(result is ShareContent.File)
        assertEquals(uri, (result as ShareContent.File).uri)
    }

    @Test
    fun `application mime returns ShareContent File`() {
        val uri = Uri.parse("content://com.example/docs/1")
        val intent = makeIntent("application/pdf", stream = uri)
        val result = ContentTypeDetector.detect(intent)
        assertTrue(result is ShareContent.File)
    }

    @Test
    fun `EXTRA_SUBJECT sets title`() {
        val intent = makeIntent("text/plain", text = "本文", subject = "タイトル")
        val result = ContentTypeDetector.detect(intent)
        assertTrue(result is ShareContent.Text)
        assertEquals("タイトル", (result as ShareContent.Text).title)
    }

    @Test
    fun `null mime type returns null`() {
        val intent = Intent(Intent.ACTION_SEND)
        val result = ContentTypeDetector.detect(intent)
        assertNull(result)
    }

    @Test
    fun `text plain with no text returns null`() {
        val intent = makeIntent("text/plain")
        val result = ContentTypeDetector.detect(intent)
        assertNull(result)
    }

    @Test
    fun `URL with surrounding whitespace is detected as URL`() {
        val intent = makeIntent("text/plain", text = "  https://example.com  ")
        val result = ContentTypeDetector.detect(intent)
        assertTrue(result is ShareContent.Url)
        assertEquals("https://example.com", (result as ShareContent.Url).url)
    }
}
