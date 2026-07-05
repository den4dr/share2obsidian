package com.den4dr.share2Obsidian

import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.data.datastore.NoteSettings
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateApplicatorTest {

    // TC-031-01: buildConfig は DataStore 設定（NoteSettings）から vault/folder を取得する
    @Test
    fun buildConfig_usesNoteSettings() {
        val config = TemplateApplicator.buildConfig(NoteSettings(vault = "MyVault", folder = "Notes"))
        assertEquals("MyVault", config.vault)
        assertEquals("Notes", config.folder)
        assertEquals(AppConfig.OBSIDIAN_TAGS, config.defaultTags)
    }

    // EDGE-003: 未設定（空文字列）の NoteSettings では vault/folder が空
    @Test
    fun buildConfig_emptySettings_returnsEmptyVaultAndFolder() {
        val config = TemplateApplicator.buildConfig(NoteSettings())
        assertEquals("", config.vault)
        assertEquals("", config.folder)
    }

    // TC-011-01: body に {{content}} が1つある場合、共有コンテンツで置換される
    @Test
    fun buildBody_singlePlaceholder_replacesWithSharedContent() {
        val template = Template(id = 1L, name = "t", body = "## 記事\n{{content}}\n\n## メモ\n", fields = emptyList())
        val result = TemplateApplicator.buildBody(template, "テスト本文")
        assertEquals("## 記事\nテスト本文\n\n## メモ\n", result)
    }

    // TC-011-02: body が空文字列の場合、共有コンテンツをそのまま使用する
    @Test
    fun buildBody_emptyBody_returnsSharedContent() {
        val template = Template(id = 1L, name = "t", body = "", fields = emptyList())
        assertEquals("テスト本文", TemplateApplicator.buildBody(template, "テスト本文"))
    }

    // TC-011-03: テンプレートが null の場合、共有コンテンツをそのまま使用する
    @Test
    fun buildBody_nullTemplate_returnsSharedContent() {
        assertEquals("テスト本文", TemplateApplicator.buildBody(null, "テスト本文"))
    }

    // TC-011-E01: body に {{content}} が複数ある場合、すべて置換される
    @Test
    fun buildBody_multiplePlaceholders_replacesAll() {
        val template = Template(id = 1L, name = "t", body = "{{content}}\n---\n{{content}}", fields = emptyList())
        assertEquals("テスト\n---\nテスト", TemplateApplicator.buildBody(template, "テスト"))
    }

    // TC-011-E02: body に {{content}} がなくかつ非空の場合、body のみが使用される
    @Test
    fun buildBody_noPlaceholderNonEmpty_returnsTemplateBodyOnly() {
        val template = Template(id = 1L, name = "t", body = "固定テキスト", fields = emptyList())
        assertEquals("固定テキスト", TemplateApplicator.buildBody(template, "テスト本文"))
    }

    // TC-3: HTML_META フィールドが ProcessedContent.metadata から値を取得
    @Test
    fun buildCustomFields_htmlMeta_getsFromMetadata() {
        val template = Template(
            id = 1L, name = "t", isDefault = true,
            fields = listOf(
                TemplateField(
                    key = "title",
                    valueSource = FieldValueSource.HTML_META,
                    valueType = FieldValueType.STRING,
                    metaKey = HtmlMetaKey.OG_TITLE,
                )
            )
        )
        val processed = ProcessedContent(
            body = "body",
            contentType = ContentKind.URL,
            metadata = mapOf(HtmlMetaKey.OG_TITLE to "記事タイトル"),
        )
        val customFields = TemplateApplicator.buildCustomFields(template, processed)
        assertEquals(1, customFields.size)
        assertEquals("記事タイトル", customFields[0].value)
        assertEquals("title", customFields[0].key)
    }

    // TC-4: URL フィールドが sourceUrl から値を取得
    @Test
    fun buildCustomFields_url_getsFromSourceUrl() {
        val template = Template(
            id = 1L, name = "t", isDefault = true,
            fields = listOf(
                TemplateField(
                    key = "source",
                    valueSource = FieldValueSource.URL,
                    valueType = FieldValueType.STRING,
                )
            )
        )
        val processed = ProcessedContent(
            body = "body",
            contentType = ContentKind.URL,
            sourceUrl = "https://example.com",
        )
        val customFields = TemplateApplicator.buildCustomFields(template, processed)
        assertEquals("https://example.com", customFields[0].value)
    }

    // TC-5: テンプレートが null の場合は空リスト
    @Test
    fun buildCustomFields_withNull_returnsEmpty() {
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)
        val customFields = TemplateApplicator.buildCustomFields(null, processed)
        assertTrue(customFields.isEmpty())
    }
}
