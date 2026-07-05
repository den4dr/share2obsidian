package com.den4dr.share2Obsidian.data.repository

import com.den4dr.share2Obsidian.domain.model.Template
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun getAllTemplates(): Flow<List<Template>>
    suspend fun getDefaultTemplate(): Template?
    suspend fun getTemplateById(id: Long): Template?
    suspend fun saveTemplate(template: Template): Long
    suspend fun deleteTemplate(template: Template)
}
