package com.den4dr.share2Obsidian.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AppDatabase の Migration(1 -> 2) を検証する計器テスト（TASK-0045 / REQ-003, REQ-004, NFR-001）。
 *
 * v1 スキーマ（vault/folder あり）で投入したデータが、マイグレーション後も
 * name/isDefault を保持し、body カラムが空文字列で追加されることを確認する。
 *
 * 実行: mise exec -- ./gradlew connectedAndroidTest（デバイス/エミュレータが必要）
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesDataAndAddsBody() {
        // v1 スキーマで DB を作成し、vault/folder 付きのレコードを投入する
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                "INSERT INTO templates (id, name, vault, folder, isDefault) " +
                    "VALUES (1, 'テンプレA', 'my_vault', 'notes', 1)"
            )
            close()
        }

        // Migration(1, 2) を適用しスキーマ検証する
        val db = helper.runMigrationsAndValidate(testDb, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT name, body, isDefault FROM templates WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("テンプレA", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("body")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isDefault")))
        }
    }
}
