# LLMによるメモ更改機能の追加 タスク概要

**作成日**: 2026-07-05
**プロジェクト期間**: Day 1 - Day 17（17営業日想定）
**推定工数**: 104時間
**総タスク数**: 19件

## 関連文書

- **要件定義書**: [📋 requirements.md](../../spec/llm-memo-rewrite/requirements.md)
- **設計文書**: [📐 architecture.md](../../design/llm-memo-rewrite/architecture.md)
- **データフロー図**: [🔄 dataflow.md](../../design/llm-memo-rewrite/dataflow.md)
- **インターフェース定義**: [📝 interfaces.kt](../../design/llm-memo-rewrite/interfaces.kt)
- **DBスキーマ**: [🗄️ database-schema.kt](../../design/llm-memo-rewrite/database-schema.kt)
- **外部API連携仕様**: [🔌 api-endpoints.md](../../design/llm-memo-rewrite/api-endpoints.md)
- **受け入れ基準**: [✅ acceptance-criteria.md](../../spec/llm-memo-rewrite/acceptance-criteria.md)
- **ユーザストーリー**: [📖 user-stories.md](../../spec/llm-memo-rewrite/user-stories.md)
- **コンテキストノート**: [📝 note.md](../../spec/llm-memo-rewrite/note.md)

## フェーズ構成

| フェーズ | 期間 | 成果物 | タスク数 | 工数 | タスク番号 |
|---------|------|--------|----------|------|----------|
| Phase 1 | Day 1-3 | 依存関係・ドメインモデル・DB Migration・LLM設定Repository | 4件 | 21h | TASK-0055〜0058 |
| Phase 2 | Day 4-5 | LLM呼び出しロジック・DI設定 | 3件 | 15h | TASK-0059〜0061 |
| Phase 3 | Day 6-8 | 本文リライトUI（Must Have） | 4件 | 20h | TASK-0062〜0065 |
| Phase 4 | Day 9-10 | LLM設定UI | 2件 | 10h | TASK-0066〜0067 |
| Phase 5 | Day 11-12 | タグ提案（Should Have） | 2件 | 10h | TASK-0068〜0069 |
| Phase 6 | Day 13-15 | カスタムフィールドLLM生成（Could Have） | 3件 | 20h | TASK-0070〜0072 |
| Phase 7 | Day 16-17 | 統合・品質確認 | 1件 | 8h | TASK-0073 |

## タスク番号管理

**使用済みタスク番号**: TASK-0001 〜 TASK-0073
**次回開始番号**: TASK-0074

## 全体進捗

- [x] Phase 1: 依存関係追加・ドメインモデル変更・DB Migration・LLM設定Repository
- [x] Phase 2: LLM呼び出しロジック（Ktor Client・エラーハンドリング）・DI設定
- [x] Phase 3: 本文リライトUI（EditScreen「メモを更改」ボタン、Must Have）
- [x] Phase 4: SettingsScreen LLM設定UI
- [x] Phase 5: タグ提案UI（Should Have）
- [x] Phase 6: カスタムフィールドLLM生成UI（Could Have）
- [x] Phase 7: E2E統合テスト・最終品質確認

## マイルストーン

- **M1: 基盤完成** (Day 3): ドメインモデル変更 + Room Migration(2→3) + LlmSettingsRepository動作確認
- **M2: LLM呼び出しロジック完成** (Day 5): LlmRewriteRepository（Ktor Client）+ Hilt DI設定完了
- **M3: 本文リライトMust Have完成** (Day 8): EditScreen上で「メモを更改」ボタンによる本文リライトが動作
- **M4: LLM設定UI完成** (Day 10): SettingsScreenでAPIエンドポイント・APIキー・モデル名を設定可能
- **M5: タグ提案完成** (Day 12): 「タグを提案」ボタンによるタグ候補生成が動作
- **M6: カスタムフィールドLLM生成完成** (Day 15): テンプレート編集画面でLLM生成フィールドを設定・生成可能
- **M7: リリース準備完了** (Day 17): 全JVM/計装テスト合格・acceptance-criteria.md全項目充足・debug/releaseビルド成功

---

## Phase 1: 基盤構築

**期間**: Day 1-3
**目標**: LLM機能追加に必要な依存関係・ドメインモデル・DBスキーマ・LLM設定Repositoryを整備する
**成果物**: 変更後Template/TemplateField/FieldValueSource、TemplateEntity/AppDatabase(v3)、LlmSettingsRepository一式

### タスク一覧

