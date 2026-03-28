package com.den4dr.share2Obsidian.content

interface ContentProcessor<T : ShareContent> {
    suspend fun process(content: T): ProcessedContent
}
