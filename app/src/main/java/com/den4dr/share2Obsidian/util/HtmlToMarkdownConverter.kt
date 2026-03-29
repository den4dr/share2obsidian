package com.den4dr.share2Obsidian.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object HtmlToMarkdownConverter {
    fun convert(html: String): String {
        val doc = Jsoup.parse(html)
        return convertNode(doc.body()).trim()
    }

    private fun convertNode(node: Node): String {
        return when (node) {
            is TextNode -> node.text()
            is Element -> convertElement(node)
            else -> ""
        }
    }

    private fun convertElement(element: Element): String {
        val childContent = element.childNodes().joinToString("") { convertNode(it) }
        return when (element.tagName()) {
            "h1" -> "# $childContent\n\n"
            "h2" -> "## $childContent\n\n"
            "h3" -> "### $childContent\n\n"
            "strong", "b" -> "**$childContent**"
            "em", "i" -> "*$childContent*"
            "a" -> "[$childContent](${element.attr("href")})"
            "li" -> "- $childContent\n"
            "ul", "ol" -> "$childContent\n"
            "p" -> "$childContent\n\n"
            "br" -> "\n"
            "body", "div", "span" -> childContent
            else -> childContent
        }
    }
}
