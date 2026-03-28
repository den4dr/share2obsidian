package com.den4dr.share2Obsidian.content

import android.net.Uri

sealed class ShareContent {
    data class Text(
        val text: String,
        val title: String? = null
    ) : ShareContent()

    data class Url(
        val url: String,
        val title: String? = null
    ) : ShareContent()

    data class Html(
        val html: String?,
        val fallbackText: String = "",
        val title: String? = null
    ) : ShareContent()

    data class File(
        val uri: Uri,
        val title: String? = null
    ) : ShareContent()
}
