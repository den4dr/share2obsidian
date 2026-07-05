package com.den4dr.share2Obsidian.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Transaction
    @Query("SELECT * FROM templates ORDER BY isDefault DESC, id ASC")
    fun getAllTemplatesWithFields(): Flow<List<TemplateWithFields>>

    @Transaction
    @Query("SELECT * FROM templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplateWithFields(): TemplateWithFields?

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateWithFieldsById(id: Long): TemplateWithFields?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<TemplateFieldEntity>)

    @Delete
    suspend fun deleteTemplate(template: TemplateEntity)

    @Query("DELETE FROM template_fields WHERE templateId = :templateId")
    suspend fun deleteFieldsByTemplateId(templateId: Long)

    @Query("UPDATE templates SET isDefault = 0 WHERE id != :excludeId")
    suspend fun clearDefaultExcept(excludeId: Long)

    @Query("UPDATE templates SET isDefault = 0")
    suspend fun clearAllDefaults()
}
