package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.util.WebViewExtractor

class UrlContentProcessor(
    private val webViewExtractor: WebViewExtractor
) : ContentProcessor<ShareContent.Url> {
    override suspend fun process(content: ShareContent.Url): ProcessedContent {
        val result = webViewExtractor.extract(content.url)
        val url = content.url
        val body = result.bodyText ?: url
        val rawMetadata = mapOf(
            HtmlMetaKey.OG_TITLE to result.ogTitle,
            HtmlMetaKey.OG_DESCRIPTION to result.ogDescription,
            HtmlMetaKey.URL to url,
            HtmlMetaKey.PUBLISHED_DATE to result.publishedTime,
            HtmlMetaKey.MODIFIED_DATE to result.modifiedTime,
            HtmlMetaKey.AUTHOR to result.author,
        )
        return ProcessedContent(
            body = body,
            title = content.title,
            contentType = ContentKind.URL,
            metadata = rawMetadata.filter { it.value.isNotBlank() },
            sourceUrl = url,
        )
    }
}
