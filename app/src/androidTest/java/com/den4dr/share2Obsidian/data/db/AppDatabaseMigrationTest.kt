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

    // ==========================================================================================
    // TASK-0057 (database-migration-v3) Red フェーズ: 以下は未実装の AppDatabase.MIGRATION_2_3 を
    // 参照するため、AppDatabase の version=3化・MIGRATION_2_3 実装が完了するまでコンパイルエラーと
    // なる想定のテストである。
    // ==========================================================================================

    // TC-N04: MIGRATION_2_3 で v2 実データが保持され新規カラムが '' で追加される
    @Test
    fun migrate2To3_preservesDataAndAddsLlmColumns() {
        // 【テスト目的】: MIGRATION_2_3 が既存データを保持しつつ bodyLlmPrompt/llmPrompt 列を '' で追加することを確認する
        // 【テスト内容】: v2 スキーマに templates/template_fields のレコードを投入し MIGRATION_2_3 を適用する
        // 【期待される動作】: 既存カラムが保持されたまま、新規カラムが空文字で追加される
        // 🔵 信頼性レベル: database-migration-v3-testcases.md TC-N04・要件定義書 TC4 に基づく

        // 【テストデータ準備】: v2 の templates/template_fields に既存レコードを投入する
        // 【初期条件設定】: v2 スキーマで DB を作成する
        helper.createDatabase(testDb, 2).apply {
            execSQL("INSERT INTO templates (id, name, body, isDefault) VALUES (1, 'テンプレA', '## 記事', 1)")
            execSQL(
                "INSERT INTO template_fields (id, templateId, key, valueSource, valueType, defaultValue, metaKey, sortOrder) " +
                    "VALUES (10, 1, 'title', 'HTML_META', 'STRING', '', 'OG_TITLE', 0)"
            )
            close()
        }

        // 【実際の処理実行】: MIGRATION_2_3 を適用しスキーマ検証する
        // 【処理内容】: v2 → v3 のマイグレーションを実行する
        val db = helper.runMigrationsAndValidate(testDb, 3, true, AppDatabase.MIGRATION_2_3)

        // 【結果検証】: 既存カラム保持 + 新規カラム '' を確認する
        db.query("SELECT name, body, isDefault, bodyLlmPrompt FROM templates WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("テンプレA", c.getString(c.getColumnIndexOrThrow("name"))) // 【確認内容】: 既存カラム name が保持されること
            assertEquals("## 記事", c.getString(c.getColumnIndexOrThrow("body"))) // 【確認内容】: 既存カラム body が保持されること
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("isDefault"))) // 【確認内容】: 既存カラム isDefault が保持されること
            assertEquals("", c.getString(c.getColumnIndexOrThrow("bodyLlmPrompt"))) // 【確認内容】: 新規カラム bodyLlmPrompt が '' で追加されること
        }
        db.query("SELECT key, valueSource, metaKey, llmPrompt FROM template_fields WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("title", c.getString(c.getColumnIndexOrThrow("key"))) // 【確認内容】: 既存カラム key が保持されること
            assertEquals("HTML_META", c.getString(c.getColumnIndexOrThrow("valueSource"))) // 【確認内容】: 既存カラム valueSource が保持されること
            assertEquals("", c.getString(c.getColumnIndexOrThrow("llmPrompt"))) // 【確認内容】: 新規カラム llmPrompt が '' で追加されること
        }
    }

    // TC-E01: MIGRATION_2_3 未登録時の Room 例外
    @Test(expected = IllegalStateException::class)
    fun migrate2To3_withoutMigrationRegistered_throwsIllegalStateException() {
        // 【テスト目的】: MIGRATION_2_3 を渡さずに v2→v3 を要求した場合、Room がマイグレーション経路欠如の例外を投げることを確認する
        // 【テスト内容】: v2 スキーマの DB を作成し、マイグレーションを一切指定せず runMigrationsAndValidate(testDb, 3, true) を実行する
        // 【期待される動作】: IllegalStateException がスローされる（DatabaseModule への登録漏れを検出する回帰テスト）
        // 🟡 信頼性レベル: database-migration-v3-testcases.md TC-E01（要件定義書エッジケースからの妥当な推測、例外メッセージ文言はRoom実装依存のため型のみ検証）に基づく

        // 【テストデータ準備】: v2 スキーマで DB を作成する（マイグレーション未指定のシナリオ）
        // 【初期条件設定】: マイグレーション登録忘れを模した状態を再現する
        helper.createDatabase(testDb, 2).apply {
            close()
        }

        // 【実際の処理実行】: マイグレーションを指定せず v3 への移行を要求する
        // 【処理内容】: Room が移行経路を解決できず例外を投げることを期待する
        helper.runMigrationsAndValidate(testDb, 3, true)

        // 【結果検証】: IllegalStateException が発生することを @Test(expected=...) で確認する（本文には到達しない）
    }

    // TC-B02(統合部分): フィールドが空（0件）のテンプレートのマイグレーション
    @Test
    fun migrate2To3_withNoTemplateFieldsRows_addsLlmPromptColumnSuccessfully() {
        // 【テスト目的】: template_fields が0件の状態でも MIGRATION_2_3 が正常に完了し、llmPrompt 列が追加されることを確認する
        // 【テスト内容】: templates のみレコードを投入し template_fields は0件のまま MIGRATION_2_3 を適用する
        // 【期待される動作】: 0行に対する ADD COLUMN も例外なく成功する
        // 🟡 信頼性レベル: database-migration-v3-testcases.md TC-B02（要件定義書エッジケースからの妥当な推測）に基づく

        // 【テストデータ準備】: フィールドを1つも持たないシンプルなテンプレートを想定する
        // 【初期条件設定】: templates のみ投入し template_fields は空のまま v2 スキーマを作成する
        helper.createDatabase(testDb, 2).apply {
            execSQL("INSERT INTO templates (id, name, body, isDefault) VALUES (1, 'テンプレB', '', 0)")
            close()
        }

        // 【実際の処理実行】: MIGRATION_2_3 を適用する
        // 【処理内容】: template_fields が0行でも ADD COLUMN が成功することを確認する
        val db = helper.runMigrationsAndValidate(testDb, 3, true, AppDatabase.MIGRATION_2_3)

        // 【結果検証】: template_fields テーブルに llmPrompt 列が例外なく追加されていることを確認する
        db.query("PRAGMA table_info(template_fields)").use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == "llmPrompt") {
                    found = true
                }
            }
            assertTrue(found) // 【確認内容】: template_fields に llmPrompt 列が追加されていること（0行でも成功）
        }
    }

    // TC-B04: v1→v3 連続マイグレーション（MIGRATION_1_2 + MIGRATION_2_3）でのデータ保持
    @Test
    fun migrateFrom1To3_preservesDataAndInitializesAllNewColumns() {
        // 【テスト目的】: v1 から v3 まで MIGRATION_1_2 + MIGRATION_2_3 を連続適用した場合も、既存データが保持され新規カラムが正しく初期化されることを確認する
        // 【テスト内容】: v1 スキーマ（vault/folder あり）にレコードを投入し、2段階のマイグレーションを連続適用する
        // 【期待される動作】: body（v1→v2追加）と bodyLlmPrompt/llmPrompt（v2→v3追加）が正しく初期化され、既存データが保持される
        // 🟡 信頼性レベル: database-migration-v3-testcases.md TC-B04（既存migrate1To2パターン＋連続移行のRoom標準挙動からの妥当な推測）に基づく

        // 【テストデータ準備】: v1 時代からのユーザーデータ（vault/folder付き）を想定する
        // 【初期条件設定】: v1 スキーマで DB を作成しレコードを投入する
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                "INSERT INTO templates (id, name, vault, folder, isDefault) " +
                    "VALUES (1, 'テンプレA', 'my_vault', 'notes', 1)"
            )
            close()
        }

        // 【実際の処理実行】: MIGRATION_1_2 → MIGRATION_2_3 を連続適用する
        // 【処理内容】: v1 → v2 → v3 の2段階マイグレーションを実行する
        val db = helper.runMigrationsAndValidate(
            testDb, 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3
        )

        // 【結果検証】: 既存データ保持 + 全新規カラムの初期化を確認する
        db.query("SELECT name, isDefault, body, bodyLlmPrompt FROM templates WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("テンプレA", c.getString(c.getColumnIndexOrThrow("name"))) // 【確認内容】: 既存カラム name が2段階移行後も保持されること
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("isDefault"))) // 【確認内容】: 既存カラム isDefault が保持されること
            assertEquals("", c.getString(c.getColumnIndexOrThrow("body"))) // 【確認内容】: v1→v2で追加された body が '' で初期化されること
            assertEquals("", c.getString(c.getColumnIndexOrThrow("bodyLlmPrompt"))) // 【確認内容】: v2→v3で追加された bodyLlmPrompt が '' で初期化されること
        }
    }
}
