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

    /**
     * 【機能概要】: DB から取得した TemplateWithFields（Entity）を、ドメイン層で扱う Template に変換する
     * 【設計方針】: フィールドの並びをドメインモデル（Template）の定義順に揃え、対応関係を追いやすくする
     * 【保守性】: 新規カラム追加時はこの1関数のみを見れば読込方向のマッピング漏れがないか確認できる
     * 🔵 信頼性レベル: TASK-0057 要件定義 2.3 マッピング表に基づく（推測なし）
     */
    private fun TemplateWithFields.toDomain(): Template = Template(
        id = template.id,
        name = template.name,
        body = template.body,
        // 【bodyLlmPrompt マッピング】: TemplateEntity.bodyLlmPrompt を Template.bodyLlmPrompt にそのまま反映する
        // 🔵 信頼性レベル: TASK-0057 要件定義 2.3・TC-N01/TC-B01 に基づく（推測なし）
        bodyLlmPrompt = template.bodyLlmPrompt,
        isDefault = template.isDefault,
        fields = fields.map { it.toDomain() },
    )

    /**
     * 【機能概要】: TemplateFieldEntity を、ドメイン層で扱う TemplateField に変換する
     * 【設計方針】: valueSource/metaKey は文字列から enum への解決を担う（未知値は例外を伝播させ、不正データを握りつぶさない）
     * 🔵 信頼性レベル: TASK-0057 要件定義 2.3 マッピング表に基づく（推測なし）
     */
    private fun TemplateFieldEntity.toDomain(): TemplateField = TemplateField(
        id = id,
        templateId = templateId,
        key = key,
        valueSource = FieldValueSource.valueOf(valueSource),
        valueType = FieldValueType.valueOf(valueType),
        defaultValue = defaultValue,
        metaKey = if (metaKey.isEmpty()) null else HtmlMetaKey.valueOf(metaKey),
        // 【llmPrompt マッピング】: TemplateFieldEntity.llmPrompt を TemplateField.llmPrompt にそのまま反映する
        // 🔵 信頼性レベル: TASK-0057 要件定義 2.3・TC-N02/TC-B01 に基づく（推測なし）
        llmPrompt = llmPrompt,
        sortOrder = sortOrder,
    )

    /**
     * 【機能概要】: ドメイン層の Template を、DB 保存用の TemplateEntity に変換する
     * 【設計方針】: toDomain() と対称な双方向マッピングとし、保存経路でのフィールド欠落を防ぐ
     * 🔵 信頼性レベル: TASK-0057 要件定義 2.3 マッピング表に基づく（推測なし）
     */
    private fun Template.toEntity(): TemplateEntity = TemplateEntity(
        id = id,
        name = name,
        body = body,
        // 【bodyLlmPrompt 書き込み】: 保存経路（Domain→Entity）でも bodyLlmPrompt を欠落させない
        // 🔵 信頼性レベル: TASK-0057 要件定義 2.3・TC-N03 に基づく（推測なし）
        bodyLlmPrompt = bodyLlmPrompt,
        isDefault = isDefault,
    )

    /**
     * 【機能概要】: ドメイン層の TemplateField を、DB 保存用の TemplateFieldEntity に変換する
     * 【設計方針】: toDomain() と対称な双方向マッピングとし、保存経路でのフィールド欠落を防ぐ
     * 🔵 信頼性レベル: TASK-0057 要件定義 2.3 マッピング表に基づく（推測なし）
     */
    private fun TemplateField.toEntity(templateId: Long): TemplateFieldEntity = TemplateFieldEntity(
        id = id,
        templateId = templateId,
        key = key,
        valueSource = valueSource.name,
        valueType = valueType.name,
        defaultValue = defaultValue,
        metaKey = metaKey?.name ?: "",
        // 【llmPrompt 書き込み】: 保存経路（Domain→Entity）でも llmPrompt を欠落させない
        // 🔵 信頼性レベル: TASK-0057 要件定義 2.3・TC-N03 に基づく（推測なし）
        llmPrompt = llmPrompt,
        sortOrder = sortOrder,
    )
}
