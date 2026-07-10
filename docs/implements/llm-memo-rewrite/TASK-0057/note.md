# TASK-0057 開発コンテキストノート

**タスクID**: TASK-0057  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: TemplateEntity/TemplateFieldEntity/AppDatabase マイグレーション（v2→v3）・Repositoryマッピング修正

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **最低SDK**: API 33 (Android 13、SQLite 3.39+)
- **ターゲットSDK**: API 36
- **Java互換性**: 11
- **データベース**: Room (2024.09.00 以降)

### アーキテクチャパターン
- **DB層**: Room ORM + SQLite
- **マイグレーション**: Room Migration API（既存 MIGRATION_1_2 パターン踏襲）
- **Repository**: TemplateRepositoryImpl（データクラス往復変換）

### 関連技術
- **テストフレームワーク**: JUnit 4 + Room MigrationTestHelper
- **Gradle**: AGP 9.1.0、version catalog (`gradle/libs.versions.toml`)

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ・Room版ピン
- `app/build.gradle.kts` - Gradle設定・テスト依存関係

---

## 2. 開発ルール

### TDD開発フロー
1. `/tsumiki:tdd-requirements TASK-0057` - 詳細要件定義
2. `/tsumiki:tdd-testcases` - テストケース作成（3個の単体テスト + 1個の統合テスト）
3. `/tsumiki:tdd-red` - テスト実装（失敗状態）
4. `/tsumiki:tdd-green` - 最小実装（テスト通過）
5. `/tsumiki:tdd-refactor` - リファクタリング（品質向上）
6. `/tsumiki:tdd-verify-complete` - 品質確認・完了

### テスト実行コマンド
```bash
# ユニットテスト実行（RepositoryImplマッピングテスト）
mise exec -- ./gradlew test

# 特定テストクラス実行
mise exec -- ./gradlew test --tests "*TemplateRepositoryImplTest*"

# インストルメント化テスト実行（AppDatabaseMigrationTest）
mise exec -- ./gradlew connectedAndroidTest

# 全体チェック
mise exec -- ./gradlew clean lint build test connectedAndroidTest
```

### マイグレーション実装ルール
- **既存パターン準拠**: MIGRATION_1_2 実装パターンを踏襲（`db.execSQL()` で SQL実行）
- **ADD COLUMNのみ**: DROP COLUMN を伴わないため SQLiteバージョン制約なし
- **デフォルト値**: `DEFAULT ''` で既存レコードを初期化
- **DataStore非使用**: DBスキーマ内のカラム定義のため DataStore は不要

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0057.md` - 完了条件・実装詳細
- `docs/design/llm-memo-rewrite/database-schema.kt` - スキーマ定義・マッピング記述例

---

## 3. 関連実装

### 変更対象ファイル（既存）

#### 1. TemplateEntity.kt
**現在** (version 2):
```kotlin
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String = "",
    val isDefault: Boolean,
)
```

**変更内容**: `bodyLlmPrompt: String = ""` をカラムとして追加（REQ-101）
- 位置: `body` フィールドの直後、`isDefault` の前
- テンプレート単位の本文用LLMプロンプト保存用
- デフォルト値: 空文字 `""` （後方互換性）

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateEntity.kt`

#### 2. TemplateFieldEntity.kt
**現在** (version 2):
```kotlin
@Entity(
    tableName = "template_fields",
    foreignKeys = [ForeignKey(...)],
    indices = [Index(...)],
)
data class TemplateFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "templateId") val templateId: Long,
    val key: String,
    val valueSource: String,  // FieldValueSource.name()
    val valueType: String,
    val defaultValue: String,
    val metaKey: String,
    val sortOrder: Int,
)
```

**変更内容**: `llmPrompt: String = ""` をカラムとして追加（REQ-104）
- 位置: `metaKey` フィールドの直後、`sortOrder` の前
- `valueSource == "LLM"` の場合のみ使用
- デフォルト値: 空文字 `""` （後方互換性）

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateFieldEntity.kt`

#### 3. AppDatabase.kt
**現在** (version 2):
```kotlin
@Database(
    entities = [TemplateEntity::class, TemplateFieldEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) { /* 既存 */ }
    }
}
```

**変更内容**:
- `version: 2` → `version: 3` に変更
- `MIGRATION_2_3` を追加（MIGRATION_1_2 と並行注册）
- SQL実行:
  ```sql
  ALTER TABLE templates ADD COLUMN bodyLlmPrompt TEXT NOT NULL DEFAULT ''
  ALTER TABLE template_fields ADD COLUMN llmPrompt TEXT NOT NULL DEFAULT ''
  ```

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/data/db/AppDatabase.kt`

