package com.den4dr.share2Obsidian.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TemplateEntity::class, TemplateFieldEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao

    companion object {
        /**
         * Migration(1, 2): templates テーブルから vault/folder を削除し body を追加する。
         *
         * 実行順序:
         *   1. body カラムを追加（既存レコードは body = '' で初期化、REQ-004: 既存データ保護）
         *   2. vault カラムを削除（DataStore へ移行）
         *   3. folder カラムを削除（DataStore へ移行）
         *
         * SQLite DROP COLUMN は SQLite 3.35+ でサポート。minSdk 33 (Android 13 = SQLite 3.39+) のため使用可能。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN body TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE templates DROP COLUMN vault")
                db.execSQL("ALTER TABLE templates DROP COLUMN folder")
            }
        }
    }
}