- [x] [TASK-0055: 依存関係追加とプロジェクト設定](TASK-0055.md) - 3h (DIRECT) 🔵 ✅ 完了 (2026-07-05)
- [x] [TASK-0056: Template/TemplateField/FieldValueSource ドメインモデル変更](TASK-0056.md) - 4h (TDD) 🔵 ✅ 完了 (2026-07-06)
- [x] [TASK-0057: TemplateEntity/TemplateFieldEntity/AppDatabase Migration(2→3)・Repositoryマッピング修正](TASK-0057.md) - 6h (TDD) 🔵 ✅ 完了 (2026-07-06)
- [x] [TASK-0058: LlmSettings・LlmSettingsRepository・LlmSettingsRepositoryImpl実装](TASK-0058.md) - 8h (TDD) 🔵 ✅ 完了 (2026-07-06)

### 依存関係

```
TASK-0055 → TASK-0056
TASK-0055 → TASK-0058
TASK-0056 → TASK-0057
```

---

## Phase 2: LLM呼び出しロジック・DI設定

**期間**: Day 4-5
**目標**: OpenAI互換Chat Completions APIを呼び出すLLMクライアント層を実装する
**成果物**: LlmRewriteResult/DTO、LlmRewriteRepository（Ktor実装）、LlmModule（Hilt）

### タスク一覧

- [x] [TASK-0059: LlmRewriteResult・ChatCompletion DTO実装](TASK-0059.md) - 4h (TDD) 🔵 ✅ **完了** (TDD開発完了 - 14テストケース全通過)
- [x] [TASK-0060: LlmRewriteRepository・LlmRewriteRepositoryImpl実装](TASK-0060.md) - 8h (TDD) 🔵 ✅ **完了**（TDD開発完了 - ユニットテスト13件全通過、計装テストIT-01/IT-02はコンパイル確認のみ）
- [x] [TASK-0061: LlmModule Hilt DI設定](TASK-0061.md) - 3h (DIRECT) 🔵 ✅ 完了 (2026-07-06)

### 依存関係

```
TASK-0055 → TASK-0059
TASK-0059 → TASK-0060
TASK-0058 → TASK-0061
TASK-0060 → TASK-0061
```

---

## Phase 3: 本文リライトUI（Must Have）

**期間**: Day 6-8
**目標**: 共有/取得直後の元コンテンツ（ProcessedContent）を入力としたLLM本文リライト機能をEditScreenに実装する
**成果物**: MainActivity/EditScreenViewModel/EditScreen変更、strings.xml追加

### タスク一覧

- [x] [TASK-0062: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加](TASK-0062.md) - 4h (TDD) 🔵 ✅ **完了** (TDD開発完了 - 8テストケース全通過)
- [x] [TASK-0063: EditScreenViewModel Hilt化・rewriteBody()実装](TASK-0063.md) - 8h (TDD) 🔵 ✅ **完了** (TDD開発完了 - 14テストケース全通過)
- [x] [TASK-0064: strings.xml 新規文字列追加](TASK-0064.md) - 2h (DIRECT) 🔵 ✅ 完了 (2026-07-06)
- [x] [TASK-0065: EditScreen UI「メモを更改」ボタン・ローディング・エラーToast実装](TASK-0065.md) - 6h (TDD) 🔵 ✅ **完了**（ユニットテスト240件全成功、計装テストはコンパイル確認のみ）

### 依存関係

```
TASK-0057 → TASK-0062
TASK-0061 → TASK-0063
TASK-0062 → TASK-0063
TASK-0063 → TASK-0065
TASK-0064 → TASK-0065
```

---

## Phase 4: LLM設定UI

**期間**: Day 9-10
**目標**: SettingsScreenでLLM API接続情報（エンドポイントURL・APIキー・モデル名）を設定可能にする
**成果物**: SettingsViewModel/SettingsScreen変更

### タスク一覧

- [x] [TASK-0066: SettingsViewModel LLM設定対応](TASK-0066.md) - 4h (TDD) 🔵 ✅ **完了** (TDD開発完了 - 10テストケース全通過)
- [x] [TASK-0067: SettingsScreen LLM設定入力欄追加](TASK-0067.md) - 6h (TDD) 🟡 ✅ **完了**

### 依存関係

```
TASK-0058 → TASK-0066
TASK-0061 → TASK-0066
TASK-0066 → TASK-0067
```

---

## Phase 5: タグ提案（Should Have）

**期間**: Day 11-12
**目標**: 本文リライトと同一の元コンテンツを入力としたタグ候補提案機能を実装する
**成果物**: EditScreenViewModel.suggestTags()、EditScreen「タグを提案」ボタン

### タスク一覧

