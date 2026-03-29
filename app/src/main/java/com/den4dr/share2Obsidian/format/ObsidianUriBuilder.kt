package com.den4dr.share2Obsidian.format

import android.net.Uri
import com.den4dr.share2Obsidian.AppConfig

object ObsidianUriBuilder {
    private val BASE_URI = Uri.parse("obsidian://new")

    fun build(content: String, title: String?): Uri {
        return BASE_URI.buildUpon()
            .appendQueryParameter("content", content)
            .appendQueryParameter("title", title ?: "")
            .appendQueryParameter("vault", AppConfig.OBSIDIAN_VAULT)
            .appendQueryParameter("folder", AppConfig.OBSIDIAN_FOLDER)
            .build()
    }
}
