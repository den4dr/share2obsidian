package com.den4dr.share2Obsidian.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TemplateEntity::class, TemplateFieldEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
}