- [x] [TASK-0068: EditScreenViewModel suggestTags()実装](TASK-0068.md) - 6h (TDD) 🔵 ✅ **完了** (TDD開発完了 - 7テストケース全通過)
- [x] [TASK-0069: EditScreen「タグを提案」ボタンUI追加](TASK-0069.md) - 4h (TDD) 🔵 ✅ **完了** (TDD開発完了 - 6テストケース全通過)

### 依存関係

```
TASK-0063 → TASK-0068
TASK-0068 → TASK-0069
TASK-0064 → TASK-0069
```

---

## Phase 6: カスタムフィールドのLLM生成（Could Have）

**期間**: Day 13-15
**目標**: テンプレートのカスタムフィールドにLLM生成ソースを追加し、EditScreen上でフィールドごとに値を生成できるようにする
**成果物**: CustomFieldState拡張、TemplateEditScreen/FieldAddDialog LLM UI、EditScreenViewModel.generateCustomFieldValue()

### タスク一覧

- [x] [TASK-0070: CustomFieldState拡張・TemplateApplicator.buildCustomFields()のLLM対応](TASK-0070.md) - 6h (TDD) 🟡 ✅ **完了** (TDD開発完了 - 8テストケース全通過)
- [x] [TASK-0071: TemplateEditViewModel/TemplateEditScreen/FieldAddDialog へのLLM UI追加](TASK-0071.md) - 8h (TDD) 🔵 ✅ **完了** (TDD開発完了 - 12テストケース全通過)
- [x] [TASK-0072: EditScreenViewModel generateCustomFieldValue()・EditScreen UI「生成」ボタン追加](TASK-0072.md) - 6h (TDD) 🟡 ✅ **完了** (TDD開発完了 - 13テストケース全通過・全スイート284件成功)

### 依存関係

```
TASK-0056 → TASK-0070
TASK-0057 → TASK-0070
TASK-0056 → TASK-0071
TASK-0057 → TASK-0071
TASK-0070 → TASK-0072
TASK-0063 → TASK-0072
TASK-0071 → TASK-0072
```

---

## Phase 7: 統合・品質確認

**期間**: Day 16-17
**目標**: 全機能のE2E統合テストと最終品質確認を実施する
**成果物**: 統合テスト結果、debug/releaseビルド成功

### タスク一覧

- [x] [TASK-0073: E2E統合テスト・最終品質確認](TASK-0073.md) - 8h (TDD) 🟡 ✅ **完了** (2026-07-10: ユニット284件・計装75件全合格、lint/assembleDebug/assembleRelease成功、acceptance-criteria全27ケース合格)

### 依存関係

```
TASK-0065 → TASK-0073
TASK-0067 → TASK-0073
TASK-0069 → TASK-0073
TASK-0072 → TASK-0073
```

---

## 信頼性レベルサマリー

### 全タスク統計

- **総タスク数**: 19件
- 🔵 **青信号**: 16件 (84%)
- 🟡 **黄信号**: 3件 (16%)（TASK-0070: カスタムフィールドの設計拡張、TASK-0072: 生成タイミングの設計判断、TASK-0073: E2E統合テスト）
- 🔴 **赤信号**: 0件 (0%)

### フェーズ別信頼性（項目単位集計）

| フェーズ | 🔵 青 | 🟡 黄 | 合計 |
|---------|-------|-------|------|
| Phase 1 | 58 | 7 | 65 |
| Phase 2 | 38 | 10 | 48 |
| Phase 3 | 62 | 2 | 64 |
| Phase 4 | 28 | 8 | 36 |
| Phase 5 | 32 | 2 | 34 |
| Phase 6 | 40 | 13 | 53 |
| Phase 7 | 1 | 16 | 17 |
| **合計** | **259** | **58** | **317** |

**全体の信頼性レベル分布（全タスクファイルの実装詳細・テスト要件・UI/UX要件を項目単位で集計）**:
- 🔵 青信号: 259件 (82%)
- 🟡 黄信号: 58件 (18%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: ✅ 高品質

## クリティカルパス

```
TASK-0055 → TASK-0059 → TASK-0060 → TASK-0061 → TASK-0063 → TASK-0068 → TASK-0069 → TASK-0073
```

**クリティカルパス工数**: 44時間（約6営業日）
**並行作業可能工数**: 60時間（ドメインモデル/DB系統(TASK-0056,0057,0062)とLLMクライアント系統(TASK-0059,0060,0061)、および設定UI(Phase4)・カスタムフィールド(Phase6)の一部が並行可能）

## 次のステップ

タスクを実装するには:
- 全タスク順番に実装: `/tsumiki:kairo-implement`
- 特定タスクを実装: `/tsumiki:kairo-implement TASK-0055`
