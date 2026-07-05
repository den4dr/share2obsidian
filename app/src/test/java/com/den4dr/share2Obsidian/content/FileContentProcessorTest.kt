package com.den4dr.share2Obsidian.content

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileContentProcessorTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val processor = FileContentProcessor(context)
    private val testUri = Uri.parse("content://com.example/files/image.png")

    @Test
    fun `process sets clipboard with file uri`() = runBlocking {
        val content = ShareContent.File(uri = testUri)
        processor.process(content)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertNotNull(clipboard.primaryClip)
        assertEquals(testUri, clipboard.primaryClip?.getItemAt(0)?.uri)
    }

    @Test
    fun `process returns non-empty body`() = runBlocking {
        val content = ShareContent.File(uri = testUri)
        val result = processor.process(content)
        assertFalse(result.body.isEmpty())
    }

    @Test
    fun `process returns contentType FILE`() = runBlocking {
        val content = ShareContent.File(uri = testUri)
        val result = processor.process(content)
        assertEquals(ContentKind.FILE, result.contentType)
    }

    @Test
    fun `process preserves title`() = runBlocking {
        val content = ShareContent.File(uri = testUri, title = "添付ファイル")
        val result = processor.process(content)
        assertEquals("添付ファイル", result.title)
    }
}
