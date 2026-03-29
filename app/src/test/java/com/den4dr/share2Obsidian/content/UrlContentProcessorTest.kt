package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.util.WebViewExtractionResult
import com.den4dr.share2Obsidian.util.WebViewExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UrlContentProcessorTest {

    private fun fakeExtractor(result: WebViewExtractionResult): WebViewExtractor {
        return object : WebViewExtractor(RuntimeEnvironment.getApplication()) {
            override suspend fun extract(url: String) = result
        }
    }

    @Test
    fun `successful extraction uses bodyText as body`() = runBlocking {
        val extractor = fakeExtractor(WebViewExtractionResult(bodyText = "ページ本文"))
        val processor = UrlContentProcessor(extractor)
        val result = processor.process(ShareContent.Url(url = "https://example.com"))
        assertEquals("ページ本文", result.body)
        assertEquals(ContentKind.URL, result.contentType)
    }

    @Test
    fun `null bodyText falls back to url`() = runBlocking {
        val extractor = fakeExtractor(WebViewExtractionResult(bodyText = null))
        val processor = UrlContentProcessor(extractor)
        val result = processor.process(ShareContent.Url(url = "https://example.com"))
        assertEquals("https://example.com", result.body)
        assertEquals(ContentKind.URL, result.contentType)
    }

    @Test
    fun `title is preserved`() = runBlocking {
        val extractor = fakeExtractor(WebViewExtractionResult(bodyText = "本文"))
        val processor = UrlContentProcessor(extractor)
        val result = processor.process(ShareContent.Url(url = "https://example.com", title = "ページタイトル"))
        assertEquals("ページタイトル", result.title)
    }

    @Test
    fun `contentType is always URL`() = runBlocking {
        val extractor = fakeExtractor(WebViewExtractionResult(bodyText = null))
        val processor = UrlContentProcessor(extractor)
        val result = processor.process(ShareContent.Url(url = "https://example.com"))
        assertEquals(ContentKind.URL, result.contentType)
    }
}