#### 4. DatabaseModule.kt
**変更内容**: `.addMigrations(...)` に `MIGRATION_2_3` を登録
- 既存: `addMigrations(AppDatabase.MIGRATION_1_2)` 
- 変更後: `addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)`

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/di/DatabaseModule.kt`

#### 5. TemplateRepositoryImpl.kt
**変更内容**: `toDomain()`/`toEntity()` マッピング関数に `bodyLlmPrompt`/`llmPrompt` を追加

**`TemplateWithFields.toDomain()` 関数** (Template への変換):
```kotlin
private fun TemplateWithFields.toDomain(): Template = Template(
    id = template.id,
    name = template.name,
    body = template.body,
    bodyLlmPrompt = template.bodyLlmPrompt,   // 🔵 REQ-101 新規追加
    isDefault = template.isDefault,
    fields = fields.map { it.toDomain() },
)
```

**`TemplateFieldEntity.toDomain()` 関数** (TemplateField への変換):
```kotlin
private fun TemplateFieldEntity.toDomain(): TemplateField = TemplateField(
    id = id,
    templateId = templateId,
    key = key,
    valueSource = FieldValueSource.valueOf(valueSource),
    valueType = FieldValueType.valueOf(valueType),
    defaultValue = defaultValue,
    metaKey = if (metaKey.isEmpty()) null else HtmlMetaKey.valueOf(metaKey),
    llmPrompt = llmPrompt,   // 🔵 REQ-104 新規追加
    sortOrder = sortOrder,
)
```

**`Template.toEntity()` 関数** (TemplateEntity への変換):
```kotlin
private fun Template.toEntity(): TemplateEntity = TemplateEntity(
    id = id,
    name = name,
    body = body,
    bodyLlmPrompt = bodyLlmPrompt,   // 🔵 REQ-101 新規追加
    isDefault = isDefault,
)
```

**`TemplateField.toEntity()` 関数** (TemplateFieldEntity への変換):
```kotlin
private fun TemplateField.toEntity(templateId: Long): TemplateFieldEntity = TemplateFieldEntity(
    id = id,
    templateId = templateId,
    key = key,
    valueSource = valueSource.name,
    valueType = valueType.name,
    defaultValue = defaultValue,
    metaKey = metaKey?.name ?: "",
    llmPrompt = llmPrompt,   // 🔵 REQ-104 新規追加
    sortOrder = sortOrder,
)
```

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImpl.kt`

### 前提タスクからの伝播

TASK-0056 で既に実装済み（ドメインモデル側の変更）:
- `Template.bodyLlmPrompt: String = ""`
- `TemplateField.llmPrompt: String = ""`
- `FieldValueSource.LLM` enum値

**ファイルパス**:
- `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt`

---

## 4. 設計文書

### 主要設計ドキュメント

#### database-schema.kt
**内容**: Version 2→3 スキーマ Diff、MIGRATION_2_3 実装コード、マッピング関数コード例
- TemplateEntity・TemplateFieldEntity エンティティ定義（変更後）
- AppDatabase version変更・MIGRATION_2_3 追加方法
- TemplateRepositoryImpl マッピング関数（変更後）
- スキーマ Diff（version 2 vs 3）

**信頼性**: 🔵 青信号 100% - REQ-101, REQ-104・既存 MIGRATION_1_2 実装パターンより

**ファイルパス**: `docs/design/llm-memo-rewrite/database-schema.kt`

#### architecture.md
**内容**: システムアーキテクチャ・コンポーネント関係図
- `ProcessedContent` 保持設計（REQ-406）
- 「変更が必要な既存コンポーネント」テーブル（行 66-89）
- DB関連の変更点（TemplateEntity, AppDatabase, TemplateRepositoryImpl）

**ファイルパス**: `docs/design/llm-memo-rewrite/architecture.md`

#### interfaces.kt
**内容**: Kotlin インターフェース・型定義
- Template・TemplateField・FieldValueSource（データクラス定義）

**ファイルパス**: `docs/design/llm-memo-rewrite/interfaces.kt`

