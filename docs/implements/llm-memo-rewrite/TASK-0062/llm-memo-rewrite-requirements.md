# TASK-0062 TDD要件定義書: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加

**機能名**: llm-memo-rewrite（MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加）
**タスクID**: TASK-0062
**要件名**: llm-memo-rewrite
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0062/llm-memo-rewrite-requirements.md`
**作成日**: 2026-07-06
**フェーズ**: Phase 3 - 本文リライトUI（Must Have）

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: `MainActivity.onCreate()` において、`TemplateApplicator.buildBody()` によるテンプレート適用**前**の元コンテンツ（`processed.body`）を `sourceContent` として別変数に退避し、デフォルトテンプレートの `bodyLlmPrompt` と共に `viewModel.initialize()` の新規引数として `EditScreenViewModel` に受け渡す。あわせて `EditScreenViewModel.initialize()` のシグネチャに `sourceContent` / `bodyLlmPrompt` の2引数を追加する。
- 🔵 **どのような問題を解決するか**: 現状の `MainActivity` は `viewModel.initialize(processed.copy(body = resolvedBody), config, customFields)` で **テンプレート適用後の `resolvedBody`** のみを ViewModel に渡している。このため LLM リライト（REQ-002）に必要な「共有/取得直後の元コンテンツ（テンプレート適用前・ユーザー編集前）」が ViewModel に到達しない。本タスクはこの元コンテンツを保持経路に載せ、REQ-406（元コンテンツ保持）を成立させる。
- 🔵 **想定されるユーザー**: 共有されたテキスト/URL/HTML/ファイルを Obsidian メモとして保存する際に、LLM で本文をリライトしたいエンドユーザー（後続タスクで UI 化）。本タスク単体では内部データ経路の整備であり直接の UI 変化はない。
- 🔵 **システム内での位置づけ**: 共有インテント処理フローの「ProcessedContent 生成 → テンプレート適用 → ViewModel 初期化」の接続点。architecture.md「ProcessedContent保持設計」に基づき、`sourceContent` / `bodyLlmPrompt` は `EditFormState` には含めず、後続 TASK-0063 で ViewModel 内部プライベートプロパティとして `rewriteBody()`/`suggestTags()` の入力に使用される。本タスクは引数追加のみで、ViewModel 内部の保持・利用は TASK-0063 の担当。

- **参照したEARS要件**: REQ-002, REQ-406, REQ-101, REQ-102
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`「ProcessedContent保持設計」「変更が必要な既存コンポーネント」（MainActivity.kt 行81、EditScreenViewModel.kt 行82）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 MainActivity.onCreate() 内の処理（変更対象）

- 🔵 **入力**:
  - `processed: ProcessedContent` — `ContentProcessor.process()` の結果。`body: String`（元コンテンツ）、`title: String?` を含む。
  - `defaultTemplate: Template?` — `templateRepository.getDefaultTemplate()` の結果。`null`（デフォルト未設定）の可能性あり。`bodyLlmPrompt: String = ""` フィールドを持つ（TASK-0056/0057で追加済み）。
- 🔵 **中間算出**:
  - `sourceContent: String = processed.body` — `TemplateApplicator.buildBody()` **呼び出し前**に退避する（テンプレート適用前の値）。
  - `bodyLlmPrompt: String = defaultTemplate?.bodyLlmPrompt.orEmpty()` — `defaultTemplate` が `null` の場合は空文字にフォールバック。
  - `resolvedBody: String = TemplateApplicator.buildBody(defaultTemplate, processed.body)` — テンプレート適用後の値（既存処理）。
- 🔵 **出力（呼び出し）**:
  ```kotlin
  viewModel.initialize(
      processed = processed.copy(body = resolvedBody),
      config = config,
      customFields = customFields,
      sourceContent = sourceContent,   // 新規引数
      bodyLlmPrompt = bodyLlmPrompt,    // 新規引数
  )
  ```

### 2.2 EditScreenViewModel.initialize() シグネチャ（変更対象）

- 🔵 **変更前**:
  ```kotlin
  fun initialize(
      processed: ProcessedContent,
      config: NoteConfig,
      customFields: List<CustomFieldState> = emptyList(),
  )
  ```
- 🔵 **変更後**:
  ```kotlin
  fun initialize(
      processed: ProcessedContent,
      config: NoteConfig,
      customFields: List<CustomFieldState> = emptyList(),
      sourceContent: String = "",   // 新規引数（デフォルト値で後方互換維持）
      bodyLlmPrompt: String = "",    // 新規引数（デフォルト値で後方互換維持）
  )
  ```

### 2.3 入出力の関係性

- 🔵 `sourceContent` は `processed.body`（テンプレート適用前）そのもの。一方 `initialize()` に渡す `processed.copy(body = resolvedBody)` の `body` はテンプレート適用後の値であり、両者は（テンプレートにプレースホルダが含まれる場合）**異なる値**になる。
- 🔵 `bodyLlmPrompt` は `defaultTemplate?.bodyLlmPrompt` を `orEmpty()` で `null` フォールバックした値と一致する。

