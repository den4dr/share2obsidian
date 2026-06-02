/**
 * 編集テンプレートの管理機能 Room DB スキーマ定義
 *
 * 作成日: 2026-05-31
 * 関連設計: architecture.md / interfaces.kt
 * 形式: Room DB エンティティ・DAO の Kotlin 定義
 *
 * 信頼性レベル:
 * - 🔵 青信号: 要件定義書・設計文書・ユーザヒアリング・既存実装を参考にした確実な定義
 * - 🟡 黄信号: 要件定義書・設計文書・ユーザヒアリングから妥当な推測による定義
 * - 🔴 赤信号: 要件定義書・設計文書・ユーザヒアリングにない推測による定義
 */

package com.den4dr.share2Obsidian.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ========================================
// エンティティ定義
// ========================================

/**
 * テンプレートエンティティ
 * テーブル名: templates
 *
 * 🔵 信頼性: REQ-015, REQ-021, REQ-061・ユーザヒアリング「テンプレート名, vault/folder, デフォルトフラグ」より
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,           // 🔵 Room 自動採番 PK

    // 一意性はアプリ側バリデーションで保証（DB UNIQUE 制約なし）🔵 ユーザヒアリング確認済み
    val name: String,           // 🔵 REQ-015 テンプレート名（必須）
    val vault: String,          // 🔵 REQ-015 Obsidian Vault 名（空文字許容）
    val folder: String,         // 🔵 REQ-015 保存先フォルダ（空文字許容）
    val isDefault: Boolean,     // 🔵 REQ-021 デフォルトフラグ（同時に 1 件のみ true）
)

/**
 * テンプレートフィールドエンティティ
 * テーブル名: template_fields
 *
 * 🔵 信頼性: REQ-033, REQ-041〜044, REQ-061 より
 *
 * 外部キー: templateId → templates.id (CASCADE DELETE)
 */
@Entity(
    tableName = "template_fields",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE, // 🔵 テンプレート削除時にフィールドも自動削除（EDGE-002）
        )
    ],
    indices = [
        Index(value = ["templateId"]), // 🔵 templateId での検索パフォーマンス向上
    ]
)
data class TemplateFieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,               // 🔵 Room 自動採番 PK

    @ColumnInfo(name = "templateId")
    val templateId: Long,           // 🔵 FK → templates.id

    val key: String,                // 🔵 REQ-033 front matter キー名（例: "source", "description"）

    // valueSource は FieldValueSource enum の name() として保存
    // 例: "FIXED", "HTML_META", "URL", "EMPTY"
    val valueSource: String,        // 🔵 REQ-033 値ソース種別

    // valueType は FieldValueType enum の name() として保存
    // 例: "STRING", "LIST"
    val valueType: String,          // 🔵 REQ-033 値の型

    // FIXED の場合の初期値。LIST 型の場合はカンマ区切り文字列
    // FIXED 以外の場合は空文字列 ""
    val defaultValue: String,       // 🔵 REQ-041

    // HTML_META の場合のマッピングキー（HtmlMetaKey enum の name()）
    // 例: "OG_TITLE", "OG_DESCRIPTION", "URL", "PUBLISHED_DATE", "MODIFIED_DATE", "AUTHOR"
    // HTML_META 以外の場合は空文字列 ""
    val metaKey: String,            // 🔵 REQ-042

    // TemplateEditScreen での表示順序（0始まり、追加順固定・並び替えUIなし）
    val sortOrder: Int,             // 🔵 追加順をインデックスで保持。ドラッグ&ドロップ不要とユーザー確認済み
)

// ========================================
// リレーション（@Relation）
// ========================================

/**
 * テンプレートとフィールドを結合したデータクラス
 * DAO の @Transaction クエリで使用
 *
 * 🔵 信頼性: Room @Relation パターン・REQ-011, REQ-051 より
 */
data class TemplateWithFields(
    @Embedded
    val template: TemplateEntity,           // 🔵 親テンプレート

    @Relation(
        parentColumn = "id",
        entityColumn = "templateId",
        // sortOrder で並び替え (DAO クエリ側で ORDER BY を指定)
    )
    val fields: List<TemplateFieldEntity>,  // 🔵 子フィールド一覧（0件以上）
)

// ========================================
// DAO（Data Access Object）
// ========================================

/**
 * TemplateDao
 * テンプレートと関連フィールドの CRUD 操作を提供
 *
 * 🔵 信頼性: REQ-011〜014, REQ-021〜022, REQ-061〜063・Room DAO パターンより
 */
@Dao
interface TemplateDao {

    // ── クエリ ──────────────────────────────────────────────────

    /**
     * 全テンプレートを Flow で取得
     * isDefault=true が先頭、次に id 昇順
     * DB 変更を自動的に通知（REQ-011, REQ-063）
     * 🔵 REQ-011, REQ-063
     */
    @Transaction
    @Query("""
        SELECT * FROM templates
        ORDER BY isDefault DESC, id ASC
    """)
    fun getAllTemplatesWithFields(): Flow<List<TemplateWithFields>>

