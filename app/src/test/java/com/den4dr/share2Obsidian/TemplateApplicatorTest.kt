package com.den4dr.share2Obsidian

import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateApplicatorTest {

    // TC-1: デフォルトテンプレートがある場合、vault/folder が上書きされる
    @Test
    fun buildConfig_withTemplate_overridesVaultAndFolder() {
        val template = Template(
            id = 1L, name = "t", vault = "myVault", folder = "Clippings",
            isDefault = true, fields = emptyList()
        )
        val config = TemplateApplicator.buildConfig(template)
        assertEquals("myVault", config.vault)
        assertEquals("Clippings", config.folder)
    }

    // TC-2: テンプレートが null の場合、AppConfig フォールバック
    @Test
    fun buildConfig_withNull_fallsBackToAppConfig() {
        val config = TemplateApplicator.buildConfig(null)
        assertEquals(AppConfig.OBSIDIAN_VAULT, config.vault)
        assertEquals(AppConfig.OBSIDIAN_FOLDER, config.folder)
    }

    // TC-3: HTML_META フィールドが ProcessedContent.metadata から値を取得
    @Test
    fun buildCustomFields_htmlMeta_getsFromMetadata() {
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
