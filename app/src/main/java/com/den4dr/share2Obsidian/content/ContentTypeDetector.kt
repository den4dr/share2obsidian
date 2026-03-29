package com.den4dr.share2Obsidian.content

import android.content.Intent
import android.net.Uri

object ContentTypeDetector {
    private val URL_PATTERN = Regex("^https?://[^\\s]+$")

    fun detect(intent: Intent): ShareContent? {
        val mimeType = intent.type ?: return null
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        return when {
            mimeType == "text/html" -> {
                val html = intent.getStringExtra("android.intent.extra.HTML_TEXT")
                val fallback = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                ShareContent.Html(html = html, fallbackText = fallback, title = title)
            }
            mimeType == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
                if (URL_PATTERN.matches(text.trim())) {
                    ShareContent.Url(url = text.trim(), title = title)
                } else {
                    ShareContent.Text(text = text, title = title)
                }
            }
            mimeType.startsWith("image/") || mimeType.startsWith("application/") -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
                ShareContent.File(uri = uri, title = title)
            }
            else -> null
        }
    }
}
