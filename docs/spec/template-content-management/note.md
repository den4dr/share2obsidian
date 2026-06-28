---
name: template-content-management-note
description: テンプレートの管理内容の変更機能のコンテキストノート
metadata:
  type: project
---

# テンプレートの管理内容の変更 コンテキストノート

**作成日**: 2026-06-07
**機能名**: template-content-management

---

## 前提機能

本機能は `edit-template-management`（TASK-0025〜0042、全実装済み）の設計を変更するものです。
既存の実装が存在するため、変更箇所を明確にして差分実装を行います。

---

## 技術スタック

- **言語**: Kotlin 2.2+
- **UI**: Jetpack Compose + Material3 (BOM 2024.09.00)
- **アーキテクチャ**: MVVM + Repository + Hilt DI
- **永続化**: Room DB（既存）、DataStore Preferences（既存）
- **非同期**: Kotlin Coroutines + StateFlow
- **テスト**: JUnit 4/5, MockK, Compose UI Test
- **minSdk**: 33 (Android 13)

---

## 変更対象コンポーネント

| クラス | パス | 変更内容 |
|--------|------|---------|
| `Template` | `domain/model/Template.kt` | `vault`, `folder` 削除 → `body: String` 追加 |
| `TemplateEntity` | `data/db/TemplateEntity.kt` | `vault`, `folder` 削除 → `body` 追加 |
| `AppDatabase` | `data/db/AppDatabase.kt` | スキーマバージョン番号を上げ Migration 追加 |
| `TemplateApplicator` | `TemplateApplicator.kt` | `buildConfig()` は DataStore 由来に変更、`buildBody()` 追加 |
| `TemplateEditUiState` | `ui/template/TemplateEditScreen.kt` or ViewModel | `vault`, `folder` 削除 → `body` 追加 |
| `TemplateEditViewModel` | `ui/template/TemplateEditViewModel.kt` | vault/folder 関連ロジック削除、body ロジック追加 |
| `TemplateEditScreen` | `ui/template/TemplateEditScreen.kt` | vault/folder UI 削除、body 入力エリア追加 |
| `EditFormState` | `ui/EditFormState.kt` | `vault: String`, `folder: String` フィールド追加 |
| `EditScreenViewModel` | `ui/EditScreenViewModel.kt` | vault/folder 初期化ロジック追加 |
| `EditScreen` | `ui/EditScreen.kt` | 表示順変更（vault→folder→title→frontmatter→body）、vault/folder 入力欄追加 |
| `SendParams` | `ui/SendParams.kt` | vault/folder を含む config の構築方法変更 |
| `MainActivity` | `MainActivity.kt` | `buildConfig()` の DataStore 参照変更 |

---

## 開発ルール

- 単一Activity構成（`MainActivity`）を維持する
- UI 文字列はすべて `res/values/strings.xml` に定義する
- UI コンポーネントは Compose Material3 を使用する
- Hilt で依存注入する
- Gradle コマンドは `mise exec -- ./gradlew ...` で実行する
- Room DB の変更は必ず Migration を実装する（既存データを保護する）

---

## {{content}} プレースホルダー仕様

本文テンプレートで共有コンテンツを埋め込むためのプレースホルダー:

- **形式**: `{{content}}`
- **動作**: テンプレート適用時に共有コンテンツ（テキスト/URL本文）で置換
- **複数含まれる場合**: すべての `{{content}}` を置換する（`String.replace` 動作）
- **body が空の場合**: 共有コンテンツをそのまま使用
- **body に `{{content}}` がないが非空の場合**: テンプレートbodyのみを使用（共有コンテンツは含まない）

---

## vault/folder の責務分離

| 設定場所 | 役割 |
|---------|------|
| DataStore (NoteConfig) | グローバルデフォルト値として保持 |
| SettingsScreen | DataStore の vault/folder を編集する画面 |
| EditScreen | DataStore の値で初期化し、保存のたびにユーザーが変更可能（変更はURIに反映されるがDataStoreには保存されない） |
| Template | **含まない**（テンプレートはファイル内容のみを定義） |

---

## タスク番号管理

**使用済み**: TASK-0001〜TASK-0042
**本機能**: TASK-0043〜（番号は kairo-tasks で割り当て）
