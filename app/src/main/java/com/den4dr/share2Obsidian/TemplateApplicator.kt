package com.den4dr.share2Obsidian

import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.data.datastore.NoteSettings
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.format.NoteConfig

object TemplateApplicator {

    /**
     * DataStore 由来の [NoteSettings] から [NoteConfig] を構築する。
     * テンプレートからは vault/folder を取得しない（REQ-031）。
     */
    fun buildConfig(settings: NoteSettings): NoteConfig = NoteConfig(
        vault = settings.vault,
        folder = settings.folder,
        defaultTags = AppConfig.OBSIDIAN_TAGS,
    )

    /**
     * テンプレートの本文（body）に含まれる `{{content}}` プレースホルダーを共有コンテンツで解決する。
     *
     * - body が空または null の場合: 共有コンテンツをそのまま返す（REQ-013, REQ-014）
     * - `{{content}}` を含む場合: すべて共有コンテンツで置換する（REQ-012, EDGE-001）
     * - `{{content}}` を含まない非空 body の場合: body のみを返す（EDGE-002）
     */
    fun buildBody(template: Template?, sharedBody: String): String {
        val templateBody = template?.body ?: ""
        return if (templateBody.isEmpty()) sharedBody
        else templateBody.replace("{{content}}", sharedBody)
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
