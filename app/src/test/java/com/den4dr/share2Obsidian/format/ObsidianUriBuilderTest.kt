package com.den4dr.share2Obsidian.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObsidianUriBuilderTest {

    @Test
    fun `build with content and title returns obsidian uri`() {
        val uri = ObsidianUriBuilder.build("本文", "タイトル")
        assertEquals("obsidian", uri.scheme)
        assertEquals("new", uri.host)
        assertEquals("本文", uri.getQueryParameter("content"))
        assertEquals("タイトル", uri.getQueryParameter("title"))
    }

    @Test
    fun `build with null title uses empty string`() {
        val uri = ObsidianUriBuilder.build("本文", null)
        assertEquals("", uri.getQueryParameter("title"))
    }

    @Test
    fun `build includes vault and folder from AppConfig`() {
        val uri = ObsidianUriBuilder.build("本文", null)
        assertNotNull(uri.getQueryParameter("vault"))
        assertNotNull(uri.getQueryParameter("folder"))
    }

    @Test
    fun `build with special characters encodes properly`() {
        val content = "# タイトル\n本文 & 記号 <test>"
        val uri = ObsidianUriBuilder.build(content, null)
        assertNotNull(uri)
        assertEquals(content, uri.getQueryParameter("content"))
    }
}
