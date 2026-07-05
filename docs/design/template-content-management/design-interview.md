---
name: template-content-management-design-interview
description: テンプレートの管理内容の変更 設計ヒアリング記録
metadata:
  type: project
---

# テンプレートの管理内容の変更 設計ヒアリング記録

**作成日**: 2026-06-07
**ヒアリング実施**: kairo-design step4

## ヒアリング目的

既存の実装（TASK-0025〜0042 全完了）のコードベースを精査し、
PRD・要件定義書との差分から生じる設計上の不明点を解消するためのヒアリングを実施。

---

## Q1: vault/folder の DataStore 化と SettingsScreen UI

**カテゴリ**: アーキテクチャ / データモデル
**背景**:
- 現在 vault/folder は `AppConfig` に定数としてハードコードされている（"testVault", "70_clippings"）
- 要件「DataStoreのデフォルト値を使いつつ設定画面でも変更可」を実現するには、
  DataStore への永続化 + SettingsScreen への入力 UI 追加が必要
- SettingsScreen は現在「テンプレート管理へのナビゲーション」のみで設定変更機能は持たない

**回答**: SettingsScreenに追加（推奨）

**信頼性への影響**:
- DataStore モジュール（`NoteSettingsRepository` + `DataStoreModule`）の新規追加が確定
- `SettingsViewModel` の新規追加が確定
- `SettingsScreen` に vault/folder 入力欄追加が確定（信頼性: 🔵）

---

## Q2: EditScreenViewModel.buildSendParams() のリファクタリング方針

**カテゴリ**: アーキテクチャ / 技術選択
**背景**:
- 現在 `buildSendParams(config: NoteConfig)` は `config` 引数から vault/folder を `SendParams.config` に渡す
- vault/folder を `EditFormState` に追加した場合、`config` 引数が不要になる可能性がある
- または `config` を維持しつつ `EditFormState.vault/folder` で上書きする方法もある

**回答**: EditFormStateからvault/folderを取得（推奨）

**信頼性への影響**:
- `buildSendParams()` シグネチャから `config: NoteConfig` 引数を削除することが確定
- `EditFormState.vault` + `EditFormState.folder` から `NoteConfig` を構築して `SendParams.config` に設定（信頼性: 🔵）
- `MainActivity` での `viewModel.buildSendParams(config)` → `viewModel.buildSendParams()` 変更が確定

---

## ヒアリング結果サマリー

### 確認できた事項

- vault/folder は DataStore（`NoteSettingsRepository`）で永続化する
- `SettingsScreen` に vault/folder 入力欄を追加する（設定変更機能の実装）
- `EditFormState` に `vault: String` を追加し、`EditScreenViewModel` で管理する
- `buildSendParams()` は引数なしで `EditFormState` から `NoteConfig` を組み立てる

### 設計方針の決定事項

1. **DataStore**: `NoteSettingsRepository` + `DataStoreModule` を新規追加
2. **SettingsScreen**: `SettingsViewModel` を介して vault/folder を DataStore で読み書き
3. **EditFormState**: `vault: String` を追加（`folder` と対称）
4. **buildSendParams()**: 引数削除、`EditFormState.vault/folder` から `NoteConfig` を構築
5. **TemplateApplicator**: `buildConfig(NoteSettings)` → DataStore 値を使用、`buildBody()` を新規追加

### 残課題

- DataStore の初期値: `AppConfig` の定数値を初回起動時のデフォルトとするか空文字列とするか（設計判断: 空文字列、ユーザーが明示設定）
- `SettingsScreen` の vault/folder 保存タイミング: 入力変更時即時保存 vs 保存ボタン（設計判断: 入力変更時即時保存・デバウンスなし）

### 信頼性レベル分布

**ヒアリング前**:
- 🔵 青信号: 5件
- 🟡 黄信号: 8件
- 🔴 赤信号: 4件

**ヒアリング後**:
- 🔵 青信号: 15件 (+10)
- 🟡 黄信号: 4件 (-4)
- 🔴 赤信号: 0件 (-4)

---

## 関連文書

- **アーキテクチャ設計**: [architecture.md](architecture.md)
- **データフロー**: [dataflow.md](dataflow.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **DBスキーマ**: [database-schema.kt](database-schema.kt)
- **要件定義**: [requirements.md](../../spec/template-content-management/requirements.md)
