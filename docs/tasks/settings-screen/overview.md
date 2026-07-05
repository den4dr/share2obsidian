# 設定画面 タスク概要

**作成日**: 2026-05-31
**推定工数**: 7.5時間
**総タスク数**: 4件

## 関連文書

- **要件定義書**: [📋 requirements.md](../spec/settings-screen/requirements.md)
- **設計文書**: [📐 architecture.md](../design/settings-screen/architecture.md)
- **インターフェース定義**: [📝 interfaces.kt](../design/settings-screen/interfaces.kt)
- **データフロー図**: [🔄 dataflow.md](../design/settings-screen/dataflow.md)
- **コンテキストノート**: [📝 note.md](../spec/settings-screen/note.md)
- **既存タスク概要（基盤）**: [📋 content-edit-preview/overview.md](../tasks/content-edit-preview/overview.md)

## フェーズ構成

| フェーズ | 成果物 | タスク数 | 工数 |
|---------|--------|----------|------|
| Phase 1 - 基盤実装 | strings.xml 文字列追加 | 1 | 0.5h |
| Phase 2 - UI実装 | SettingsScreen・EditScreen 拡張 | 2 | 4h |
| Phase 3 - 統合実装 | MainActivity ナビゲーション | 1 | 3h |

## タスク番号管理

**使用済みタスク番号（share-content-expansion）**: TASK-0001 〜 TASK-0014
**使用済みタスク番号（content-edit-preview）**: TASK-0015 〜 TASK-0020
**本機能タスク番号**: TASK-0021 〜 TASK-0024
**次回開始番号**: TASK-0025

## 全体進捗

- [x] Phase 1: 基盤実装
  - [x] [TASK-0021: strings.xml 文字列リソース追加](TASK-0021.md)
- [x] Phase 2: UI実装
  - [x] [TASK-0022: SettingsScreen Composable 実装](TASK-0022.md)
  - [x] [TASK-0023: EditScreen ツールバー拡張](TASK-0023.md)
- [x] Phase 3: 統合実装
  - [x] [TASK-0024: MainActivity ナビゲーション実装](TASK-0024.md)

## マイルストーン

- **M1: 文字列リソース完成**: strings.xml 追加完了（TASK-0021）
- **M2: 画面実装完成**: SettingsScreen・EditScreen 拡張完了（TASK-0022, TASK-0023）
- **M3: 統合完成**: MainActivity ナビゲーション完了、全コンテキストで設定画面が動作（TASK-0024）

---

## Phase 1: 基盤実装

**目標**: UI 文字列リソースを準備する
**成果物**: `strings.xml`（更新）

### タスク一覧

- [x] [TASK-0021: strings.xml 文字列リソース追加](TASK-0021.md) - 0.5h (DIRECT) 🔵

### 依存関係

```
TASK-0021 ──→ TASK-0022
           └─→ TASK-0023
```

---

## Phase 2: UI実装

**目標**: SettingsScreen を実装し、EditScreen にツールバーを拡張する
**成果物**: `SettingsScreen.kt`（新規）、`EditScreen.kt`（変更）

### タスク一覧

- [x] [TASK-0022: SettingsScreen Composable 実装](TASK-0022.md) - 2h (TDD) 🔵
- [x] [TASK-0023: EditScreen ツールバー拡張](TASK-0023.md) - 2h (TDD) 🔵

### 依存関係

```
TASK-0021 → TASK-0022 ──→ TASK-0024
TASK-0021 → TASK-0023 ──→ TASK-0024
```

TASK-0022 と TASK-0023 は互いに独立しており並列実行可能。

---

## Phase 3: 統合実装

**目標**: MainActivity のナビゲーションロジックを実装し、全フローを統合する
**成果物**: `MainActivity.kt`（変更）

### タスク一覧

- [x] [TASK-0024: MainActivity ナビゲーション実装](TASK-0024.md) - 3h (TDD) 🔵

### 依存関係

```
TASK-0022 ──→ TASK-0024
TASK-0023 ──→ TASK-0024
```

---

## 全タスクの依存関係グラフ

```
Phase 1:
  TASK-0021 (strings.xml)

Phase 2:
  TASK-0021 → TASK-0022 (SettingsScreen)
  TASK-0021 → TASK-0023 (EditScreen 拡張) ← TASK-0022 と並列可能

Phase 3:
  TASK-0022 ┐
  TASK-0023 ┘→ TASK-0024 (MainActivity)
```

**クリティカルパス**: TASK-0021 → TASK-0022 → TASK-0024（5.5h）

---

## 信頼性レベルサマリー

### 全タスク統計

- **総タスク数**: 4件
- 🔵 **青信号**: 4件 (100%)
- 🟡 **黄信号**: 0件 (0%)
- 🔴 **赤信号**: 0件 (0%)

### フェーズ別信頼性

| フェーズ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| Phase 1 | 1 | 0 | 0 | 1 |
| Phase 2 | 2 | 0 | 0 | 2 |
| Phase 3 | 1 | 0 | 0 | 1 |
| **合計** | 4 | 0 | 0 | 4 |

**品質評価**: 高品質

---

## 次のステップ

タスクを実装するには:

```
# 全タスクを順番に実装
/tsumiki:kairo-implement

# 特定タスクを実装
/tsumiki:kairo-implement TASK-0021
```

### 推奨実装順序

1. TASK-0021（strings.xml）
2. TASK-0022 + TASK-0023（並列可）
3. TASK-0024（MainActivity）
