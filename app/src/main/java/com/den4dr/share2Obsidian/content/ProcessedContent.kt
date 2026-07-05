package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey

data class ProcessedContent(
    val body: String,
    val title: String? = null,
    val contentType: ContentKind,
    val metadata: Map<HtmlMetaKey, String> = emptyMap(),
    val sourceUrl: String? = null,
)

enum class ContentKind {
    TEXT, URL, HTML, FILE
}
