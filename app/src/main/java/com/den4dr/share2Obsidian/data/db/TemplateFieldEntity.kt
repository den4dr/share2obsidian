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
    val sortOrder: Int,
)
