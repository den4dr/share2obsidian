# 編集テンプレートの管理機能 タスク概要

**作成日**: 2026-06-01
**プロジェクト期間**: 2026-06-01 - 2026-06-20（20日）
**推定工数**: 122時間
**総タスク数**: 18件

## 関連文書

- **要件定義書**: [📋 requirements.md](../spec/edit-template-management/requirements.md)
- **設計文書**: [📐 architecture.md](../design/edit-template-management/architecture.md)
- **データフロー**: [🔄 dataflow.md](../design/edit-template-management/dataflow.md)
- **DB スキーマ**: [🗄️ database-schema.kt](../design/edit-template-management/database-schema.kt)
- **インターフェース定義**: [📝 interfaces.kt](../design/edit-template-management/interfaces.kt)
- **コンテキストノート**: [📝 note.md](../spec/edit-template-management/note.md)

## フェーズ構成

| フェーズ | 期間 | 成果物 | タスク数 | 工数 | タスク番号 |
|---------|------|--------|----------|------|----------|
| Phase 1 | Day 1-3 | Hilt/Room/Repository 基盤 | 4件 | 26h | TASK-0025〜0028 |
| Phase 2 | Day 4-9 | テンプレート管理 UI | 6件 | 42h | TASK-0029〜0034 |
| Phase 3 | Day 10-12 | HTML メタデータ抽出 | 3件 | 18h | TASK-0035〜0037 |
| Phase 4 | Day 13-16 | 既存機能統合 | 4件 | 28h | TASK-0038〜0041 |
| Phase 5 | Day 17-18 | 統合テスト・品質確認 | 1件 | 8h | TASK-0042 |

## タスク番号管理

**使用済みタスク番号**: TASK-0025 〜 TASK-0042
**次回開始番号**: TASK-0043

## 全体進捗

- [ ] Phase 1: 基盤構築（Hilt + Room + Repository）
- [ ] Phase 2: テンプレート管理 UI（CRUD 画面）
- [ ] Phase 3: HTML メタデータ抽出
- [ ] Phase 4: 既存機能統合（EditScreen + NoteComposer + MainActivity）
- [ ] Phase 5: 統合テスト・最終品質確認

## マイルストーン

- **M1: 基盤完成** (Day 3): Room DB + Hilt DI + Repository 動作確認
- **M2: テンプレート管理 UI 完成** (Day 9): CRUD 画面 + ナビゲーション動作確認
- **M3: メタデータ抽出完成** (Day 12): WebView/Jsoup メタデータ取得動作確認
- **M4: 統合完成** (Day 16): EditScreen カスタムフィールド + テンプレート適用動作確認
- **M5: リリース準備完了** (Day 18): 全テスト通過・リリースビルド成功

---

## Phase 1: 基盤構築

**期間**: Day 1-3
**目標**: Hilt DI・Room DB・Repository パターンの導入
**成果物**: DI 設定、DB スキーマ、Repository 実装

### タスク一覧

- [x] [TASK-0025: Hilt・KSP・Room 依存関係追加とプロジェクト設定](TASK-0025.md) - 4h (DIRECT) 🔵
- [x] [TASK-0026: ドメインモデル実装](TASK-0026.md) - 6h (TDD) 🔵
- [x] [TASK-0027: Room DB エンティティ・DAO・AppDatabase 実装](TASK-0027.md) - 8h (TDD) 🔵
- [x] [TASK-0028: TemplateRepository・Hilt DI・Application クラス実装](TASK-0028.md) - 8h (TDD) 🔵

### 依存関係

```
TASK-0025 → TASK-0026
TASK-0025 → TASK-0027
TASK-0026 → TASK-0027
TASK-0026 → TASK-0028
TASK-0027 → TASK-0028
```

---

## Phase 2: テンプレート管理 UI

**期間**: Day 4-9
**目標**: テンプレートの CRUD 操作 UI とナビゲーション実装
**成果物**: TemplateListScreen、TemplateEditScreen、SettingsScreen/MainActivity 統合

### タスク一覧

- [x] [TASK-0029: TemplateListViewModel 実装](TASK-0029.md) - 4h (TDD) 🔵
- [x] [TASK-0030: TemplateListScreen 実装](TASK-0030.md) - 8h (TDD) 🔵
- [x] [TASK-0031: TemplateEditViewModel 実装](TASK-0031.md) - 8h (TDD) 🔵
- [ ] [TASK-0032: TemplateEditScreen 基本情報 UI 実装](TASK-0032.md) - 8h (TDD) 🔵
- [ ] [TASK-0033: TemplateEditScreen フィールド管理 UI 実装](TASK-0033.md) - 8h (TDD) 🔵
- [ ] [TASK-0034: SettingsScreen・MainActivity ナビゲーション統合](TASK-0034.md) - 6h (TDD) 🔵

