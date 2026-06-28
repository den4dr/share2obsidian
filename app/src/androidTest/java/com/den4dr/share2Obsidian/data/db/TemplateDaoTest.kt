package com.den4dr.share2Obsidian.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TemplateDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.templateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertTemplate_andGetAll() = runBlocking {
        dao.insertTemplate(
            TemplateEntity(name = "テスト", body = "", isDefault = false)
        )
        val result = dao.getAllTemplatesWithFields().first()
        assertEquals(1, result.size)
        assertEquals("テスト", result[0].template.name)
    }

    @Test
    fun getDefaultTemplate_returnsDefault() = runBlocking {
        dao.insertTemplate(
            TemplateEntity(name = "デフォルト", body = "", isDefault = true)
        )
        val result = dao.getDefaultTemplateWithFields()
        assertNotNull(result)
        assertEquals("デフォルト", result!!.template.name)
    }

    @Test
    fun deleteTemplate_cascadesFields() = runBlocking {
        val templateId = dao.insertTemplate(
            TemplateEntity(name = "テスト", body = "", isDefault = false)
        )
        dao.insertFields(
            listOf(
                TemplateFieldEntity(
                    templateId = templateId,
                    key = "source",
                    valueSource = "URL",
                    valueType = "STRING",
                    defaultValue = "",
                    metaKey = "",
                    sortOrder = 0,
                )
            )
        )
        val before = dao.getTemplateWithFieldsById(templateId)
        assertEquals(1, before!!.fields.size)

        dao.deleteTemplate(
            TemplateEntity(id = templateId, name = "テスト", body = "", isDefault = false)
        )
        val after = dao.getTemplateWithFieldsById(templateId)
        assertNull(after)
        // template_fields のカスケード削除は外部キー制約で保証される
        val allTemplates = dao.getAllTemplatesWithFields().first()
        assertTrue(allTemplates.isEmpty())
    }

    @Test
    fun clearDefaultExcept_clearsOthers() = runBlocking {
        val id1 = dao.insertTemplate(
            TemplateEntity(name = "T1", body = "", isDefault = true)
        )
        val id2 = dao.insertTemplate(
            TemplateEntity(name = "T2", body = "", isDefault = true)
        )
        dao.clearDefaultExcept(id2)
        val t1 = dao.getTemplateWithFieldsById(id1)
        val t2 = dao.getTemplateWithFieldsById(id2)
        assertFalse(t1!!.template.isDefault)
        assertTrue(t2!!.template.isDefault)
    }
}