### 参照元
- `docs/spec/llm-memo-rewrite/requirements.md` - REQ-101, REQ-104, REQ-303, REQ-406
- `docs/tasks/llm-memo-rewrite/TASK-0057.md` - タスク定義・完了条件・実装詳細
- `docs/tasks/llm-memo-rewrite/overview.md` - プロジェクト全体概要

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### ユニットテスト
- **フレームワーク**: JUnit 4
- **モック・スタブ**: MockK（Kotlin ネイティブ）
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/data/repository/`

#### インストルメント化テスト（マイグレーション）
- **フレームワーク**: JUnit 4 + Room `MigrationTestHelper`
- **テストディレクトリ**: `app/src/androidTest/java/com/den4dr/share2Obsidian/data/db/`
- **実行要件**: Android デバイス/エミュレータが必要（`connectedAndroidTest`）

#### Gradle設定
```gradle
testImplementation(libs.junit)              // JUnit 4
testImplementation(libs.mockk)              // MockK
testImplementation(libs.kotlinx.coroutines.test)  // Coroutines テスト
androidTestImplementation(libs.androidx.room.testing)  // Room MigrationTestHelper
```

**ファイルパス**: `app/build.gradle.kts`

### 既存テストパターン

#### TemplateRepositoryImplTest.kt（参考）
**ファイルパス**: `app/src/test/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImplTest.kt`

パターン: データクラスの往復変換テスト
- `toDomain()` で値が保持されるか
- `toEntity()` で値が保持されるか
- Enum 値の往復変換

#### AppDatabaseMigrationTest.kt
**ファイルパス**: `app/src/androidTest/java/com/den4dr/share2Obsidian/data/db/AppDatabaseMigrationTest.kt`

既存テストメソッド:
- `migrate1To2_preservesDataAndAddsBody()` - MIGRATION_1_2 テストケース

新規追加対象:
- `migrate2To3_preservesDataAndAddsLlmColumns()` - MIGRATION_2_3 テストケース

### テストケース（TASK-0057で実装予定）

#### テストケース1: bodyLlmPrompt の往復変換（ユニットテスト）
**テストパターン**: `TemplateRepositoryImplTest`
```
Given: bodyLlmPrompt = "要約して" を持つ TemplateEntity
When:  toEntity() → toDomain() の往復変換を行う
Then:  bodyLlmPrompt が "要約して" のまま保持される
信頼性: 🔵 REQ-101・data class マッピング仕様
```

#### テストケース2: llmPrompt の往復変換（ユニットテスト）
**テストパターン**: `TemplateRepositoryImplTest`
```
Given: llmPrompt = "タイトルを生成" を持つ TemplateFieldEntity
When:  toEntity() → toDomain() の往復変換を行う
Then:  llmPrompt が "タイトルを生成" のまま保持される
信頼性: 🔵 REQ-104・data class マッピング仕様
```

#### テストケース3: デフォルト値の往復変換（ユニットテスト）
**テストパターン**: `TemplateRepositoryImplTest`
```
Given: bodyLlmPrompt/llmPrompt を指定せずに生成した Template/TemplateField
When:  toEntity() → toDomain() の往復変換を行う
Then:  bodyLlmPrompt/llmPrompt は "" のまま保持される
信頼性: 🔵 後方互換性・NFR-001
```

#### テストケース4: MIGRATION_2_3 による既存データ保持（統合テスト）
**テストパターン**: `AppDatabaseMigrationTest`
```
Given: Version 2 のスキーマで既存レコード（templates, template_fields）を投入
When:  MIGRATION_2_3 を実行
Then:  既存レコードの値が保持されたまま、bodyLlmPrompt/llmPrompt が '' で追加される
信頼性: 🔵 Room MigrationTestHelper 標準パターン
```

### テスト実行手順

```bash
# ステップ1: ユニットテスト実行
mise exec -- ./gradlew test

# ステップ2: インストルメント化テスト実行（デバイス/エミュレータ必須）
mise exec -- ./gradlew connectedAndroidTest

