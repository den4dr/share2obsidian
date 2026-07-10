---
name: llm-memo-rewrite-note
description: LLMによるメモ更改機能のコンテキストノート
metadata:
  type: project
---

# LLMによるメモ更改機能の追加 コンテキストノート

**作成日**: 2026-07-05
**機能名**: llm-memo-rewrite

---

## 前提機能

本機能は `template-content-management`（TASK-0043〜0054、全実装済み）で確立された Template/EditScreen/TemplateApplicator の構造の上に、LLM によるコンテンツ書き換え機能を追加するものです。

---

## 技術スタック（現状）

- **言語**: Kotlin 2.2+
- **UI**: Jetpack Compose + Material3 (BOM 2024.09.00)
- **アーキテクチャ**: MVVM + Repository + Hilt DI
- **永続化**: Room DB（既存）、DataStore Preferences（既存、平文）
- **非同期**: Kotlin Coroutines + StateFlow
- **テスト**: JUnit 4/5, MockK, Robolectric, Compose UI Test
- **minSdk**: 33 (Android 13)
- **HTTPクライアント**: 未導入（新規追加が必要。jsoup はネットワークI/Oを行わないHTML静的パース専用）
- **暗号化ストレージ**: 未導入（EncryptedSharedPreferences 等の追加が必要）
- **INTERNET権限**: `AndroidManifest.xml` に宣言済み

---

## 関連コンポーネント（現状の把握）

| クラス | パス | 関連内容 |
|--------|------|---------|
| `EditScreen` | `ui/EditScreen.kt` | 本文入力 `OutlinedTextField`（testTag "body_field"）、送信ボタンあり |
| `EditScreenViewModel` | `ui/EditScreenViewModel.kt` | `updateBody()` が本文更新の唯一の入口。Hilt注入されていない素の ViewModel |
| `EditFormState` | `ui/EditFormState.kt` | vault/title/body/tagsText/folder/customFields を保持 |
| `Template` / `TemplateApplicator` | `TemplateApplicator.kt` | `buildBody()` で `{{content}}` を文字列置換 |
| `TemplateField` / `FieldValueSource` | テンプレートのカスタムフィールド定義（FIXED/HTML_META/URL/EMPTY） |
| `NoteSettings` | DataStore設定。現状 vault/folder のみ |
| `SettingsScreen`/`SettingsViewModel` | vault/folder を即時保存編集 |
| `MainActivity` | `ActivityNotFoundException` を Toast（日本語）で通知する既存エラー処理パターン |

---

## 本機能で追加が想定される変更点（要件定義時点の見立て）

- `NoteSettings` に LLM API エンドポイントURL・モデル名を追加、APIキーは暗号化ストレージへ分離保存
- `Template` に本文用LLMプロンプト（`bodyLlmPrompt` 相当）を追加
- `FieldValueSource` に `LLM` を追加し、`TemplateField` にフィールド用プロンプトを追加
- `EditScreenViewModel` に非同期リライト処理（body必須、tags任意）を追加
- `EditScreen` に「メモを更改」ボタン（body）、「タグを提案」ボタン（tags）を追加
- 新規 HTTP クライアント依存関係の追加（OpenAI互換 Chat Completions 形式）
- 新規 Hilt モジュール（LLMクライアント・リポジトリのDI）
- **重要**: LLMへの入力は EditScreen 上の編集済み本文ではなく、共有/取得直後の元コンテンツ（`ProcessedContent`）である（REQ-002/REQ-302/REQ-406）。現状 `MainActivity` は `ProcessedContent` を `TemplateApplicator.buildBody()` に渡した後 `EditScreenViewModel.initialize()` に解決済みの本文だけを渡しているため、`ProcessedContent` 自体を `EditScreenViewModel` まで保持・伝搬する設計変更が必要になる見込み

---

## 開発ルール（既存踏襲）

- 単一Activity構成（`MainActivity`）を維持する
- UI 文字列はすべて `res/values/strings.xml` に定義する（エラーメッセージは日本語）
- UI コンポーネントは Compose Material3 を使用する
- Hilt で依存注入する
- Gradle コマンドは `mise exec -- ./gradlew ...` で実行する
- エラー時は既存の Toast パターン（日本語メッセージ、`ActivityNotFoundException` と同様の扱い）を踏襲する

---

## タスク番号管理

**使用済み**: TASK-0001〜TASK-0054
**本機能**: 番号は `kairo-tasks` で割り当て（TASK-0055〜想定）
