package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.util.WebViewExtractionResult
import com.den4dr.share2Obsidian.util.WebViewExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // TC-TASK0036-1: metadata が HtmlMetaKey で正しくマッピングされる
    @Test
    fun `metadata is mapped from WebViewExtractionResult`() = runBlocking {
        val extractor = fakeExtractor(
            WebViewExtractionResult(
                bodyText = "本文",
                ogTitle = "テスト記事",
                author = "著者名",
            )
        )
        val processor = UrlContentProcessor(extractor)
        val result = processor.process(ShareContent.Url(url = "https://example.com"))
        assertEquals("テスト記事", result.metadata[HtmlMetaKey.OG_TITLE])
        assertEquals("著者名", result.metadata[HtmlMetaKey.AUTHOR])
        assertEquals("https://example.com", result.metadata[HtmlMetaKey.URL])
    }

    // TC-TASK0036-2: sourceUrl が ProcessedContent.sourceUrl に設定される
    @Test
    fun `sourceUrl is set from url`() = runBlocking {
        val extractor = fakeExtractor(WebViewExtractionResult(bodyText = "本文"))
        val processor = UrlContentProcessor(extractor)
        val result = processor.process(ShareContent.Url(url = "https://example.com"))
        assertEquals("https://example.com", result.sourceUrl)
    }

    // TC-TASK0036-3: 空のメタデータフィールドはマップに含まれない
    @Test
    fun `empty metadata fields are excluded`() = runBlocking {
        val extractor = fakeExtractor(
            WebViewExtractionResult(
                bodyText = "本文",
                ogTitle = "",
                ogDescription = "",
            )
        )
        val processor = UrlContentProcessor(extractor)
        val result = processor.process(ShareContent.Url(url = "https://example.com"))
        assertNull(result.metadata[HtmlMetaKey.OG_TITLE])
        assertNull(result.metadata[HtmlMetaKey.OG_DESCRIPTION])
        // URL は常に含まれる
        assertEquals("https://example.com", result.metadata[HtmlMetaKey.URL])
    }
}