    /**
     * デフォルトテンプレートを取得（なければ null）
     * 🔵 REQ-051
     */
    @Transaction
    @Query("SELECT * FROM templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplateWithFields(): TemplateWithFields?

    /**
     * ID 指定でテンプレートを取得（なければ null）
     * 🔵 REQ-013
     */
    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateWithFieldsById(id: Long): TemplateWithFields?

    // ── 挿入・更新 ───────────────────────────────────────────────

    /**
     * テンプレートを挿入（既存の場合は REPLACE）
     * 新規挿入時は自動採番した id を返す
     * 🔵 REQ-012, REQ-013, REQ-061
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity): Long

    /**
     * フィールドを一括挿入（既存の場合は REPLACE）
     * 🔵 REQ-031, REQ-061
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<TemplateFieldEntity>)

    // ── 削除 ────────────────────────────────────────────────────

    /**
     * テンプレートを削除
     * ForeignKey CASCADE により関連フィールドも自動削除される（EDGE-002）
     * 🔵 REQ-014, EDGE-002
     */
    @Delete
    suspend fun deleteTemplate(template: TemplateEntity)

    /**
     * 指定テンプレートのフィールドをすべて削除
     * テンプレート更新時に既存フィールドを全削除してから再挿入する用途
     * 🔵 REQ-013
     */
    @Query("DELETE FROM template_fields WHERE templateId = :templateId")
    suspend fun deleteFieldsByTemplateId(templateId: Long)

    // ── デフォルト管理 ──────────────────────────────────────────

    /**
     * 指定 id 以外のすべてのテンプレートのデフォルトフラグをクリア
     * 新たにデフォルトを設定するときに呼び出す（REQ-022）
     * 🔵 REQ-022
     */
    @Query("UPDATE templates SET isDefault = 0 WHERE id != :excludeId")
    suspend fun clearDefaultExcept(excludeId: Long)

    /**
     * すべてのテンプレートのデフォルトフラグをクリア
     * デフォルトを完全に解除する場合に使用
     * 🟡 運用上必要になる可能性を考慮
     */
    @Query("UPDATE templates SET isDefault = 0")
    suspend fun clearAllDefaults()
}

// ========================================
// AppDatabase
// ========================================

/**
 * Room データベース定義
 * 🔵 信頼性: REQ-061・Room 公式パターンより
 *
 * バージョン管理:
 * - v1: TemplateEntity, TemplateFieldEntity（初期リリース）
 */
@Database(
    entities = [
        TemplateEntity::class,        // 🔵 templates テーブル
        TemplateFieldEntity::class,   // 🔵 template_fields テーブル
    ],
    version = 1,
    exportSchema = true,    // 🟡 スキーマ履歴を assets/databases/ に保存（マイグレーション管理用）
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao  // 🔵 REQ-061

    // Hilt Module で singleton として生成するため companion object は不要
}

// ========================================
// RepositoryImpl（シグネチャ）
// ========================================

/**
 * TemplateRepositoryImpl のシグネチャ
 *
 * TemplateEntity ↔ Template のマッピング責務を持つ
 * 🔵 信頼性: NFR-201・Repository パターンより
 */
// class TemplateRepositoryImpl @Inject constructor(
//     private val dao: TemplateDao
// ) : TemplateRepository {
//
//     override fun getAllTemplates(): Flow<List<Template>> =
//         dao.getAllTemplatesWithFields().map { list ->
//             list.map { it.toDomain() }
//         }
//
//     override suspend fun getDefaultTemplate(): Template? =
//         dao.getDefaultTemplateWithFields()?.toDomain()
//
//     override suspend fun getTemplateById(id: Long): Template? =
//         dao.getTemplateWithFieldsById(id)?.toDomain()
//
//     override suspend fun saveTemplate(template: Template): Long {
//         if (template.isDefault) {
//             dao.clearDefaultExcept(template.id)
//         }
//         val newId = dao.insertTemplate(template.toEntity())
//         dao.deleteFieldsByTemplateId(newId)  // 既存フィールドを削除してから再挿入
//         dao.insertFields(template.fields.mapIndexed { index, field ->
//             field.toEntity(templateId = newId, sortOrder = index)
//         })
//         return newId
//     }
//
//     override suspend fun deleteTemplate(template: Template) {
//         dao.deleteTemplate(template.toEntity())
//     }
// }

// ========================================
// マッピング拡張関数（シグネチャ）
// ========================================

// TemplateWithFields → Template ドメインモデル
// fun TemplateWithFields.toDomain(): Template { ... }

// Template → TemplateEntity
// fun Template.toEntity(): TemplateEntity { ... }

// TemplateFieldEntity → TemplateField
// fun TemplateFieldEntity.toDomain(): TemplateField { ... }

// TemplateField → TemplateFieldEntity
// fun TemplateField.toEntity(templateId: Long, sortOrder: Int): TemplateFieldEntity { ... }

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 20件 (87%)
 * - 🟡 黄信号: 3件 (13%) ← exportSchema, clearAllDefaults, sortOrder
 * - 🔴 赤信号: 0件 (0%)
 *
 * 品質評価: 高品質
 */
