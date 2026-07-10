/**
 * LLMによるメモ更改機能の追加 Room DB スキーマ設計（version 3）
 *
 * 作成日: 2026-07-05
 * 関連設計: architecture.md / dataflow.md
 * 言語: Kotlin 2.2+
 *
 * 変更概要:
 *   - TemplateEntity: bodyLlmPrompt 追加
 *   - TemplateFieldEntity: llmPrompt 追加
 *   - AppDatabase: version 2 → 3、MIGRATION_2_3 追加
 *   - TemplateRepositoryImpl: toDomain() / toEntity() マッピング修正
 *   - TemplateDao: 変更なし
 *
 * 信頼性レベル:
 * - 🔵 青信号: 要件定義書・設計文書・ユーザヒアリング・既存実装を参考にした確実な定義
 * - 🟡 黄信号: 要件定義書・設計文書・ユーザヒアリングから妥当な推測による定義
 * - 🔴 赤信号: 要件定義書・設計文書・ユーザヒアリングにない推測による定義
 */

package com.den4dr.share2Obsidian.data.db

// ========================================
// TemplateEntity（変更後）
// ========================================

/**
 * TemplateEntity（変更後）
 *
 * 変更点:
 *   - bodyLlmPrompt: String を追加 🔵 REQ-101
 *
 * 対応する Room テーブル DDL (version 3):
 *   CREATE TABLE templates (
 *       id            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 *       name          TEXT    NOT NULL,
 *       body          TEXT    NOT NULL DEFAULT '',
 *       bodyLlmPrompt TEXT    NOT NULL DEFAULT '',
 *       isDefault     INTEGER NOT NULL
 *   )
 *
 * 🔵 信頼性: REQ-101・ヒアリング「本文用LLMプロンプトをテンプレートに保存」より
 */
// @Entity(tableName = "templates")
// data class TemplateEntity(
//     @PrimaryKey(autoGenerate = true) val id: Long = 0,   // 🔵 既存
//     val name: String,                                     // 🔵 既存
//     val body: String = "",                               // 🔵 既存
//     val bodyLlmPrompt: String = "",                       // 🔵 REQ-101 新規追加
//     val isDefault: Boolean,                               // 🔵 既存
// )

// ========================================
// TemplateFieldEntity（変更後）
// ========================================

/**
 * TemplateFieldEntity（変更後）
 *
 * 変更点:
 *   - llmPrompt: String を追加（valueSource == "LLM" の場合のみ使用） 🔵 REQ-104
 *
 * 対応する Room テーブル DDL (version 3):
 *   CREATE TABLE template_fields (
 *       id         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 *       templateId INTEGER NOT NULL,
 *       key        TEXT    NOT NULL,
 *       valueSource TEXT   NOT NULL,   -- "FIXED" | "HTML_META" | "URL" | "EMPTY" | "LLM"
 *       valueType  TEXT    NOT NULL,
 *       defaultValue TEXT  NOT NULL,
 *       metaKey    TEXT    NOT NULL,
 *       llmPrompt  TEXT    NOT NULL DEFAULT '',
 *       sortOrder  INTEGER NOT NULL,
 *       FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE
 *   )
 *
 * 🔵 信頼性: REQ-104・REQ-303（FieldValueSourceにLLM追加）より
 * 備考: valueSource は既存実装同様に enum の name() を TEXT として保存するため、
 *       "LLM" という新しい文字列値が追加されるのみでカラム定義自体の変更は不要 🔵
 */
// @Entity(
//     tableName = "template_fields",
//     foreignKeys = [ForeignKey(
//         entity = TemplateEntity::class,
//         parentColumns = ["id"],
//         childColumns = ["templateId"],
//         onDelete = ForeignKey.CASCADE,
//     )],
//     indices = [Index(value = ["templateId"])],
// )
// data class TemplateFieldEntity(
//     @PrimaryKey(autoGenerate = true) val id: Long = 0,
//     @ColumnInfo(name = "templateId") val templateId: Long,
//     val key: String,
//     val valueSource: String,   // FieldValueSource.name()。"LLM" が新規追加値 🔵 REQ-303
//     val valueType: String,
//     val defaultValue: String,
//     val metaKey: String,
//     val llmPrompt: String = "",  // 🔵 REQ-104 新規追加
//     val sortOrder: Int,
// )

// ========================================
// AppDatabase（変更後）
// ========================================

/**
 * AppDatabase（変更後）
 *
 * 変更点:
 *   - version: 2 → 3 🔵 REQ-101, REQ-104
 *   - MIGRATION_2_3 を追加 🔵
 *   - fallbackToDestructiveMigration() は使用しない（既存パターン継続） 🔵
 *
 * 🔵 信頼性: REQ-101, REQ-104・既存 AppDatabase(version=2) 実装より
 */
