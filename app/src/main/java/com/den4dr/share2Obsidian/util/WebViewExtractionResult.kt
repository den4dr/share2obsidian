package com.den4dr.share2Obsidian.util

data class WebViewExtractionResult(
    val bodyText: String?,
    val pageTitle: String? = null,
    val ogTitle: String = "",
    val ogDescription: String = "",
    val publishedTime: String = "",
    val modifiedTime: String = "",
    val author: String = "",
)
