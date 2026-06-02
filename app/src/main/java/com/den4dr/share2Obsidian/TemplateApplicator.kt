package com.den4dr.share2Obsidian

import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.format.NoteConfig

object TemplateApplicator {

    fun buildConfig(template: Template?): NoteConfig = if (template != null) {
        NoteConfig(
            vault = template.vault,
            folder = template.folder,
            defaultTags = AppConfig.OBSIDIAN_TAGS,
        )
    } else {
        NoteConfig.fromAppConfig()
    }

    fun buildCustomFields(
        template: Template?,
        processed: ProcessedContent,
    ): List<CustomFieldState> = template?.fields?.map { field ->
        val value = when (field.valueSource) {
            FieldValueSource.FIXED -> field.defaultValue
            FieldValueSource.HTML_META -> processed.metadata[field.metaKey] ?: ""
            FieldValueSource.URL -> processed.sourceUrl ?: ""
            FieldValueSource.EMPTY -> ""
        }
        CustomFieldState(field.key, value, field.valueType)
    } ?: emptyList()
}
