package com.den4dr.share2Obsidian.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_fields",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["templateId"])],
)
data class TemplateFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "templateId") val templateId: Long,
    val key: String,
    val valueSource: String,   // FieldValueSource.name()
    val valueType: String,     // FieldValueType.name()
    val defaultValue: String,
    val metaKey: String,       // HtmlMetaKey.name() または "" (HTML_META 以外)
    // 【フィールド単位のLLM生成用プロンプト】: valueSource == "LLM" の場合のみ使用する列（REQ-104）。
    // 空文字は「未設定」を表し、既存レコード・既存呼び出し元との後方互換のためデフォルト値 "" を付与する。
    // 🔵 信頼性レベル: TASK-0057 要件定義・database-schema.kt に基づく（推測なし）
    val llmPrompt: String = "",
    val sortOrder: Int,
)
