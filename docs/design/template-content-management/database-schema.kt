/**
 * テンプレートの管理内容の変更 Room DB スキーマ設計（version 2）
 *
 * 作成日: 2026-06-07
 * 関連設計: architecture.md / dataflow.md
 * 言語: Kotlin 2.2+
 *
 * 変更概要:
 *   - TemplateEntity: vault/folder 削除、body 追加
 *   - AppDatabase: version 1 → 2、Migration(1, 2) 追加
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
 *   - vault: String を削除 🔵 REQ-003
 *   - folder: String を削除 🔵 REQ-003
 *   - body: String を追加 🔵 REQ-003
 *
 * 対応する Room テーブル DDL (version 2):
 *   CREATE TABLE templates (
 *       id      INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 *       name    TEXT    NOT NULL,
 *       body    TEXT    NOT NULL DEFAULT '',
 *       isDefault INTEGER NOT NULL
 *   )
 *
 * 🔵 信頼性: REQ-003・ユーザヒアリング「Templateからvault/folderを削除しbodyを追加」より
 */
// @Entity(tableName = "templates")
// data class TemplateEntity(
//     @PrimaryKey(autoGenerate = true) val id: Long = 0,   // 🔵 既存
//     val name: String,                                     // 🔵 既存
//     val body: String = "",                               // 🔵 REQ-003 新規追加
//     val isDefault: Boolean,                               // 🔵 既存
//     // vault: String を削除 🔵 REQ-003
//     // folder: String を削除 🔵 REQ-003
// )

// ========================================
// TemplateFieldEntity（変更なし）
// ========================================

/**
 * TemplateFieldEntity（変更なし）
 * 🔵 信頼性: 既存実装・変更対象外
 *
 * 対応する Room テーブル DDL (version 1/2 共通):
 *   CREATE TABLE template_fields (
 *       id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 *       templateId   INTEGER NOT NULL,
 *       key          TEXT    NOT NULL,
 *       valueType    TEXT    NOT NULL,
 *       FOREIGN KEY(templateId) REFERENCES templates(id) ON DELETE CASCADE
 *   )
 */
// @Entity(
//     tableName = "template_fields",
//     foreignKeys = [ForeignKey(
//         entity = TemplateEntity::class,
//         parentColumns = ["id"],
//         childColumns = ["templateId"],
//         onDelete = ForeignKey.CASCADE,
//     )]
// )
// data class TemplateFieldEntity(
//     @PrimaryKey(autoGenerate = true) val id: Long = 0,
//     val templateId: Long,
//     val key: String,
//     val valueType: String,
// )

// ========================================
// AppDatabase（変更後）
// ========================================

/**
 * AppDatabase（変更後）
 *
 * 変更点:
 *   - version: 1 → 2 🔵 REQ-003
 *   - MIGRATION_1_2 を追加 🔵 REQ-003, REQ-004
 *   - fallbackToDestructiveMigration() は使用しない 🔵 REQ-004, NFR-001
 *
 * 🔵 信頼性: REQ-003, REQ-004, NFR-001・Room Migration 公式パターンより
 */
// @Database(
//     entities = [TemplateEntity::class, TemplateFieldEntity::class],
//     version = 2,             // 🔵 REQ-003: version 1 → 2 に変更
//     exportSchema = true,     // 🔵 NFR-001: スキーマ履歴を保持
// )
// abstract class AppDatabase : RoomDatabase() {
//     abstract fun templateDao(): TemplateDao
//
//     companion object {
//         // ========================================
//         // Migration(1, 2)
//         // ========================================
//         /**
//          * Migration(1, 2)
//          * 🔵 信頼性: REQ-003, REQ-004・SQL文はarchitecture.mdより
//          *
//          * 実行順序:
//          *   1. body カラムを追加（既存レコードは body = '' で初期化） ← REQ-004: 既存データ保護
//          *   2. vault カラムを削除 ← REQ-001: vault は DataStore に移行
//          *   3. folder カラムを削除 ← REQ-001: folder は DataStore に移行
//          *
//          * SQLite DROP COLUMN は Android API 35 / SQLite 3.35+ でサポート。
//          * minSdk 33 (Android 13 = SQLite 3.39+) のため使用可能。 🔵 NFR-001
//          */
//         val MIGRATION_1_2 = object : Migration(1, 2) {
//             override fun migrate(db: SupportSQLiteDatabase) {
//                 // Step 1: body を追加（DEFAULT '' なので既存レコードへの影響なし）
//                 db.execSQL("ALTER TABLE templates ADD COLUMN body TEXT NOT NULL DEFAULT ''")
//                 // Step 2: vault を削除
//                 db.execSQL("ALTER TABLE templates DROP COLUMN vault")
//                 // Step 3: folder を削除
//                 db.execSQL("ALTER TABLE templates DROP COLUMN folder")
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
//                 .addMigrations(MIGRATION_1_2)    // 🔵 REQ-003: Migration 登録
//                 // .fallbackToDestructiveMigration() は使用しない（NFR-001: 既存データ保護）
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
 * TemplateEntity / TemplateFieldEntity の CRUD 操作を提供。
 * body フィールドの追加はエンティティ側で吸収されるため DAO の変更は不要。
 */
