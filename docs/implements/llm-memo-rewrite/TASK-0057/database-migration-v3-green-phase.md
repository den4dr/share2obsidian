# TASK-0057 TDD Greenフェーズ記録: database-migration-v3

**機能名**: database-migration-v3
**タスクID**: TASK-0057
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装方針

Redフェーズで作成した失敗テスト（ユニット7件・統合4件、計11件）を通すため、note.md・要件定義書・database-schema.kt に明記済みの実装内容をそのまま適用した。既存パターン（`MIGRATION_1_2`）を踏襲し、推測を伴わない実装（信頼性 🔵）とした。

- 仕様と現在の実装コードを照合した結果、差異は無かった（要件定義書・テストケース・Redフェーズのテストコードと完全に整合）。
- 実装は5ファイルすべてで「カラム追加」「バージョン変更」「マイグレーション登録」「マッピング追加」のみに限定し、既存ロジック（`MIGRATION_1_2`、DAOクエリ本体等）には一切手を加えていない。

---

## 2. 実装コード

### 2.1 `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateEntity.kt`

```kotlin
package com.den4dr.share2Obsidian.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String = "",
    // 【本文用LLMプロンプト】: テンプレート単位の本文リライト用プロンプトを保存する列（REQ-101）。
    // 空文字は「未設定」を表し、既存レコード・既存呼び出し元との後方互換のためデフォルト値 "" を付与する。
    // 🔵 信頼性レベル: TASK-0057 要件定義・database-schema.kt に基づく（推測なし）
    val bodyLlmPrompt: String = "",
    val isDefault: Boolean,
)
```

### 2.2 `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateFieldEntity.kt`

```kotlin
package com.den4dr.share2Obsidian.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_fields",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["templateId"])],
)
data class TemplateFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "templateId") val templateId: Long,
    val key: String,
    val valueSource: String,   // FieldValueSource.name()
    val valueType: String,     // FieldValueType.name()
    val defaultValue: String,
    val metaKey: String,       // HtmlMetaKey.name() または "" (HTML_META 以外)
    // 【フィールド単位のLLM生成用プロンプト】: valueSource == "LLM" の場合のみ使用する列（REQ-104）。
    // 空文字は「未設定」を表し、既存レコード・既存呼び出し元との後方互換のためデフォルト値 "" を付与する。
    // 🔵 信頼性レベル: TASK-0057 要件定義・database-schema.kt に基づく（推測なし）
    val llmPrompt: String = "",
    val sortOrder: Int,
)
```

### 2.3 `app/src/main/java/com/den4dr/share2Obsidian/data/db/AppDatabase.kt`

```kotlin
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
         * （既存実装・変更なし）
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
```

### 2.4 `app/src/main/java/com/den4dr/share2Obsidian/di/DatabaseModule.kt`（差分箇所）

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "share2obsidian.db")
    // 【マイグレーション登録】: バージョン昇順で登録する（登録漏れは Room が IllegalStateException を投げる）
    // 🔵 信頼性レベル: TASK-0057 要件定義・制約条件（マイグレーション登録順序制約）に基づく
    .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
    .build()
```

### 2.5 `app/src/main/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImpl.kt`

```kotlin
package com.den4dr.share2Obsidian.data.repository