# ステップ3: 全体チェック（Lint + Build + Test）
mise exec -- ./gradlew clean lint build test connectedAndroidTest
```

---

## 6. 注意事項

### 実装時の重要なポイント

#### 1. MIGRATION_1_2 との相互作用
- **既存マイグレーションは変更しない**: MIGRATION_1_2 の実装・テストに変更を加えない
- **マイグレーション登録順序**: DatabaseModule で `addMigrations(MIGRATION_1_2, MIGRATION_2_3)` と順序を厳密に管理（バージョン順）
- **既存テストの回帰**: 既存の `migrate1To2_preservesDataAndAddsBody` テストが引き続き通ることを確認

#### 2. Entityエンティティのデフォルト値
- **後方互換性**: `bodyLlmPrompt = ""`, `llmPrompt = ""` でデフォルト値を明示指定
- **テスト**: 既存呼び出し元（`TemplateApplicator`, `MainActivity`）への影響を最小化

#### 3. SQL ALTER TABLE ADD COLUMN
- **SQLiteバージョン制約**: ADD COLUMN はSQLite 3.0+ で広くサポート（DROP COLUMN と異なり制約なし）
- **既存データの初期化**: `DEFAULT ''` で既存レコードを初期化

#### 4. Repository マッピング関数の対称性
- **双方向マッピング**: `toDomain()` と `toEntity()` の両方に変更を反映
- **フィールド順序**: マッピング関数の可読性のため、ドメインモデルと同じ順序（body → bodyLlmPrompt → fields）

#### 5. テスト対象ファイルの配置
- **ユニットテスト**: `TemplateRepositoryImplTest.kt` に追加（または既存テストファイルに新規テストメソッド追加）
- **統合テスト**: `AppDatabaseMigrationTest.kt` に `migrate2To3_preservesDataAndAddsLlmColumns` メソッド追加

### セキュリティ・パフォーマンス要件
- **NFR-001** (タイムアウト): DB マイグレーションはローカル実行のため影響なし
- **NFR-101** (APIキー暗号化): DB層では不関連（LlmSettingsRepository で実装）

### 技術的制約
- **SQLite バージョン**: minSdk 33 (Android 13 = SQLite 3.39+) のため ADD COLUMN は安全に使用可能
- **Room 公式パターン**: `MigrationTestHelper` は Room 公式ドキュメント推奨の検証方法

### ビルド・実行手順（参考）
Java は `.mise.toml` で管理されているため、Gradle コマンドは必ず `mise exec --` 経由で実行：
```bash
# ビルド
mise exec -- ./gradlew build

# テスト実行
mise exec -- ./gradlew test connectedAndroidTest

# クリーンビルド + Lint + テスト
mise exec -- ./gradlew clean lint build test connectedAndroidTest
```

---

## 7. ファイル一覧（参照用）

### 対象変更ファイル（実装・テスト）
| ファイル | 変更内容 | 信頼性 |
|---------|---------|--------|
| `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateEntity.kt` | `bodyLlmPrompt: String = ""` 追加 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateFieldEntity.kt` | `llmPrompt: String = ""` 追加 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/db/AppDatabase.kt` | version 3, MIGRATION_2_3 追加 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/di/DatabaseModule.kt` | MIGRATION_2_3 登録 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImpl.kt` | マッピング関数の `bodyLlmPrompt`/`llmPrompt` 追加 | 🔵 |
| `app/src/test/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImplTest.kt` | ユニットテスト（往復変換） | 🔵 |
| `app/src/androidTest/java/com/den4dr/share2Obsidian/data/db/AppDatabaseMigrationTest.kt` | 統合テスト（MIGRATION_2_3） | 🔵 |

### 参照ドキュメント（読み込み専用）
| ファイル | 概要 | 信頼性 |
|---------|------|--------|
| `docs/design/llm-memo-rewrite/database-schema.kt` | スキーマ定義・マッピング記述例 | 🔵 |
| `docs/design/llm-memo-rewrite/architecture.md` | システムアーキテクチャ | 🔵 |
| `docs/spec/llm-memo-rewrite/requirements.md` | 機能要件定義（REQ-101, REQ-104等） | 🔵 |
| `docs/spec/llm-memo-rewrite/acceptance-criteria.md` | 受け入れ基準 | 🔵 |
| `docs/tasks/llm-memo-rewrite/TASK-0057.md` | タスク定義・完了条件 | 🔵 |
| `docs/tasks/llm-memo-rewrite/overview.md` | プロジェクト全体概要（19タスク） | 🔵 |
| `app/build.gradle.kts` | Gradle設定・テスト依存関係 | 🔵 |
| `gradle/libs.versions.toml` | バージョンカタログ | 🔵 |

### 前提条件（TASK-0056 完了状態）
| ファイル | 状態 | 確認方法 |
|---------|------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt` | `bodyLlmPrompt` 実装済み | `cat app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt \| grep bodyLlmPrompt` |
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt` | `llmPrompt` 実装済み | `cat app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt \| grep llmPrompt` |
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt` | `LLM` enum値実装済み | `cat app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt \| grep LLM` |

---

## 8. 信頼性レベルサマリー

### 項目別信頼性

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 実装詳細 | 5 | 0 | 0 | 5 |
| テスト設計 | 4 | 0 | 0 | 4 |
| マイグレーション | 1 | 0 | 0 | 1 |

### 全体評価

- **総項目数**: 10項目
- 🔵 **青信号**: 10項目 (100%)
- 🟡 **黄信号**: 0項目 (0%)
- 🔴 **赤信号**: 0項目 (0%)

**品質評価**: ✅ 高品質

---

**このノートは TDD RED フェーズ開始前のコンテキスト情報集約です。実装時に参照。**
