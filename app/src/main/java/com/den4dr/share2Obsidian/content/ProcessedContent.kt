package com.den4dr.share2Obsidian.content

data class ProcessedContent(
    val body: String,
    val title: String? = null,
    val contentType: ContentKind
)

enum class ContentKind {
    TEXT, URL, HTML, FILE
}
