# 編集テンプレート管理機能 コンテキストノート

**作成日**: 2026-05-31
**機能名**: edit-template-management

---

## 技術スタック

- **言語**: Kotlin 2.2+
- **UI**: Jetpack Compose + Material3 (BOM 2024.09.00)
- **アーキテクチャ**: MVVM + Repository + Hilt DI
- **永続化**: Room DB（本機能で初導入）、DataStore Preferences（既存）
- **非同期**: Kotlin Coroutines + StateFlow
- **テスト**: JUnit 4/5, MockK, Compose UI Test
- **minSdk**: 33 (Android 13)

---

## 既存コンポーネント（関連するもの）

| クラス | パス | 役割 | 変更区分 |
|--------|------|------|---------|
| `AppConfig` | `AppConfig.kt` | OBSIDIAN_VAULT / OBSIDIAN_FOLDER / OBSIDIAN_TAGS の定数 | 将来的に不要になる可能性あり |
| `NoteConfig` | `format/NoteConfig.kt` | vault, folder, defaultTags を保持 | 拡張が必要 |
| `FrontmatterBuilder` | `format/FrontmatterBuilder.kt` | title + tags の Frontmatter 生成（AppConfig 直参照） | テンプレート対応に拡張許容 |
| `ProcessedContent` | `content/ProcessedContent.kt` | body, title, contentType のみ保持 | HTMLメタデータ追加が必要 |
| `ShareContent` | `content/ShareContent.kt` | Text / Url / Html / File | 変更なし予定 |
| `HtmlContentProcessor` | `content/HtmlContentProcessor.kt` | HTML → Markdown 変換、title 抽出 | メタデータ抽出追加が必要 |
| `EditFormState` | `ui/EditFormState.kt` | title, body, tagsText, folder の UI 状態 | カスタムフィールド対応が必要 |
| `EditScreen` | `ui/EditScreen.kt` | 編集フォーム画面 | テンプレート適用フィールド追加 |
| `SettingsScreen` | `ui/SettingsScreen.kt` | 設定画面プレースホルダー | テンプレート管理画面へのナビ追加 |
| `MainActivity` | `MainActivity.kt` | ナビゲーション管理 | TemplateListScreen への遷移追加 |

---

## 開発ルール

- 単一Activity構成（`MainActivity`）を維持する
- UI 文字列はすべて `res/values/strings.xml` に定義する
- UI コンポーネントは Compose Material3 を使用する
- Hilt で依存注入する（Module 追加が必要）
- Gradle コマンドは `mise exec -- ./gradlew ...` で実行する

---

## Room DB 初導入の注意点

- `gradle/libs.versions.toml` に Room 依存関係を追加する
- `AppDatabase` クラスを新規作成する
- Hilt Module で `AppDatabase` と DAO を provide する
- テスト用に in-memory DB を使用する

---

## HTMLメタデータ抽出の現状と課題

現在の `HtmlContentProcessor` は HTML → Markdown 変換と title のみ抽出。
新たに抽出が必要なメタデータ:
- `og:title` / `<title>` タグ
- `og:description` / `<meta name="description">`
- ページURL (ShareContent.Url / Html の url フィールドから)
- `article:published_time` / `datePublished` (schema.org)
- `article:modified_time` / `dateModified` (schema.org)
- `author` / `article:author` / `og:site_name`

`ProcessedContent` に `metadata: Map<HtmlMetaKey, String>` を追加して保持することを推奨。

---

## タスク番号管理

**使用済み**: TASK-0001〜TASK-0024
**本機能**: TASK-0025〜（番号は kairo-tasks で割り当て）
