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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TemplateRepositoryImpl @Inject constructor(
    private val dao: TemplateDao,
) : TemplateRepository {

    override fun getAllTemplates(): Flow<List<Template>> =
        dao.getAllTemplatesWithFields().map { list -> list.map { it.toDomain() } }

    override suspend fun getDefaultTemplate(): Template? =
        dao.getDefaultTemplateWithFields()?.toDomain()

    override suspend fun getTemplateById(id: Long): Template? =
        dao.getTemplateWithFieldsById(id)?.toDomain()

    override suspend fun saveTemplate(template: Template): Long {
        if (template.isDefault) {
            dao.clearDefaultExcept(template.id)
        }
        val newId = dao.insertTemplate(template.toEntity())
        dao.deleteFieldsByTemplateId(newId)
        dao.insertFields(template.fields.map { it.toEntity(templateId = newId) })
        return newId
    }

    override suspend fun deleteTemplate(template: Template) {
        dao.deleteTemplate(template.toEntity())
    }

    private fun TemplateWithFields.toDomain(): Template = Template(
        id = template.id,
        name = template.name,
        vault = template.vault,
        folder = template.folder,
        isDefault = template.isDefault,
        fields = fields.map { it.toDomain() },
    )

    private fun TemplateFieldEntity.toDomain(): TemplateField = TemplateField(
        id = id,
        templateId = templateId,
        key = key,
        valueSource = FieldValueSource.valueOf(valueSource),
        valueType = FieldValueType.valueOf(valueType),
        defaultValue = defaultValue,
        metaKey = if (metaKey.isEmpty()) null else HtmlMetaKey.valueOf(metaKey),
        sortOrder = sortOrder,
    )

    private fun Template.toEntity(): TemplateEntity = TemplateEntity(
        id = id,
        name = name,
        vault = vault,
        folder = folder,
        isDefault = isDefault,
    )

    private fun TemplateField.toEntity(templateId: Long): TemplateFieldEntity = TemplateFieldEntity(
        id = id,
        templateId = templateId,
        key = key,
        valueSource = valueSource.name,
        valueType = valueType.name,
        defaultValue = defaultValue,
        metaKey = metaKey?.name ?: "",
        sortOrder = sortOrder,
    )
}
