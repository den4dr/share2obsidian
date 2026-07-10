package com.den4dr.share2Obsidian.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TemplateEntity::class, TemplateFieldEntity::class],
    version = 3,
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

        /**
         * 【機能概要】: Migration(2, 3): templates に bodyLlmPrompt、template_fields に llmPrompt を追加する
         * 【実装方針】: MIGRATION_1_2 と同様に ADD COLUMN のみで既存レコードを破壊しない形で実装する
         * 【テスト対応】: TC-N04（データ保持＋カラム追加）、TC-E01（未登録時の例外）、TC-B02(統合)（0行での成功）、
         *   TC-B04（MIGRATION_1_2 との連続適用）を通すための実装
         * 🔵 信頼性レベル: TASK-0057 要件定義 2.4・database-schema.kt に基づく（推測なし）
         *
         * 実行順序:
         *   1. templates.bodyLlmPrompt を追加（既存レコードは '' で初期化、REQ-101）
         *   2. template_fields.llmPrompt を追加（既存レコードは '' で初期化、REQ-104）
         *
         * ADD COLUMN のみのため DROP COLUMN 用の SQLite バージョン制約（3.35+）は対象外。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 【本文用LLMプロンプト列追加】: 既存 templates レコードを保持したまま列を追加する
                db.execSQL("ALTER TABLE templates ADD COLUMN bodyLlmPrompt TEXT NOT NULL DEFAULT ''")
                // 【フィールド用LLMプロンプト列追加】: 既存 template_fields レコードを保持したまま列を追加する
                db.execSQL("ALTER TABLE template_fields ADD COLUMN llmPrompt TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
