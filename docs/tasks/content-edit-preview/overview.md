# 展開内容の編集・プレビュー機能 タスク概要

**作成日**: 2026-03-29
**推定工数**: 23時間
**総タスク数**: 6件

## 関連文書

- **要件定義書**: [📋 requirements.md](../spec/content-edit-preview/requirements.md)
- **設計文書**: [📐 architecture.md](../design/content-edit-preview/architecture.md)
- **インターフェース定義**: [📝 interfaces.kt](../design/content-edit-preview/interfaces.kt)
- **データフロー図**: [🔄 dataflow.md](../design/content-edit-preview/dataflow.md)
- **コンテキストノート**: [📝 note.md](../spec/content-edit-preview/note.md)
- **既存タスク概要（基盤）**: [📋 share-content-expansion/overview.md](../tasks/share-content-expansion/overview.md)

## フェーズ構成

| フェーズ | 成果物 | タスク数 | 工数 |
|---------|--------|----------|------|
| Phase 1 - 基盤実装 | NoteConfig/NoteComposer・EditFormState/parseTagsText・strings.xml | 3 | 8h |
| Phase 2 - UI・統合実装 | EditScreenViewModel・EditScreen・MainActivity変更 | 3 | 15h |

## タスク番号管理

**使用済みタスク番号（share-content-expansion）**: TASK-0001 〜 TASK-0014
**本機能タスク番号**: TASK-0015 〜 TASK-0020
**次回開始番号**: TASK-0021

## 全体進捗

- [x] Phase 1: 基盤実装
  - [x] [TASK-0015: NoteConfig + NoteComposer 実装](TASK-0015.md)
  - [x] [TASK-0016: EditFormState + parseTagsText + SendParams 実装](TASK-0016.md)
  - [x] [TASK-0018: strings.xml UI文字列リソース追加](TASK-0018.md)
- [x] Phase 2: UI・統合実装
  - [x] [TASK-0017: EditScreenViewModel 実装](TASK-0017.md)
  - [x] [TASK-0019: EditScreen Composable 実装](TASK-0019.md)
  - [x] [TASK-0020: MainActivity フロー変更](TASK-0020.md)

## マイルストーン

- **M1: 基盤完成**: NoteConfig/NoteComposer/EditFormState/parseTagsText/strings.xml 完成
- **M2: ViewModel完成**: EditScreenViewModel 完成、フォーム状態管理が動作
- **M3: UI完成**: EditScreen Composable 完成、フォーム表示・入力が動作
- **M4: 統合完成**: MainActivity フロー変更完了、全コンテンツタイプで編集画面が表示される

---

## Phase 1: 基盤実装

**目標**: フォーマット層の新規クラスとデータクラスを実装し、UI 文字列を準備する
**成果物**: `NoteConfig.kt`, `NoteComposer.kt`, `EditFormState.kt`, `SendParams.kt`, `strings.xml`（更新）

### タスク一覧

- [ ] [TASK-0015: NoteConfig + NoteComposer 実装](TASK-0015.md) - 4h (TDD) 🔵
- [ ] [TASK-0016: EditFormState + parseTagsText + SendParams 実装](TASK-0016.md) - 3h (TDD) 🔵
- [ ] [TASK-0018: strings.xml UI文字列リソース追加](TASK-0018.md) - 1h (DIRECT) 🔵

### 依存関係

```
TASK-0015 ─────────────────────────────→ TASK-0020
TASK-0016 ──→ TASK-0017 ──→ TASK-0019 ──→ TASK-0020
TASK-0018 ──────────────→ TASK-0019
```

TASK-0015・TASK-0016・TASK-0018 は互いに独立しており並列実行可能。

---

## Phase 2: UI・統合実装

**目標**: ViewModel と EditScreen を実装し、MainActivity のフローを編集画面経由に変更する
**成果物**: `EditScreenViewModel.kt`, `EditScreen.kt`, `MainActivity.kt`（変更）

### タスク一覧

- [ ] [TASK-0017: EditScreenViewModel 実装](TASK-0017.md) - 5h (TDD) 🔵
- [ ] [TASK-0019: EditScreen Composable 実装](TASK-0019.md) - 6h (TDD) 🔵
- [ ] [TASK-0020: MainActivity フロー変更](TASK-0020.md) - 4h (TDD) 🔵

### 依存関係

```
TASK-0016 → TASK-0017
TASK-0017 ┐
TASK-0018 ┤→ TASK-0019
           ↓
TASK-0015 ┐
TASK-0019 ┤→ TASK-0020
```

---

## 全タスクの依存関係グラフ

```
Phase 1:
  TASK-0015 (NoteConfig + NoteComposer)
  TASK-0016 (EditFormState + parseTagsText)
  TASK-0018 (strings.xml)

Phase 2:
  TASK-0016 → TASK-0017 (EditScreenViewModel)
  TASK-0017 → TASK-0019 (EditScreen)
  TASK-0018 → TASK-0019
  TASK-0015 → TASK-0020 (MainActivity変更)
  TASK-0019 → TASK-0020
```

**クリティカルパス**: TASK-0016 → TASK-0017 → TASK-0019 → TASK-0020（17h）

---

## 信頼性レベルサマリー

### 全タスク統計

- **総タスク数**: 6件
- 🔵 **青信号**: 6件 (100%)
- 🟡 **黄信号**: 0件 (0%)
- 🔴 **赤信号**: 0件 (0%)

### フェーズ別信頼性

| フェーズ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| Phase 1 | 3 | 0 | 0 | 3 |
| Phase 2 | 3 | 0 | 0 | 3 |
| **合計** | 6 | 0 | 0 | 6 |

**品質評価**: 高品質

---

## 次のステップ

タスクを実装するには:

```
# 全タスクを順番に実装
/tsumiki:kairo-implement

# 特定タスクを実装
/tsumiki:kairo-implement TASK-0015
```

### 推奨実装順序

1. TASK-0015 + TASK-0016 + TASK-0018（並列可）
2. TASK-0017（TASK-0016 完了後）
3. TASK-0019（TASK-0017 + TASK-0018 完了後）
4. TASK-0020（TASK-0015 + TASK-0019 完了後）
