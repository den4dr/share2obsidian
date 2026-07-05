package com.den4dr.share2Obsidian.content

class TextContentProcessor : ContentProcessor<ShareContent.Text> {
    override suspend fun process(content: ShareContent.Text): ProcessedContent {
        return ProcessedContent(
            body = content.text,
            title = content.title,
            contentType = ContentKind.TEXT
        )
    }
}