import com.den4dr.share2Obsidian.data.db.TemplateDao
import com.den4dr.share2Obsidian.data.db.TemplateEntity
import com.den4dr.share2Obsidian.data.db.TemplateFieldEntity
import com.den4dr.share2Obsidian.data.db.TemplateWithFields
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TemplateRepositoryImpl @Inject constructor(
    private val dao: TemplateDao,
) : TemplateRepository {

    override fun getAllTemplates(): Flow<List<Template>> =
        dao.getAllTemplatesWithFields().map { list -> list.map { it.toDomain() } }

    override suspend fun getDefaultTemplate(): Template? =
        dao.getDefaultTemplateWithFields()?.toDomain()

    override suspend fun getTemplateById(id: Long): Template? =
        dao.getTemplateWithFieldsById(id)?.toDomain()

    override suspend fun saveTemplate(template: Template): Long {
        if (template.isDefault) {
            dao.clearDefaultExcept(template.id)
        }
        val newId = dao.insertTemplate(template.toEntity())
        dao.deleteFieldsByTemplateId(newId)
        dao.insertFields(template.fields.map { it.toEntity(templateId = newId) })
        return newId
    }

    override suspend fun deleteTemplate(template: Template) {
        dao.deleteTemplate(template.toEntity())
    }

    private fun TemplateWithFields.toDomain(): Template = Template(
        id = template.id,
        name = template.name,
        body = template.body,
        // 【bodyLlmPrompt マッピング】: TemplateEntity.bodyLlmPrompt を Template.bodyLlmPrompt にそのまま反映する
        // 🔵 信頼性レベル: TASK-0057 要件定義 2.3・TC-N01/TC-B01 に基づく（推測なし）
        bodyLlmPrompt = template.bodyLlmPrompt,
        isDefault = template.isDefault,
        fields = fields.map { it.toDomain() },
    )

    private fun TemplateFieldEntity.toDomain(): TemplateField = TemplateField(
        id = id,
        templateId = templateId,
        key = key,
        valueSource = FieldValueSource.valueOf(valueSource),
        valueType = FieldValueType.valueOf(valueType),
        defaultValue = defaultValue,
        metaKey = if (metaKey.isEmpty()) null else HtmlMetaKey.valueOf(metaKey),
        // 【llmPrompt マッピング】: TemplateFieldEntity.llmPrompt を TemplateField.llmPrompt にそのまま反映する
        // 🔵 信頼性レベル: TASK-0057 要件定義 2.3・TC-N02/TC-B01 に基づく（推測なし）
        llmPrompt = llmPrompt,
        sortOrder = sortOrder,
    )

    private fun Template.toEntity(): TemplateEntity = TemplateEntity(
        id = id,
        name = name,
        body = body,
        // 【bodyLlmPrompt 書き込み】: 保存経路（Domain→Entity）でも bodyLlmPrompt を欠落させない
        // 🔵 信頼性レベル: TASK-0057 要件定義 2.3・TC-N03 に基づく（推測なし）
        bodyLlmPrompt = bodyLlmPrompt,
        isDefault = isDefault,
    )

    private fun TemplateField.toEntity(templateId: Long): TemplateFieldEntity = TemplateFieldEntity(
        id = id,
        templateId = templateId,
        key = key,
        valueSource = valueSource.name,
        valueType = valueType.name,
        defaultValue = defaultValue,
        metaKey = metaKey?.name ?: "",
        // 【llmPrompt 書き込み】: 保存経路（Domain→Entity）でも llmPrompt を欠落させない
        // 🔵 信頼性レベル: TASK-0057 要件定義 2.3・TC-N03 に基づく（推測なし）
        llmPrompt = llmPrompt,
        sortOrder = sortOrder,
    )
}
```

### 2.6 テストコードの修正（Redフェーズの構造的バグ修正）

`app/src/test/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImplTest.kt` の `getAllTemplates_unknownValueSource_throwsIllegalArgumentException` は、Kotlin の式本体関数 (`= runBlocking { ... }`) の戻り値型が最終行 `repository.getAllTemplates().first()`（`List<Template>`）から推論されてしまい、`@Test` メソッドが `void` である必要がある JUnit 4 の制約に反し `InvalidTestClassError` でテストクラス自体が起動不能になっていた（実装コードの問題ではなく、Redフェーズで書かれたテストコード自体の構造的バグ）。

修正: 戻り値型を明示的に `Unit` に指定した。

```kotlin
// 修正前
fun getAllTemplates_unknownValueSource_throwsIllegalArgumentException() = runBlocking {

// 修正後
fun getAllTemplates_unknownValueSource_throwsIllegalArgumentException(): Unit = runBlocking {
```

この修正はテストの意図・アサーション内容を一切変更しておらず、Kotlinの型推論による戻り値型のみを明示化したものである。

---

## 3. テスト実行結果

### 3.1 ユニットテスト（対象テストのみ）

```
mise exec -- ./gradlew :app:testDebugUnitTest --tests "*TemplateRepositoryImplTest*"

BUILD SUCCESSFUL
```

`TemplateRepositoryImplTest` の全テスト（既存4件＋Redフェーズ追加7件、計11件）が成功した。

### 3.2 ユニットテスト（プロジェクト全体）

```
mise exec -- ./gradlew :app:testDebugUnitTest

BUILD SUCCESSFUL
```

既存の他クラスのユニットテストも含め全て回帰なく成功した。

### 3.3 統合テスト（コンパイル確認のみ）

実行環境に adb / エミュレータが存在しないため `connectedAndroidTest` の実機実行は未実施。代わりにコンパイルタスクで Red→Green の解消を確認した。

```
mise exec -- ./gradlew :app:compileDebugAndroidTestKotlin

BUILD SUCCESSFUL
```

`AppDatabase.MIGRATION_2_3` の未解決参照エラーが解消され、`AppDatabaseMigrationTest.kt`（`migrate2To3_preservesDataAndAddsLlmColumns` 等、統合テスト4件）がコンパイル可能になったことを確認した。実機での実行成功確認はデバイス/エミュレータが用意でき次第、別途 `mise exec -- ./gradlew connectedAndroidTest` で行う必要がある（課題として次項に記載）。

---

## 4. 課題・改善点（Refactorフェーズで対応）

- **統合テストの実機実行が未確認**: 本環境に adb/エミュレータが無いため `connectedAndroidTest` は未実施。コンパイル成功のみ確認済み。実機/エミュレータ環境で `migrate2To3_preservesDataAndAddsLlmColumns` 等4件の実行時成功を別途確認する必要がある。
- **MIGRATION_2_3 のコメント形式**: `MIGRATION_1_2` は関数コメントに「実行順序」のみを簡潔に記述するスタイルだったが、`MIGRATION_2_3` は日本語コメント要件に従い実装方針・テスト対応まで詳細に記述しており、既存コメントとやや粒度が異なる。Refactorフェーズで両者のコメントスタイルの統一を検討する余地がある（機能に影響なし）。
- **テストファイルの構造的バグ修正**: Red フェーズで作成された `getAllTemplates_unknownValueSource_throwsIllegalArgumentException` の戻り値型問題を Green フェーズで最小修正した。他の `@Test(expected = ...)` パターンのテストで同様の問題が将来再発しないよう、テストコーディング規約としてのメモ化を検討してもよい。

---

## 5. 品質判定

```
✅ 高品質:
- テスト結果: ユニットテスト全件成功（対象11件＋既存回帰含む）、統合テストはコンパイル成功（実機実行はデバイス/エミュレータ不在のため未実施・別途確認予定）
- 実装品質: シンプル（カラム追加・バージョン変更・マッピング追加のみ、既存パターン踏襲）
- リファクタ箇所: コメントスタイルの統一のみ、機能的な改善点なし
- 機能的問題: なし
- コンパイルエラー: なし
- ファイルサイズ: 全対象ファイル800行以下（最大91行）
- モック使用: 実装コード（app/src/main/...）にモック・スタブは含まれていない
```

**総合品質評価**: ✅ 高品質

---

## 次のお勧めステップ

`/tsumiki:tdd-refactor llm-memo-rewrite TASK-0057` でRefactorフェーズ（品質改善）を開始します。
