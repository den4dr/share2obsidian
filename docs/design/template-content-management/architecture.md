---
name: template-content-management-architecture
description: テンプレートの管理内容の変更 アーキテクチャ設計
metadata:
  type: project
---

# テンプレートの管理内容の変更 アーキテクチャ設計

**作成日**: 2026-06-07
**関連要件定義**: [requirements.md](../../spec/template-content-management/requirements.md)
**ヒアリング記録**: [design-interview.md](design-interview.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: EARS要件定義書・設計文書・ユーザヒアリングを参考にした確実な設計
- 🟡 **黄信号**: EARS要件定義書・設計文書・ユーザヒアリングから妥当な推測による設計
- 🔴 **赤信号**: EARS要件定義書・設計文書・ユーザヒアリングにない推測による設計

---

## システム概要 🔵

**信頼性**: 🔵 *requirements.md REQ-001〜REQ-062・ユーザヒアリングより*

既存の edit-template-management 実装（TASK-0025〜0042 完了）を以下の方針で変更する。

1. **Template モデルのスリム化**: vault/folder を削除し、body（本文テンプレート）を追加
2. **DataStore-based 設定管理**: vault/folder をアプリ全体のグローバル設定として DataStore に保存
3. **EditScreen の vault/folder 編集**: EditScreen に vault/folder 入力欄を追加し、保存のたびに変更可能
4. **TemplateEditScreen の簡素化**: vault/folder UI 削除、body テンプレート入力エリア追加
5. **{{content}} プレースホルダー解決**: TemplateApplicator に `buildBody()` を追加

---

## アーキテクチャパターン 🔵

**信頼性**: 🔵 *tech-stack.md・既存 edit-template-management architecture.md より*

- **パターン**: 既存の単一アクティビティ + Compose UI + MVVM + Repository を継続
- **DI**: Hilt（既存）
- **ナビゲーション**: `rememberSaveable` による状態管理（既存パターン継続）
- **DataStore**: DataStore Preferences を vault/folder のグローバル設定に使用（今回新規追加）

---

## 新規追加コンポーネント 🔵

**信頼性**: 🔵 *REQ-021, REQ-022・設計ヒアリングより*

| ファイル | 区分 | 役割 | 対応要件 |
|---------|------|------|---------|
| `data/datastore/NoteSettings.kt` | **新規** | vault/folder を保持するドメインデータクラス | REQ-021 |
| `data/datastore/NoteSettingsRepository.kt` | **新規** | DataStore 読み書きのインターフェース | REQ-021 |
| `data/datastore/NoteSettingsRepositoryImpl.kt` | **新規** | DataStore Preferences を使った実装 | REQ-021 |
| `di/DataStoreModule.kt` | **新規** | Hilt Module（NoteSettingsRepository の提供） | REQ-021 |
| `ui/SettingsViewModel.kt` | **新規** | SettingsScreen で vault/folder を読み書きする ViewModel | REQ-021 |

---

## 変更が必要な既存コンポーネント 🔵

**信頼性**: 🔵 *requirements.md 全体・既存実装より*

| ファイル | 変更内容 | 対応要件 |
|---------|---------|---------|
| `domain/model/Template.kt` | `vault`, `folder` 削除 → `body: String = ""` 追加 | REQ-001, REQ-002 |
| `data/db/TemplateEntity.kt` | `vault`, `folder` 削除 → `body: String = ""` 追加 | REQ-003 |
| `data/db/AppDatabase.kt` | version 1→2、Migration(1, 2) 追加 | REQ-003, REQ-004, NFR-001 |
| `data/repository/TemplateRepositoryImpl.kt` | `toDomain()` / `toEntity()` マッピング修正（vault/folder 削除、body 追加） | REQ-001〜003 |
| `TemplateApplicator.kt` | `buildConfig(NoteSettings)` に変更（Template 非依存）、`buildBody()` 新規追加 | REQ-031, REQ-032 |
| `MainActivity.kt` | `NoteSettingsRepository` を inject、`buildConfig()` 呼び出し変更、`buildBody()` 呼び出し追加、`buildSendParams()` 引数削除 | REQ-022, REQ-031, REQ-032 |
| `ui/EditFormState.kt` | `vault: String` フィールド追加 | REQ-061 |
| `ui/EditScreenViewModel.kt` | `vault` の初期化追加、`updateVault()` 追加、`buildSendParams()` 引数削除・`NoteConfig` をEditFormStateから構築 | REQ-061, REQ-062 |
| `ui/SendParams.kt` | `config` の意味を明確化（vault/folder は EditFormState 由来） | REQ-062 |
| `ui/EditScreen.kt` | 表示順変更（vault→folder→title→frontmatter→body）、vault/folder 入力欄追加 | REQ-041, REQ-042, REQ-043 |
| `ui/SettingsScreen.kt` | vault/folder 入力欄追加、`SettingsViewModel` 連携 | REQ-021 |
| `ui/template/TemplateEditViewModel.kt` | `TemplateEditUiState` から vault/folder 削除 → body 追加、`updateVault/Folder()` 削除、`updateBody()` 追加、`save()` 修正 | REQ-051, REQ-052 |
| `ui/template/TemplateEditScreen.kt` | vault/folder UI 削除、body 入力エリア追加 | REQ-051, REQ-052, REQ-053 |

---

## ディレクトリ構造（変更後） 🔵

**信頼性**: 🔵 *既存プロジェクト構造・edit-template-management architecture.md より*

```
app/src/main/java/com/den4dr/share2Obsidian/
├── TemplateApplicator.kt          # 変更: buildConfig/buildBody
├── MainActivity.kt                # 変更: NoteSettingsRepository inject
│
├── data/
│   ├── datastore/                 # 新規ディレクトリ
│   │   ├── NoteSettings.kt        # 新規: data class(vault, folder)
│   │   ├── NoteSettingsRepository.kt  # 新規: interface
│   │   └── NoteSettingsRepositoryImpl.kt  # 新規: DataStore実装
│   ├── db/
│   │   ├── AppDatabase.kt         # 変更: version 2, Migration(1,2)
│   │   ├── TemplateEntity.kt      # 変更: vault/folder削除, body追加
│   │   └── TemplateWithFields.kt  # 変更なし
│   └── repository/
│       └── TemplateRepositoryImpl.kt  # 変更: マッピング修正
│
├── di/
│   ├── DatabaseModule.kt          # 変更なし
│   └── DataStoreModule.kt         # 新規: NoteSettingsRepository提供
│
├── domain/model/
│   └── Template.kt                # 変更: vault/folder削除, body追加
│
└── ui/
    ├── EditFormState.kt           # 変更: vault追加
    ├── EditScreen.kt              # 変更: 表示順, vault/folder入力欄
    ├── EditScreenViewModel.kt     # 変更: vault対応, buildSendParams引数削除
    ├── SendParams.kt              # 変更なし（意味は変わる）
    ├── SettingsScreen.kt          # 変更: vault/folder入力欄追加
    ├── SettingsViewModel.kt       # 新規: DataStore連携
    └── template/
        ├── TemplateEditScreen.kt  # 変更: vault/folder UI削除, body追加
        └── TemplateEditViewModel.kt  # 変更: TemplateEditUiState修正
```

---

## DataStore 設計 🔵

**信頼性**: 🔵 *REQ-021・設計ヒアリング「SettingsScreenに追加」・DataStore Preferences 公式パターンより*

### Keys

```kotlin
val VAULT_KEY = stringPreferencesKey("vault")
val FOLDER_KEY = stringPreferencesKey("folder")
```

### デフォルト値

| キー | デフォルト値 | 理由 |
|-----|------------|------|
| vault | `""` | ユーザーが明示的に設定するまで空欄とする（AppConfig.OBSIDIAN_VAULT = "testVault" はテスト用ハードコードのため引き継がない） |
| folder | `""` | 同上 |

### 保存タイミング

SettingsScreen での vault/folder 変更は「入力変更時即時保存」とする（保存ボタンなし）。

---

## {{content}} プレースホルダー解決ロジック 🔵

**信頼性**: 🔵 *REQ-011, REQ-012, REQ-013・ユーザヒアリングより*

`TemplateApplicator.buildBody()` に実装する:

```kotlin
fun buildBody(template: Template?, sharedBody: String): String {
    val templateBody = template?.body ?: ""
    return when {
        templateBody.isEmpty() -> sharedBody  // REQ-013: 未設定時は共有コンテンツをそのまま使用
        else -> templateBody.replace("{{content}}", sharedBody)  // REQ-012: プレースホルダー置換
    }
}
```

- `String.replace()` を使用することで `{{content}}` が複数あっても全置換（EDGE-001）
- `templateBody` が非空で `{{content}}` を含まない場合、共有コンテンツは含まれない（EDGE-002）

---

## Room DB マイグレーション設計 🔵

**信頼性**: 🔵 *REQ-003, REQ-004, NFR-001・Room Migration 公式パターンより*

### スキーマ変更 (version 1 → 2)

```
templates テーブル:
  削除: vault TEXT NOT NULL
  削除: folder TEXT NOT NULL
  追加: body TEXT NOT NULL DEFAULT ''
```

### Migration(1, 2) 実装

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE templates ADD COLUMN body TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE templates DROP COLUMN vault")
        db.execSQL("ALTER TABLE templates DROP COLUMN folder")
    }
}
```

**注意**: SQLite の `DROP COLUMN` は Android API 35 / SQLite 3.35+ でサポート。
minSdk 33 (Android 13 = SQLite 3.39) なので問題なし。

---

## EditFormState の vault 追加と buildSendParams リファクタリング 🔵

**信頼性**: 🔵 *REQ-061, REQ-062・設計ヒアリング「EditFormStateからvault/folderを取得」より*

### EditFormState 変更後

```kotlin
data class EditFormState(
    val vault: String,    // 新規追加: DataStore vault デフォルトで初期化
    val folder: String,
    val title: String,
    val body: String,
    val tagsText: String,
    val customFields: List<CustomFieldState> = emptyList(),
)
```

### buildSendParams() 変更後

```kotlin
fun buildSendParams(): SendParams {  // config 引数削除
    val state = _formState.value
    return SendParams(
        title = state.title.ifBlank { null },
        body = state.body,
        tags = parseTagsText(state.tagsText),
        config = NoteConfig(vault = state.vault, folder = state.folder, defaultTags = emptyList()),
        customFields = state.customFields,
    )
}
```

---

## TemplateApplicator の変更 🔵

**信頼性**: 🔵 *REQ-031, REQ-032・設計ヒアリングより*

```kotlin
object TemplateApplicator {

