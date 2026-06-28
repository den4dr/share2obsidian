package com.den4dr.share2Obsidian.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateTest {

    @Test
    fun `Template defaults - id is 0 and isDefault is false`() {
        val template = Template(
            name = "Web記事",
            fields = emptyList(),
        )
        assertEquals(0L, template.id)
        assertEquals(false, template.isDefault)
    }

    @Test
    fun `Template body defaults to empty string`() {
        val template = Template(name = "test", fields = emptyList(), isDefault = false)
        assertEquals("", template.body)
    }

    @Test
    fun `TemplateField metaKey is null when valueSource is not HTML_META`() {
        val field = TemplateField(
            key = "status",
            valueSource = FieldValueSource.FIXED,
            valueType = FieldValueType.STRING,
            defaultValue = "draft",
        )
        assertNull(field.metaKey)
    }

    @Test
    fun `FieldValueSource contains all expected values`() {
        val names = FieldValueSource.entries.map { it.name }
        assert("FIXED" in names)
        assert("HTML_META" in names)
        assert("URL" in names)
        assert("EMPTY" in names)
        assertEquals(4, names.size)
    }

    @Test
    fun `FieldValueType contains STRING and LIST`() {
        val names = FieldValueType.entries.map { it.name }
        assert("STRING" in names)
        assert("LIST" in names)
        assertEquals(2, names.size)
    }

    @Test
    fun `HtmlMetaKey contains all expected keys`() {
        val names = HtmlMetaKey.entries.map { it.name }
        assert("OG_TITLE" in names)
        assert("OG_DESCRIPTION" in names)
        assert("URL" in names)
        assert("PUBLISHED_DATE" in names)
        assert("MODIFIED_DATE" in names)
        assert("AUTHOR" in names)
        assertEquals(6, names.size)
    }

    @Test
    fun `CustomFieldState holds key value and type`() {
        val state = CustomFieldState(
            key = "source",
            value = "https://example.com",
            valueType = FieldValueType.STRING,
        )
        assertEquals("source", state.key)
        assertEquals("https://example.com", state.value)
        assertEquals(FieldValueType.STRING, state.valueType)
    }

    @Test
    fun `Template copy with updated isDefault`() {
        val original = Template(name = "Test", fields = emptyList())
        val updated = original.copy(isDefault = true)
        assertEquals(true, updated.isDefault)
        assertEquals(original.name, updated.name)
    }
}
