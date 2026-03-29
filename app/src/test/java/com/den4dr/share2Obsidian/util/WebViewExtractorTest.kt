package com.den4dr.share2Obsidian.util

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebViewExtractorTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `extract returns null bodyText on timeout`() = runBlocking {
        // 1ms timeout — WebView will never finish loading in unit tests
        val extractor = WebViewExtractor(context, timeoutMs = 1L)
        val result = extractor.extract("https://example.com")
        assertNull(result.bodyText)
    }
}
