package com.den4dr.share2Obsidian.domain.model

data class TemplateField(
    val id: Long = 0,
    val templateId: Long = 0,
    val key: String,
    val valueSource: FieldValueSource,
    val valueType: FieldValueType,
    val defaultValue: String = "",
    val metaKey: HtmlMetaKey? = null,
    val sortOrder: Int = 0,
)
