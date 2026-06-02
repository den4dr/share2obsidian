package com.den4dr.share2Obsidian.integration

import com.den4dr.share2Obsidian.AppConfig
import com.den4dr.share2Obsidian.TemplateApplicator
import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import com.den4dr.share2Obsidian.format.NoteComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeCaseIntegrationTest {

    // EDGE-001: テンプレートなし → AppConfig フォールバック
    @Test
    fun edge001_noTemplate_usesAppConfigFallback() {
        val config = TemplateApplicator.buildConfig(null)
        assertEquals(AppConfig.OBSIDIAN_VAULT, config.vault)
        assertEquals(AppConfig.OBSIDIAN_FOLDER, config.folder)
    }

    // EDGE-003: WebView タイムアウト → metadata が emptyMap() → カスタムフィールドが空
    @Test
    fun edge003_webviewTimeout_emptyMetadata() {
        val template = Template(
            id = 1L, name = "t", vault = "v", folder = "f", isDefault = true,
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
            body = "url fallback",
            contentType = ContentKind.URL,
            metadata = emptyMap(),  // タイムアウトで空
        )
        val customFields = TemplateApplicator.buildCustomFields(template, processed)
        assertEquals(1, customFields.size)
        assertEquals("", customFields[0].value)  // フォールバックで空文字
    }

    // EDGE-004: HTML 共有時 → sourceUrl = null → URL フィールドが空
    @Test
    fun edge004_htmlShare_nullSourceUrl() {
        val template = Template(
            id = 1L, name = "t", vault = "v", folder = "f", isDefault = true,
            fields = listOf(
                TemplateField(
                    key = "source",
                    valueSource = FieldValueSource.URL,
                    valueType = FieldValueType.STRING,
                )
            )
        )
        val processed = ProcessedContent(
            body = "html body",
            contentType = ContentKind.HTML,
            sourceUrl = null,
        )
        val customFields = TemplateApplicator.buildCustomFields(template, processed)
        assertEquals("", customFields[0].value)
    }

    // EDGE-005: カスタムフィールドの key="tags" → 標準 tags が上書きされる
    @Test
    fun edge005_customTagsField_overridesStandardTags() {
        val customFields = listOf(
            CustomFieldState("tags", "custom-tag1, custom-tag2", FieldValueType.LIST)
        )
        val result = NoteComposer.buildFrontmatter("body", listOf("shared"), customFields)
        assertTrue(result.contains("tags: [custom-tag1, custom-tag2]"))
        assertFalse(result.contains("tags: [shared]"))
    }

    // 統合確認: テンプレートあり → config が正しく設定され → Frontmatter に反映
    @Test
    fun integration_templateApplied_frontmatterCorrect() {
        val template = Template(
            id = 1L, name = "Web記事", vault = "WebVault", folder = "Articles",
            isDefault = true,
            fields = listOf(
                TemplateField(
                    key = "source",
                    valueSource = FieldValueSource.URL,
                    valueType = FieldValueType.STRING,
                )
            )
        )
        val processed = ProcessedContent(
            body = "記事本文",
            contentType = ContentKind.URL,
            sourceUrl = "https://example.com",
        )
        val config = TemplateApplicator.buildConfig(template)
        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        assertEquals("WebVault", config.vault)
        assertEquals("Articles", config.folder)

        val frontmatter = NoteComposer.buildFrontmatter("記事本文", config.defaultTags, customFields)
        assertTrue(frontmatter.contains("source: https://example.com"))
        assertTrue(frontmatter.contains("tags: [shared]"))
    }
}
