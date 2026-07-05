package com.den4dr.share2Obsidian.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String = "",
    val isDefault: Boolean,
)