### 依存関係

```
TASK-0028 → TASK-0029
TASK-0029 → TASK-0030
TASK-0028 → TASK-0031
TASK-0031 → TASK-0032
TASK-0032 → TASK-0033
TASK-0030 → TASK-0034
TASK-0033 → TASK-0034
```

---

## Phase 3: HTML メタデータ抽出

**期間**: Day 10-12（Phase 2 と並行可能）
**目標**: URL 共有時の WebView メタデータ取得、HTML 共有時の Jsoup メタデータ抽出
**成果物**: ProcessedContent 拡張、WebViewExtractor JS 拡張、HtmlContentProcessor 拡張

### タスク一覧

- [ ] [TASK-0035: ProcessedContent・WebViewExtractionResult モデル拡張](TASK-0035.md) - 4h (TDD) 🔵
- [ ] [TASK-0036: WebViewExtractor JS 拡張 + UrlContentProcessor メタデータ設定](TASK-0036.md) - 8h (TDD) 🔵
- [ ] [TASK-0037: HtmlContentProcessor Jsoup メタデータ抽出実装](TASK-0037.md) - 6h (TDD) 🔵

### 依存関係

```
TASK-0026 → TASK-0035
TASK-0035 → TASK-0036
TASK-0035 → TASK-0037
```

---

## Phase 4: 既存機能統合

**期間**: Day 13-16
**目標**: NoteComposer・EditScreen・MainActivity のカスタムフィールド対応
**成果物**: Frontmatter カスタムフィールド出力、EditScreen UI 拡張、テンプレート適用ロジック

### タスク一覧

- [ ] [TASK-0038: NoteComposer.buildFrontmatter カスタムフィールド対応](TASK-0038.md) - 6h (TDD) 🔵
- [ ] [TASK-0039: EditFormState・SendParams・EditScreenViewModel カスタムフィールド拡張](TASK-0039.md) - 6h (TDD) 🔵
- [ ] [TASK-0040: EditScreen カスタムフィールドセクション UI 実装](TASK-0040.md) - 8h (TDD) 🔵
- [ ] [TASK-0041: MainActivity テンプレート適用ロジック実装](TASK-0041.md) - 8h (TDD) 🔵

### 依存関係

```
TASK-0026 → TASK-0038
TASK-0038 → TASK-0039
TASK-0039 → TASK-0040
TASK-0034 → TASK-0041
TASK-0036 → TASK-0041
TASK-0037 → TASK-0041
TASK-0038 → TASK-0041
```

---

## Phase 5: 統合テスト

**期間**: Day 17-18
**目標**: 全機能の E2E 確認とリリース品質確保
**成果物**: 統合テスト結果、リリースビルド

### タスク一覧

- [ ] [TASK-0042: E2E 統合テスト・最終品質確認](TASK-0042.md) - 8h (TDD) 🟡

### 依存関係

```
TASK-0040 → TASK-0042
TASK-0041 → TASK-0042
```

---

## 信頼性レベルサマリー

### 全タスク統計

- **総タスク数**: 18件
- 🔵 **青信号**: 17件 (94%)
- 🟡 **黄信号**: 1件 (6%)（TASK-0042: 統合テスト）
- 🔴 **赤信号**: 0件 (0%)

### フェーズ別信頼性

| フェーズ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| Phase 1 | 4 | 0 | 0 | 4 |
| Phase 2 | 6 | 0 | 0 | 6 |
| Phase 3 | 3 | 0 | 0 | 3 |
| Phase 4 | 4 | 0 | 0 | 4 |
| Phase 5 | 0 | 1 | 0 | 1 |

**品質評価**: ✅ 高品質

## クリティカルパス

```
TASK-0025
  → TASK-0026
    → TASK-0027
      → TASK-0028
        → TASK-0031
          → TASK-0032
            → TASK-0033
              → TASK-0034
                → TASK-0041
                  → TASK-0042
```

**クリティカルパス工数**: 70時間（約9日）
**並行作業可能工数**: 52時間（Phase 2 の一部と Phase 3 が並行可能）

## 次のステップ

タスクを実装するには:
- 全タスク順番に実装: `/tsumiki:kairo-implement`
- 特定タスクを実装: `/tsumiki:kairo-implement TASK-0025`