### 2.4 データフロー（dataflow.mdベース）

🔵 *note.md「参考ドキュメント関連図」・dataflow.md より*

```
MainActivity.onCreate()
  → 1. ContentProcessor.process() → ProcessedContent.body（元コンテンツ）
  → 2. sourceContent = processed.body（テンプレート適用前を退避）
  → 3. resolvedBody = TemplateApplicator.buildBody()（テンプレート適用後）
  → 4. bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty()
  → 5. viewModel.initialize(processed.copy(body=resolvedBody), config, customFields, sourceContent, bodyLlmPrompt)
       → EditScreenViewModel（sourceContent / bodyLlmPrompt を受領。保持・利用は TASK-0063）
```

- **参照したEARS要件**: REQ-002, REQ-406, REQ-101, REQ-102
- **参照した設計文書**: `docs/design/llm-memo-rewrite/interfaces.kt`（EditScreenViewModelSpec）、`docs/design/llm-memo-rewrite/architecture.md`（行113-125）、`docs/design/llm-memo-rewrite/dataflow.md`
- **既存実装**: `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt`（`bodyLlmPrompt: String = ""`）、`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（既存 `initialize()`）

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **後方互換性制約**: `EditScreenViewModel.initialize()` の新規引数は **デフォルト値付き**（`sourceContent: String = ""`, `bodyLlmPrompt: String = ""`）とし、既存の呼び出し・既存テストを破壊しない（additive な変更）。
- 🔵 **ProcessedContent 不変性制約**: `processed` オブジェクト本体の `body` フィールドは上書きしない。`processed.copy(body = resolvedBody)` で新規オブジェクトを生成する既存パターンを踏襲し、`sourceContent` は別変数として保持する。
- 🔵 **null フォールバック制約**: `defaultTemplate` が `null`（デフォルトテンプレート未設定）の場合、`orEmpty()` により `bodyLlmPrompt` を空文字 `""` に統一する（REQ-102 の前提整備。ボタン非活性判定は後続タスク）。例外を発生させない。
- 🔵 **スコープ制約**: 本タスクは「引数追加」と「MainActivity での退避・受け渡し」まで。`sourceContent` / `bodyLlmPrompt` の ViewModel 内プロパティ保持・`rewriteBody()` 等での利用は **TASK-0063** の担当であり本タスクの範囲外。
- 🔵 **アーキテクチャ制約**: 単一アクティビティ + Compose UI + MVVM + Repository。`EditScreenViewModel` は `AndroidX ViewModel` を継承（Hilt 化は TASK-0063）。`initialize()` の `initialized` フラグによる重複初期化防止（EDGE-101）は維持する。
- 🔵 **前提依存**: `Template.bodyLlmPrompt` フィールド（TASK-0056）と DB Migration version 2→3（TASK-0057）が完了していること。両タスクは overview.md で完了済み。
- 🟡 **パフォーマンス/セキュリティ**: 特段の新規制約なし。`bodyLlmPrompt` は平文でメモリ保持され、後続 TASK-0063 で LLM API 通信に使用される（本タスクでは通信は発生しない）。

- **参照したEARS要件**: REQ-101, REQ-102, REQ-406, EDGE-101
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`「ProcessedContent保持設計」、`docs/tasks/llm-memo-rewrite/TASK-0062.md`「実装詳細」「注意事項」

---

## 4. 想定される使用例（EARS Edgeケース・データフローベース）

### 4.1 基本的な使用パターン（正常系）

- 🔵 **ケースA（本文プレースホルダを含むテンプレート）**: `bodyLlmPrompt` 設定済み・本文にプレースホルダを含むデフォルトテンプレートで共有 → `sourceContent` にテンプレート適用前の元コンテンツ、`processed.body` にテンプレート適用後の `resolvedBody` が入り、両者が異なる値になる。（テストケース1に対応）
- 🔵 **ケースB（bodyLlmPrompt 設定済み）**: `bodyLlmPrompt = "要約してください"` のデフォルトテンプレート → `viewModel.initialize()` の `bodyLlmPrompt` 引数が `"要約してください"` と一致する。（テストケース2に対応）

### 4.2 エッジケース

- 🔵 **ケースC（defaultTemplate が null）**: `templateRepository.getDefaultTemplate()` が `null` を返す → `bodyLlmPrompt` 引数が空文字 `""` になり、`NullPointerException` 等の例外が発生しない。（テストケース3に対応、REQ-102/`orEmpty()`）
- 🟡 **ケースD（bodyLlmPrompt が空文字のテンプレート）**: デフォルトテンプレートは存在するが `bodyLlmPrompt = ""` → `bodyLlmPrompt` 引数は `""`。ケースCと同じく後続タスクでボタン非活性となる（本タスクでは値の受け渡しのみ検証）。
- 🟡 **ケースE（sourceContent が空文字）**: `processed.body` が空文字（EDGE-002 空ノート許容）→ `sourceContent = ""` として渡され、ガードせず受け渡す（architecture.md 行210: ガードは `bodyLlmPrompt` の有無のみ、`sourceContent` 空文字はガードしない）。

