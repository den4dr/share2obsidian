package com.den4dr.share2Obsidian.format

import com.den4dr.share2Obsidian.AppConfig

object FrontmatterBuilder {
    private val tagsString = AppConfig.OBSIDIAN_TAGS.joinToString(", ")

    fun build(title: String?, body: String): String {
        val titleLine = title?.let { "title: \"$it\"\n" } ?: ""
        return "---\n${titleLine}tags: [$tagsString]\n---\n\n$body"
    }
}
