package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.util.HtmlToMarkdownConverter

class HtmlContentProcessor : ContentProcessor<ShareContent.Html> {
    override suspend fun process(content: ShareContent.Html): ProcessedContent {
        val body = if (content.html != null) {
            try {
                HtmlToMarkdownConverter.convert(content.html)
            } catch (e: Exception) {
                content.fallbackText
            }
        } else {
            content.fallbackText
        }
        return ProcessedContent(
            body = body,
            title = content.title,
            contentType = ContentKind.HTML
        )
    }
}
