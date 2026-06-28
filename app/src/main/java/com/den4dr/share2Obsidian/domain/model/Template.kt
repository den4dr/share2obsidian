package com.den4dr.share2Obsidian.domain.model

data class Template(
    val id: Long = 0,
    val name: String,
    val body: String = "",
    val fields: List<TemplateField>,
    val isDefault: Boolean = false,
)
