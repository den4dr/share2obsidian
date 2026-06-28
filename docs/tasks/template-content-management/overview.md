# テンプレートの管理内容の変更 タスク概要

**作成日**: 2026-06-27
**プロジェクト期間**: 2026-06-27 - 2026-07-10（10営業日想定）
**推定工数**: 82時間
**総タスク数**: 12件

## 関連文書

- **要件定義書**: [📋 requirements.md](../../spec/template-content-management/requirements.md)
- **設計文書**: [📐 architecture.md](../../design/template-content-management/architecture.md)
- **データフロー図**: [🔄 dataflow.md](../../design/template-content-management/dataflow.md)
- **インターフェース定義**: [📝 interfaces.kt](../../design/template-content-management/interfaces.kt)
- **DB スキーマ**: [🗄️ database-schema.kt](../../design/template-content-management/database-schema.kt)
- **受け入れ基準**: [✅ acceptance-criteria.md](../../spec/template-content-management/acceptance-criteria.md)
- **ユーザストーリー**: [📖 user-stories.md](../../spec/template-content-management/user-stories.md)
- **コンテキストノート**: [📝 note.md](../../spec/template-content-management/note.md)

## フェーズ構成

| フェーズ | 期間 | 成果物 | タスク数 | 工数 | タスク番号 |
|---------|------|--------|----------|------|----------|
| Phase 1 | Day 1-4 | ドメイン・永続化基盤（Template/Room/DataStore） | 4件 | 24h | TASK-0043〜0046 |
| Phase 2 | Day 5 | テンプレート適用ロジック | 1件 | 8h | TASK-0047 |
| Phase 3 | Day 6-7 | 編集画面 UI | 2件 | 14h | TASK-0048〜0049 |
| Phase 4 | Day 7-9 | テンプレート管理・設定 UI | 3件 | 22h | TASK-0050〜0052 |
| Phase 5 | Day 9-10 | 統合・品質確認 | 2件 | 14h | TASK-0053〜0054 |

## タスク番号管理

**使用済みタスク番号**: TASK-0001 〜 TASK-0054
**次回開始番号**: TASK-0055

## 全体進捗

- [x] Phase 1: ドメイン・永続化基盤（Template モデル変更 + Room Migration + DataStore）
- [x] Phase 2: テンプレート適用ロジック（buildConfig/buildBody）
- [x] Phase 3: 編集画面 UI（状態拡張 + 表示順変更）
- [x] Phase 4: テンプレート管理・設定 UI
- [x] Phase 5: 統合・最終品質確認（JVM/計器テスト全合格・debug/release ビルド成功）

## マイルストーン

- **M1: 永続化基盤完成** (Day 4): Template モデル変更 + Room v1→v2 Migration + DataStore 動作確認
- **M2: 適用ロジック完成** (Day 5): `{{content}}` プレースホルダー解決・DataStore 由来 config
- **M3: 編集画面完成** (Day 7): EditScreen 表示順変更 + vault/folder 入力欄
- **M4: 管理・設定 UI 完成** (Day 9): TemplateEditScreen body 入力 + SettingsScreen vault/folder
- **M5: リリース準備完了** (Day 10): MainActivity 統合 + 全テスト通過 + リリースビルド成功

---

## Phase 1: ドメイン・永続化基盤

**期間**: Day 1-4
**目標**: Template モデルのスリム化、Room マイグレーション、DataStore 設定基盤の導入
**成果物**: 変更後 Template/Entity/AppDatabase、NoteSettings 一式

### タスク一覧

- [x] [TASK-0043: DataStore・Room-testing 依存関係追加とプロジェクト設定](TASK-0043.md) - 4h (DIRECT) 🔵
- [x] [TASK-0044: Template ドメインモデル変更（vault/folder 削除・body 追加）](TASK-0044.md) - 4h (TDD) 🔵
- [x] [TASK-0045: TemplateEntity・AppDatabase Migration(1→2)・Repository マッピング修正](TASK-0045.md) - 8h (TDD) 🔵
- [x] [TASK-0046: DataStore NoteSettings・Repository・Hilt Module 実装](TASK-0046.md) - 8h (TDD) 🔵

### 依存関係

