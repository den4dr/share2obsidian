package com.den4dr.share2Obsidian.data.repository

import com.den4dr.share2Obsidian.data.db.TemplateDao
import com.den4dr.share2Obsidian.data.db.TemplateEntity
import com.den4dr.share2Obsidian.data.db.TemplateFieldEntity
import com.den4dr.share2Obsidian.data.db.TemplateWithFields
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemplateRepositoryImplTest {

    private lateinit var dao: TemplateDao
    private lateinit var repository: TemplateRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk()
        repository = TemplateRepositoryImpl(dao)
    }

    // TC-1: getAllTemplates が Flow を正しく変換
    @Test
    fun getAllTemplates_convertsTodomainModels() = runBlocking {
        val entity = TemplateEntity(id = 1L, name = "テスト", vault = "MyVault", folder = "notes", isDefault = false)
        val withFields = TemplateWithFields(template = entity, fields = emptyList())
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        val result = repository.getAllTemplates().first()

        assertEquals(1, result.size)
        assertEquals("テスト", result[0].name)
        assertEquals("MyVault", result[0].vault)
        assertEquals("notes", result[0].folder)
        assertFalse(result[0].isDefault)
    }

    // TC-2: saveTemplate で isDefault=true の場合 clearDefaultExcept が呼ばれる
    @Test
    fun saveTemplate_withIsDefault_callsClearDefaultExcept() = runBlocking {
        val template = Template(
            id = 1L,
            name = "テスト",
            vault = "v",
            folder = "f",
            isDefault = true,
            fields = emptyList(),
        )
        coEvery { dao.clearDefaultExcept(1L) } just Runs
        coEvery { dao.insertTemplate(any()) } returns 1L
        coEvery { dao.deleteFieldsByTemplateId(any()) } just Runs
        coEvery { dao.insertFields(any()) } just Runs

        repository.saveTemplate(template)

        coVerify { dao.clearDefaultExcept(1L) }
    }

    // TC-2b: saveTemplate で isDefault=false の場合 clearDefaultExcept が呼ばれない
    @Test
    fun saveTemplate_withoutIsDefault_doesNotCallClearDefaultExcept() = runBlocking {
        val template = Template(
            id = 0L,
            name = "テスト",
            vault = "v",
            folder = "f",
            isDefault = false,
            fields = emptyList(),
        )
        coEvery { dao.insertTemplate(any()) } returns 2L
        coEvery { dao.deleteFieldsByTemplateId(any()) } just Runs
        coEvery { dao.insertFields(any()) } just Runs

        repository.saveTemplate(template)

        coVerify(exactly = 0) { dao.clearDefaultExcept(any()) }
    }

    // TC-3: deleteTemplate で dao.deleteTemplate が呼ばれる
    @Test
    fun deleteTemplate_callsDaoDeleteTemplate() = runBlocking {
        val template = Template(
            id = 5L,
            name = "テスト",
            vault = "v",
            folder = "f",
            isDefault = false,
            fields = emptyList(),
        )
        coEvery { dao.deleteTemplate(any()) } just Runs

        repository.deleteTemplate(template)

        coVerify {
            dao.deleteTemplate(
                TemplateEntity(id = 5L, name = "テスト", vault = "v", folder = "f", isDefault = false)
            )
        }
    }

    // TC-4: TemplateWithFields.toDomain() マッピング確認
    @Test
    fun getAllTemplates_mapsAllFieldsCorrectly() = runBlocking {
        val templateEntity = TemplateEntity(id = 1L, name = "テスト", vault = "v", folder = "f", isDefault = true)
        val fieldEntity = TemplateFieldEntity(
            id = 10L,
            templateId = 1L,
            key = "source",
            valueSource = "HTML_META",
            valueType = "STRING",
            defaultValue = "",
            metaKey = "OG_TITLE",
            sortOrder = 0,
        )
        val withFields = TemplateWithFields(template = templateEntity, fields = listOf(fieldEntity))
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        val result = repository.getAllTemplates().first()

        assertEquals(1, result.size)
        val template = result[0]
        assertEquals(1L, template.id)
        assertEquals("テスト", template.name)
        assertEquals("v", template.vault)
        assertEquals("f", template.folder)
        assertTrue(template.isDefault)
        assertEquals(1, template.fields.size)

        val field = template.fields[0]
        assertEquals(10L, field.id)
        assertEquals(1L, field.templateId)
        assertEquals("source", field.key)
        assertEquals(FieldValueSource.HTML_META, field.valueSource)
        assertEquals(FieldValueType.STRING, field.valueType)
        assertEquals("", field.defaultValue)
        assertEquals(HtmlMetaKey.OG_TITLE, field.metaKey)
        assertEquals(0, field.sortOrder)
    }

    // TC-4b: metaKey が空文字の場合 null にマッピング
    @Test
    fun getAllTemplates_emptyMetaKey_mapsToNull() = runBlocking {
        val templateEntity = TemplateEntity(id = 1L, name = "t", vault = "v", folder = "f", isDefault = false)
        val fieldEntity = TemplateFieldEntity(
            id = 1L,
            templateId = 1L,
            key = "k",
            valueSource = "FIXED",
            valueType = "STRING",
            defaultValue = "val",
            metaKey = "",
            sortOrder = 0,
        )
        val withFields = TemplateWithFields(template = templateEntity, fields = listOf(fieldEntity))
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        val result = repository.getAllTemplates().first()

        assertNull(result[0].fields[0].metaKey)
    }
}
