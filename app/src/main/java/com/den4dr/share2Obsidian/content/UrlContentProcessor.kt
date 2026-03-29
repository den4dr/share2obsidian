package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.util.WebViewExtractor

class UrlContentProcessor(
    private val webViewExtractor: WebViewExtractor
) : ContentProcessor<ShareContent.Url> {
    override suspend fun process(content: ShareContent.Url): ProcessedContent {
        val result = webViewExtractor.extract(content.url)
        val body = result.bodyText ?: content.url
        return ProcessedContent(
            body = body,
            title = content.title,
            contentType = ContentKind.URL
        )
    }
}