### 4.3 エラーケース

- 🔵 本タスクの範囲では新規のエラー系フローは追加しない。`defaultTemplate` の null 安全は `orEmpty()` で担保する。LLM 通信エラー等は TASK-0063 以降の担当。

- **参照したEARS要件**: REQ-002, REQ-406, REQ-102, EDGE-002, EDGE-101
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`（行167-210）、`docs/tasks/llm-memo-rewrite/TASK-0062.md`「単体テスト要件」

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: 「共有したメモを LLM でリライトして Obsidian に保存する」（`docs/spec/llm-memo-rewrite/user-stories.md`）
- **参照した機能要件**:
  - REQ-002: 「メモを更改」ボタン押下時、元コンテンツ（テンプレート適用前・ユーザー編集前）とテンプレート本文用プロンプトを LLM API に送信する
  - REQ-101: テンプレートに本文用LLMプロンプトが設定されている場合、そのプロンプトを LLM リクエストに含める
  - REQ-102: 本文用LLMプロンプト未設定時、「メモを更改」ボタンを非活性化（本タスクは空文字フォールバックで前提整備）
- **参照した非機能要件**:
  - REQ-406: LLM 入力用に、EditScreen 表示後も元コンテンツ（ProcessedContent）をテンプレート適用・ユーザー編集と独立して保持し続ける
- **参照したEdgeケース**: EDGE-101（画面回転時の重複初期化防止＝既存 `initialized` フラグ）、EDGE-002（空文字本文の許容）
- **参照した受け入れ基準**: `docs/spec/llm-memo-rewrite/acceptance-criteria.md`（本文リライトの入力ソース検証）
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`「ProcessedContent保持設計」「変更が必要な既存コンポーネント」（行72-125, 167-210）
  - **データフロー**: `docs/design/llm-memo-rewrite/dataflow.md`（本文リライトフロー）
  - **型定義**: `docs/design/llm-memo-rewrite/interfaces.kt`（EditScreenViewModelSpec）
  - **データベース**: `docs/design/llm-memo-rewrite/database-schema.kt`（TemplateEntity.bodyLlmPrompt、Migration 2→3）
- **実装ファイル**:
  - `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt`（onCreate 内 行100-107 付近）
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（initialize 行58-62 付近）
  - `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt`（bodyLlmPrompt、前提）
- **テストファイル（想定）**:
  - `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt` 等の Robolectric テスト

---

## 6. 単体テスト要件（受け入れ基準の対応）

| ID | Given | When | Then | 信頼性 |
|----|-------|------|------|--------|
| TC-001 | 本文にプレースホルダを含むデフォルトテンプレートが設定され、`EXTRA_TEXT` を含む `ACTION_SEND` 共有 | `MainActivity` を Robolectric で起動 | `initialize()` の `sourceContent` 引数が `processed.body`（テンプレート適用前）と一致し、`processed`（`resolvedBody` 含む）の `body` とは異なる | 🔵 |
| TC-002 | `bodyLlmPrompt = "要約してください"` のデフォルトテンプレートが存在 | `MainActivity` を Robolectric で起動 | `initialize()` の `bodyLlmPrompt` 引数が `"要約してください"` と一致 | 🔵 |
| TC-003 | デフォルトテンプレート未設定（`getDefaultTemplate()` が `null`） | `MainActivity` を Robolectric で起動 | `initialize()` の `bodyLlmPrompt` 引数が空文字 `""` になり、例外が発生しない | 🔵 |
| 回帰 | 既存の MainActivity / EditScreenViewModel テスト群 | 各既存テスト実行 | すべて合格（後方互換性維持） | 🔵 |

---

## 7. 品質判定

```
✅ 高品質:
- 要件の曖昧さ: なし（入出力・シグネチャが設計文書・note.md に具体的に定義済み）
- 入出力定義: 完全（MainActivity 中間算出・initialize() シグネチャ・データフローを明記）
- 制約条件: 明確（後方互換=デフォルト引数、null フォールバック、スコープ境界を明記）
- 実装可能性: 確実（前提 TASK-0056/0057 完了済み、変更は additive）
- 信頼性レベル: 🔵 が大半
```

### 信頼性レベル分布

| セクション | 🔵 青 | 🟡 黄 | 🔴 赤 |
|-----------|-------|-------|-------|
| 1. 機能概要 | 4 | 0 | 0 |
| 2. 入出力仕様 | 6 | 0 | 0 |
| 3. 制約条件 | 6 | 1 | 0 |
| 4. 使用例 | 4 | 2 | 0 |
| 6. テスト要件 | 4 | 0 | 0 |
| **合計** | **24** | **3** | **0** |

- 🔵 青信号: 24件（89%）
- 🟡 黄信号: 3件（11%）
- 🔴 赤信号: 0件（0%）

**総合品質評価**: ✅ 高品質

---

**次のお勧めステップ**: `/tsumiki:tdd-testcases llm-memo-rewrite TASK-0062` でテストケースの洗い出しを行います。
