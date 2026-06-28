package com.den4dr.share2Obsidian.format

import android.net.Uri
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueType

object NoteComposer {

    fun buildFrontmatter(
        body: String,
        tags: List<String>,
        customFields: List<CustomFieldState> = emptyList(),
    ): String {
        val sb = StringBuilder("---\n")
        val customKeySet = customFields.map { it.key }.toSet()

        for (field in customFields) {
            val valueStr = if (field.valueType == FieldValueType.LIST) {
                "[${field.value}]"
            } else {
                field.value
            }
            sb.append("${field.key}: $valueStr\n")
        }

        if ("tags" !in customKeySet) {
            val tagsString = tags.joinToString(", ")
            sb.append("tags: [$tagsString]\n")
        }

        sb.append("---\n\n$body")
        return sb.toString()
    }

    fun buildUri(content: String, title: String?, config: NoteConfig): Uri {
        val builder = Uri.parse("obsidian://new").buildUpon()
            .appendQueryParameter("content", content)
        if (config.vault.isNotBlank()) {
            builder.appendQueryParameter("vault", config.vault)
        }
        if (config.folder.isNotBlank()) {
            builder.appendQueryParameter("folder", config.folder)
        }
        if (!title.isNullOrBlank()) {
            builder.appendQueryParameter("name", title)
        }
        return builder.build()
    }
}