```
TASK-0043 → TASK-0045
TASK-0043 → TASK-0046
TASK-0044 → TASK-0045
```

---

## Phase 2: テンプレート適用ロジック

**期間**: Day 5
**目標**: テンプレートの内容適用（vault/folder の DataStore 由来化、`{{content}}` 解決）
**成果物**: TemplateApplicator.buildConfig/buildBody

### タスク一覧

- [x] [TASK-0047: TemplateApplicator buildConfig/buildBody 実装（{{content}} 解決）](TASK-0047.md) - 8h (TDD) 🔵

### 依存関係

```
TASK-0044 → TASK-0047
TASK-0046 → TASK-0047
```

---

## Phase 3: 編集画面 UI

**期間**: Day 6-7
**目標**: EditScreen での vault/folder 編集と表示順変更
**成果物**: EditFormState/EditScreenViewModel 拡張、EditScreen UI

### タスク一覧

- [x] [TASK-0048: EditFormState・SendParams・EditScreenViewModel vault 対応](TASK-0048.md) - 6h (TDD) 🔵
- [x] [TASK-0049: EditScreen 表示順変更・vault/folder 入力欄追加](TASK-0049.md) - 8h (TDD) 🔵

### 依存関係

```
TASK-0048 → TASK-0049
```

---

## Phase 4: テンプレート管理・設定 UI

**期間**: Day 7-9
**目標**: TemplateEditScreen の簡素化と本文テンプレート入力、SettingsScreen の vault/folder 設定
**成果物**: TemplateEditViewModel/Screen 変更、SettingsViewModel/Screen

### タスク一覧

- [x] [TASK-0050: TemplateEditViewModel・TemplateEditUiState body 対応](TASK-0050.md) - 6h (TDD) 🔵
- [x] [TASK-0051: TemplateEditScreen vault/folder UI 削除・body 入力エリア追加](TASK-0051.md) - 8h (TDD) 🔵
- [x] [TASK-0052: SettingsViewModel 新規・SettingsScreen vault/folder 入力欄追加](TASK-0052.md) - 8h (TDD) 🔵

### 依存関係

```
TASK-0044 → TASK-0050
TASK-0050 → TASK-0051
TASK-0046 → TASK-0052
```

---

## Phase 5: 統合・品質確認

**期間**: Day 9-10
**目標**: MainActivity 統合と E2E 品質確認
**成果物**: MainActivity 統合、統合テスト結果、リリースビルド

### タスク一覧

- [x] [TASK-0053: MainActivity テンプレート適用・DataStore 統合](TASK-0053.md) - 6h (TDD) 🔵
- [x] [TASK-0054: E2E 統合テスト・最終品質確認](TASK-0054.md) - 8h (TDD) 🟡

### 依存関係

```
TASK-0045 → TASK-0053
TASK-0046 → TASK-0053
TASK-0047 → TASK-0053
TASK-0048 → TASK-0053
TASK-0049 → TASK-0054
TASK-0051 → TASK-0054
TASK-0052 → TASK-0054
TASK-0053 → TASK-0054
```

---

## 信頼性レベルサマリー

### 全タスク統計

- **総タスク数**: 12件
- 🔵 **青信号**: 11件 (92%)
- 🟡 **黄信号**: 1件 (8%)（TASK-0054: E2E 統合テスト）
- 🔴 **赤信号**: 0件 (0%)

### フェーズ別信頼性

| フェーズ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| Phase 1 | 4 | 0 | 0 | 4 |
| Phase 2 | 1 | 0 | 0 | 1 |
| Phase 3 | 2 | 0 | 0 | 2 |
| Phase 4 | 3 | 0 | 0 | 3 |
| Phase 5 | 1 | 1 | 0 | 2 |

**品質評価**: ✅ 高品質

## クリティカルパス

```
TASK-0043
  → TASK-0046
    → TASK-0047
      → TASK-0053
        → TASK-0054
```

**クリティカルパス工数**: 34時間（約5営業日）
**並行作業可能工数**: 48時間（Phase 1 の Template モデル/UI 系タスクの一部が並行可能）

## 次のステップ

タスクを実装するには:
- 全タスク順番に実装: `/tsumiki:kairo-implement`
- 特定タスクを実装: `/tsumiki:kairo-implement TASK-0043`
