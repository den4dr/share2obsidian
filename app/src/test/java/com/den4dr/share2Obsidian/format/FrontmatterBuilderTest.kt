package com.den4dr.share2Obsidian.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FrontmatterBuilderTest {

    @Test
    fun `build with title returns frontmatter with title and tags`() {
        val result = FrontmatterBuilder.build(title = "テスト", body = "本文テキスト")
        assertEquals(
            "---\ntitle: \"テスト\"\ntags: [shared]\n---\n\n本文テキスト",
            result
        )
    }

    @Test
    fun `build with null title returns frontmatter with tags only`() {
        val result = FrontmatterBuilder.build(title = null, body = "本文テキスト")
        assertEquals(
            "---\ntags: [shared]\n---\n\n本文テキスト",
            result
        )
    }

    @Test
    fun `build with empty body does not crash`() {
        val result = FrontmatterBuilder.build(title = null, body = "")
        assertEquals(
            "---\ntags: [shared]\n---\n\n",
            result
        )
    }

    @Test
    fun `build with title containing special characters`() {
        val result = FrontmatterBuilder.build(title = "Hello \"World\"", body = "body")
        assertEquals(
            "---\ntitle: \"Hello \"World\"\"\ntags: [shared]\n---\n\nbody",
            result
        )
    }
}