// @Database(
//     entities = [TemplateEntity::class, TemplateFieldEntity::class],
//     version = 3,             // 🔵 version 2 → 3 に変更
//     exportSchema = true,     // 🔵 既存パターン継続
// )
// abstract class AppDatabase : RoomDatabase() {
//     abstract fun templateDao(): TemplateDao
//
//     companion object {
//         // 既存の MIGRATION_1_2 は変更なしでそのまま維持する 🔵
//
//         /**
//          * Migration(2, 3)
//          * 🔵 信頼性: REQ-101, REQ-104・Room ALTER TABLE ADD COLUMN 公式パターンより
//          *
//          * 実行内容:
//          *   1. templates テーブルに bodyLlmPrompt カラムを追加（既存レコードは '' で初期化）
//          *   2. template_fields テーブルに llmPrompt カラムを追加（既存レコードは '' で初期化）
//          *
//          * 本マイグレーションは ADD COLUMN のみで DROP COLUMN を伴わないため、
//          * MIGRATION_1_2 で確認した SQLite バージョン制約（3.35+ 必須）の対象外 🔵
//          */
//         val MIGRATION_2_3 = object : Migration(2, 3) {
//             override fun migrate(db: SupportSQLiteDatabase) {
//                 db.execSQL("ALTER TABLE templates ADD COLUMN bodyLlmPrompt TEXT NOT NULL DEFAULT ''")
//                 db.execSQL("ALTER TABLE template_fields ADD COLUMN llmPrompt TEXT NOT NULL DEFAULT ''")
//             }
//         }
//
//         @Volatile
//         private var INSTANCE: AppDatabase? = null
//
//         fun getDatabase(context: Context): AppDatabase {
//             return INSTANCE ?: synchronized(this) {
//                 Room.databaseBuilder(
//                     context.applicationContext,
//                     AppDatabase::class.java,
//                     "share2obsidian_database",
//                 )
//                 .addMigrations(MIGRATION_1_2, MIGRATION_2_3)    // 🔵 両マイグレーションを登録
//                 .build()
//                 .also { INSTANCE = it }
//             }
//         }
//     }
// }

// ========================================
// TemplateDao（変更なし）
// ========================================

/**
 * TemplateDao（変更なし）
 * 🔵 信頼性: 既存実装・変更対象外
 *
 * bodyLlmPrompt / llmPrompt カラムの追加はエンティティ側で吸収されるため DAO の変更は不要。
 */
// （既存の TemplateDao をそのまま維持。CRUD クエリ本体の変更なし）

// ========================================
// TemplateRepositoryImpl（変更後マッピング）
// ========================================

/**
 * TemplateRepositoryImpl のマッピング関数（変更後）
 *
 * 変更点:
 *   - toDomain(): bodyLlmPrompt / llmPrompt を追加でマッピング 🔵 REQ-101, REQ-104
 *   - toEntity(): 同上 🔵
 *
 * 🔵 信頼性: REQ-101, REQ-104・既存 TemplateRepositoryImpl 実装より
 */
// private fun TemplateWithFields.toDomain(): Template = Template(
//     id = template.id,
//     name = template.name,
//     body = template.body,
//     bodyLlmPrompt = template.bodyLlmPrompt,   // 🔵 REQ-101 新規
//     isDefault = template.isDefault,
//     fields = fields.map { fieldEntity ->
//         TemplateField(
//             id = fieldEntity.id,
//             templateId = fieldEntity.templateId,
//             key = fieldEntity.key,
//             valueSource = FieldValueSource.valueOf(fieldEntity.valueSource),
//             valueType = FieldValueType.valueOf(fieldEntity.valueType),
//             defaultValue = fieldEntity.defaultValue,
//             metaKey = fieldEntity.metaKey.takeIf { it.isNotEmpty() }?.let { HtmlMetaKey.valueOf(it) },
//             llmPrompt = fieldEntity.llmPrompt,     // 🔵 REQ-104 新規
//             sortOrder = fieldEntity.sortOrder,
//         )
//     },
// )
//
// private fun Template.toEntity(): TemplateEntity = TemplateEntity(
//     id = id,
//     name = name,
//     body = body,
//     bodyLlmPrompt = bodyLlmPrompt,   // 🔵 REQ-101 新規
//     isDefault = isDefault,
// )

// ========================================
// スキーマ変更サマリー（Diff）
// ========================================

/**
 * Version 2 → Version 3 スキーマ Diff
 *
 * [templates テーブル]
 *
 * Version 2:
 *   id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
 *   name      TEXT    NOT NULL
 *   body      TEXT    NOT NULL DEFAULT ''
 *   isDefault INTEGER NOT NULL
 *
 * Version 3:
 *   id            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
 *   name          TEXT    NOT NULL
 *   body          TEXT    NOT NULL DEFAULT ''
 *   bodyLlmPrompt TEXT    NOT NULL DEFAULT ''  ← 追加 🔵 REQ-101
 *   isDefault     INTEGER NOT NULL
 *
 * [template_fields テーブル]
 *
 * Version 2:
 *   id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
 *   templateId   INTEGER NOT NULL
 *   key          TEXT    NOT NULL
 *   valueSource  TEXT    NOT NULL
 *   valueType    TEXT    NOT NULL
 *   defaultValue TEXT    NOT NULL
 *   metaKey      TEXT    NOT NULL
 *   sortOrder    INTEGER NOT NULL
 *
 * Version 3:
 *   id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
 *   templateId   INTEGER NOT NULL
 *   key          TEXT    NOT NULL
 *   valueSource  TEXT    NOT NULL   -- "LLM" が新規追加値として使用可能に 🔵 REQ-303
 *   valueType    TEXT    NOT NULL
 *   defaultValue TEXT    NOT NULL
 *   metaKey      TEXT    NOT NULL
 *   llmPrompt    TEXT    NOT NULL DEFAULT ''  ← 追加 🔵 REQ-104
 *   sortOrder    INTEGER NOT NULL
 *
 * Migration SQL:
 *   ALTER TABLE templates ADD COLUMN bodyLlmPrompt TEXT NOT NULL DEFAULT ''
 *   ALTER TABLE template_fields ADD COLUMN llmPrompt TEXT NOT NULL DEFAULT ''
 *
 * 注意: 本マイグレーションは ADD COLUMN のみ。DROP COLUMN を伴わないため
 *       SQLiteバージョン制約（DROP COLUMNはSQLite 3.35+）の対象外で、
 *       ADD COLUMNは古いSQLiteバージョンでも広くサポートされる 🔵
 */

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 33件 (100%)
 * - 🟡 黄信号: 0件 (0%)
 * - 🔴 赤信号: 0件 (0%)
 *
 * 品質評価: ✅ 高品質
 */