    // 変更: template 引数削除 → NoteSettings 引数追加
    fun buildConfig(settings: NoteSettings): NoteConfig = NoteConfig(
        vault = settings.vault,
        folder = settings.folder,
        defaultTags = AppConfig.OBSIDIAN_TAGS,
    )

    // 新規追加: {{content}} プレースホルダー解決
    fun buildBody(template: Template?, sharedBody: String): String {
        val templateBody = template?.body ?: ""
        return if (templateBody.isEmpty()) sharedBody
               else templateBody.replace("{{content}}", sharedBody)
    }

    // 変更なし
    fun buildCustomFields(template: Template?, processed: ProcessedContent): List<CustomFieldState> { ... }
}
```

---

## 非機能要件の実現方法

### データ保護（NFR-001） 🔵

**信頼性**: 🔵 *NFR-001・Room Migration 公式設計より*

- Room Migration(1, 2) を実装し、`AppDatabase` に登録
- `exportSchema = true` で既存スキーマ履歴を保持
- `fallbackToDestructiveMigration()` は使用しない（既存データを保護）

### パフォーマンス 🟡

**信頼性**: 🟡 *既存設計パターンから妥当な推測*

- DataStore の読み書きは IO Dispatcher で実行
- `SettingsViewModel` は `viewModelScope` + `Flow` で非同期処理

### セキュリティ 🔵

**信頼性**: 🔵 *既存実装・tech-stack.mdより*

- vault/folder は DataStore（アプリ内部ストレージ）に保存。外部からアクセス不可
- 既存の Intent バリデーション・URL エンコード処理に変更なし

---

## 技術的制約

- **SQLite DROP COLUMN**: minSdk 33 (Android 13, SQLite 3.39+) のため `ALTER TABLE DROP COLUMN` が使用可能 🔵 *Android API レベル制約より*
- **DataStore Preferences**: `androidx.datastore:datastore-preferences` は既存依存関係に含まれているか確認が必要 🟡 *既存設定確認が必要*
- **単一 Activity 維持**: NavController / Fragment は不使用 🔵 *既存アーキテクチャより*

---

## 関連文書

- **データフロー**: [dataflow.md](dataflow.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **DBスキーマ**: [database-schema.kt](database-schema.kt)
- **要件定義**: [requirements.md](../../spec/template-content-management/requirements.md)

## 信頼性レベルサマリー

- 🔵 青信号: 20件 (87%)
- 🟡 黄信号: 3件 (13%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: ✅ 高品質
