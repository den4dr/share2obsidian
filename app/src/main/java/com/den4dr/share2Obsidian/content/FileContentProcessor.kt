package com.den4dr.share2Obsidian.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class FileContentProcessor(
    private val context: Context
) : ContentProcessor<ShareContent.File> {
    override suspend fun process(content: ShareContent.File): ProcessedContent {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "Shared File", content.uri)
        clipboard.setPrimaryClip(clip)

        return ProcessedContent(
            body = "(クリップボードに画像をコピーしました。Obsidian 上で貼り付けてください。)",
            title = content.title,
            contentType = ContentKind.FILE
        )
    }
}
