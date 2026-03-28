package com.den4dr.share2Obsidian.format

import com.den4dr.share2Obsidian.AppConfig

object FrontmatterBuilder {
    fun build(title: String?, body: String): String {
        val tags = AppConfig.OBSIDIAN_TAGS.joinToString(", ")
        return if (title != null) {
            "---\ntitle: \"$title\"\ntags: [$tags]\n---\n\n$body"
        } else {
            "---\ntags: [$tags]\n---\n\n$body"
        }
    }
}
