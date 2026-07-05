package com.den4dr.share2Obsidian.content

import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.util.HtmlToMarkdownConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class HtmlContentProcessor : ContentProcessor<ShareContent.Html> {
    override suspend fun process(content: ShareContent.Html): ProcessedContent {
        val html = content.html
        val body = if (html != null) {
            try {
                HtmlToMarkdownConverter.convert(html)
            } catch (e: Exception) {
                content.fallbackText
            }
        } else {
            content.fallbackText
        }
        val metadata = if (html != null) {
            try {
                extractMetadata(Jsoup.parse(html), sourceUrl = null)
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
        return ProcessedContent(
            body = body,
            title = content.title,
            contentType = ContentKind.HTML,
            metadata = metadata,
            sourceUrl = null,
        )
    }

    private fun extractMetadata(doc: Document, sourceUrl: String?): Map<HtmlMetaKey, String> {
        fun meta(vararg selectors: String): String {
            for (sel in selectors) {
                val v = doc.select(sel).attr("content").trim()
                if (v.isNotEmpty()) return v
            }
            return ""
        }
        return mapOf(
            HtmlMetaKey.OG_TITLE to (meta("meta[property=og:title]").ifEmpty { doc.title() }),
            HtmlMetaKey.OG_DESCRIPTION to meta("meta[property=og:description]", "meta[name=description]"),
            HtmlMetaKey.URL to (sourceUrl ?: ""),
            HtmlMetaKey.PUBLISHED_DATE to meta("meta[property=article:published_time]", "meta[itemprop=datePublished]"),
            HtmlMetaKey.MODIFIED_DATE to meta("meta[property=article:modified_time]", "meta[itemprop=dateModified]"),
            HtmlMetaKey.AUTHOR to meta("meta[name=author]", "meta[property=article:author]", "meta[property=og:site_name]"),
        ).filter { it.value.isNotBlank() }
    }
}
