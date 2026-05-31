# 設定画面 設計ヒアリング記録

**作成日**: 2026-05-31
**ヒアリング実施**: step4 既存情報ベースの差分ヒアリング

## ヒアリング目的

要件定義書・note.md・既存実装（MainActivity, EditScreen）を確認し、設計上の不明点を明確化するためのヒアリングを実施しました。

## 質問と回答

### Q1: 設計規模について

**カテゴリ**: 優先順位
**背景**: フル設計か軽量設計かで出力ドキュメント数が変わるため確認。

**回答**: フル設計（推奨）

**信頼性への影響**:
- architecture.md / dataflow.md / design-interview.md / interfaces.kt をすべて作成。
- DB スキーマ・API 仕様はローカルアプリのため生成不要と判断。

---

### Q2: 既存実装の詳細分析が必要か

**カテゴリ**: アーキテクチャ
**背景**: EditScreen の現在のシグネチャを正確に把握する必要があった。

**回答**: 必要

**調査結果**:
- `EditScreen(viewModel, config, onSend, onCancel)` — 現在 4 引数
- `onNavigateToSettings: () -> Unit` を第5引数として追加する設計を確定
- MainActivity の `setContent` は `lifecycleScope.launch` 内で呼ばれており、Intent なし時とは分岐を分ける必要があると確認

**信頼性への影響**:
- `EditScreen` 変更仕様が 🔵 に確定。
- MainActivity の分岐設計（直接起動 vs 共有フロー）が 🔵 に確定。

---

### Q3: アイコンタップ起動時の最初の画面

**カテゴリ**: アーキテクチャ
**背景**: note.md では「AppLauncher → Settings」と記述されていたが、中間画面が必要かどうか不明だった。

**回答**: SettingsScreen を直接表示

**信頼性への影響**:
- フロー1（アイコン起動）の設計が 🔵 に確定。
- 中間ランチャー画面は不要と確定し、実装スコープが削減。
- `setContent { SettingsScreen(onNavigateBack = { finish() }) }` のシンプルな実装方針が確定。

---

## ヒアリング結果サマリー

### 確認できた事項
- EditScreen のシグネチャ（`onNavigateToSettings` 追加で対応可能）
- アイコン起動は SettingsScreen を直接表示（中間画面不要）
- `rememberSaveable` を使ったナビゲーション状態管理で EDGE-101（画面回転）に対応

### 設計方針の決定事項
- **ナビゲーション**: NavController 不使用、`rememberSaveable { mutableStateOf(false) }` による状態ベース条件分岐
- **直接起動**: `setContent { SettingsScreen(...) }` を即時呼び出し（lifecycleScope 外）
- **共有フロー**: 既存の `setContent` 呼び出しを条件分岐に拡張
- **DB / API**: 不要（ローカルアプリ・プレースホルダー実装のため）

### 残課題
- `Icons.Default.Settings` か `Icons.Outlined.Settings` かはリファクタリング時に決定
- EditScreen の TopAppBar が現時点では存在しない可能性があるため、実装時に確認が必要

### 信頼性レベル分布

**ヒアリング前**:
- 🔵 青信号: 8件
- 🟡 黄信号: 6件
- 🔴 赤信号: 2件

**ヒアリング後**:
- 🔵 青信号: 14件 (+6)
- 🟡 黄信号: 2件 (-4)
- 🔴 赤信号: 0件 (-2)

## 関連文書

- **アーキテクチャ設計**: [architecture.md](architecture.md)
- **データフロー**: [dataflow.md](dataflow.md)
- **インターフェース定義**: [interfaces.kt](interfaces.kt)
- **要件定義**: [requirements.md](../../spec/settings-screen/requirements.md)