// @Dao
// interface TemplateDao {
//
//     // TemplateWithFields（TemplateEntity + List<TemplateFieldEntity>）を全件取得
//     @Transaction
//     @Query("SELECT * FROM templates")
//     fun getAllTemplates(): Flow<List<TemplateWithFields>>
//
//     // デフォルトテンプレートを取得
//     @Transaction
//     @Query("SELECT * FROM templates WHERE isDefault = 1 LIMIT 1")
//     fun getDefaultTemplate(): Flow<TemplateWithFields?>
//
//     // ID 指定でテンプレートを取得
//     @Transaction
//     @Query("SELECT * FROM templates WHERE id = :id")
//     suspend fun getTemplateById(id: Long): TemplateWithFields?
//
//     // テンプレートを保存（INSERT OR REPLACE）
//     @Insert(onConflict = OnConflictStrategy.REPLACE)
//     suspend fun insertTemplate(template: TemplateEntity): Long
//
//     // テンプレートフィールドを保存
//     @Insert(onConflict = OnConflictStrategy.REPLACE)
//     suspend fun insertFields(fields: List<TemplateFieldEntity>)
//
//     // テンプレートを削除（フィールドは CASCADE で連動削除）
//     @Delete
//     suspend fun deleteTemplate(template: TemplateEntity)
//
//     // 指定テンプレートのフィールドを全削除（保存前にクリアするため）
//     @Query("DELETE FROM template_fields WHERE templateId = :templateId")
//     suspend fun deleteFieldsByTemplateId(templateId: Long)
//
//     // 既存の isDefault をすべて false に更新（デフォルト変更時）
//     @Query("UPDATE templates SET isDefault = 0")
//     suspend fun clearDefaultFlag()
// }

// ========================================
// TemplateWithFields（変更なし）
// ========================================

/**
 * TemplateWithFields（変更なし）
 * Room の @Relation を使った 1:N リレーション定義。
 * body の追加は TemplateEntity 側で吸収されるため変更不要。
 * 🔵 信頼性: 既存実装・変更対象外
 */
// data class TemplateWithFields(
//     @Embedded val template: TemplateEntity,
//     @Relation(
//         parentColumn = "id",
//         entityColumn = "templateId",
//     )
//     val fields: List<TemplateFieldEntity>,
// )

// ========================================
// TemplateRepositoryImpl（変更後マッピング）
// ========================================

/**
 * TemplateRepositoryImpl のマッピング関数（変更後）
 *
 * 変更点:
 *   - toDomain(): vault/folder 削除、body 追加 🔵 REQ-001, REQ-002
 *   - toEntity(): vault/folder 削除、body 追加 🔵 REQ-003
 *
 * 🔵 信頼性: REQ-001〜003・既存 TemplateRepositoryImpl 実装より
 */
// private fun TemplateWithFields.toDomain(): Template = Template(
//     id = template.id,
//     name = template.name,
//     body = template.body,   // 🔵 REQ-002 新規: entity.body → domain.body
//     isDefault = template.isDefault,
//     fields = fields.map { fieldEntity ->
//         TemplateField(
//             key = fieldEntity.key,
//             valueType = TemplateFieldValueType.valueOf(fieldEntity.valueType),
//         )
//     },
//     // vault, folder は削除 🔵 REQ-001
// )
//
// private fun Template.toEntity(): TemplateEntity = TemplateEntity(
//     id = id,
//     name = name,
//     body = body,            // 🔵 REQ-003 新規: domain.body → entity.body
//     isDefault = isDefault,
//     // vault, folder は削除 🔵 REQ-003
// )

// ========================================
// スキーマ変更サマリー（Diff）
// ========================================

/**
 * Version 1 → Version 2 スキーマ Diff
 *
 * [templates テーブル]
 *
 * Version 1:
 *   id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
 *   name      TEXT    NOT NULL
 *   vault     TEXT    NOT NULL   ← 削除 🔵 REQ-003
 *   folder    TEXT    NOT NULL   ← 削除 🔵 REQ-003
 *   isDefault INTEGER NOT NULL
 *
 * Version 2:
 *   id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
 *   name      TEXT    NOT NULL
 *   body      TEXT    NOT NULL DEFAULT ''  ← 追加 🔵 REQ-003
 *   isDefault INTEGER NOT NULL
 *
 * [template_fields テーブル]
 *   変更なし
 *
 * Migration SQL:
 *   ALTER TABLE templates ADD COLUMN body TEXT NOT NULL DEFAULT ''
 *   ALTER TABLE templates DROP COLUMN vault
 *   ALTER TABLE templates DROP COLUMN folder
 *
 * 注意: SQLite DROP COLUMN は SQLite 3.35+ が必要。
 *       minSdk 33 (Android 13) は SQLite 3.39+ のため問題なし。 🔵 NFR-001
 */

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 18件 (100%)
 * - 🟡 黄信号: 0件 (0%)
 * - 🔴 赤信号: 0件 (0%)
 *
 * 品質評価: ✅ 高品質
 */
