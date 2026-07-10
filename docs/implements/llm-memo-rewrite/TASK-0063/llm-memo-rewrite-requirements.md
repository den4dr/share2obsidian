# TDD要件定義書: EditScreenViewModel Hilt化・rewriteBody()実装

**機能名**: llm-memo-rewrite（本文リライトUI）
**タスクID**: TASK-0063
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0063/llm-memo-rewrite-requirements.md`

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測をしている
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測をしている

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: `EditScreenViewModel` を素の `ViewModel()` から `@HiltViewModel` 化し、`LlmRewriteRepository` と `LlmSettingsRepository` をコンストラクタ注入する。共有/取得直後の元コンテンツ（`sourceContent`）とテンプレートの本文用プロンプト（`bodyLlmPrompt`）をプライベート状態として保持し、`rewriteBody()` メソッドで LLM API を呼び出して本文フィールドを書き換える。エラー通知用の `errorEvents`（`SharedFlow<Int>`）を追加する。
- 🔵 **どのような問題を解決するか**: EditScreen 上でユーザーが編集した本文（`formState.body`）ではなく、テンプレート適用前・編集前の元コンテンツを入力に LLM リライトを行うことで、ユーザーが編集操作をした後でも一貫した入力ソースからの書き換えを保証する（REQ-002, REQ-406）。
- 🔵 **想定されるユーザー**: 共有インテント経由でテキストを Obsidian に送る前に、LLM で本文を整形・要約したいエンドユーザー。
- 🔵 **システム内での位置づけ**: MVVM の ViewModel 層。UI（EditScreen, TASK-0065）とデータ層（`LlmRewriteRepository`/`LlmSettingsRepository`, TASK-0058〜0061）を仲介する。DI は Hilt を用いる。

- **参照したEARS要件**: REQ-002, REQ-003, REQ-102, REQ-201, REQ-202, REQ-406, EDGE-101
- **参照した設計文書**:
  - `docs/design/llm-memo-rewrite/architecture.md`（「EditScreenViewModel 設計変更」）
  - `docs/design/llm-memo-rewrite/interfaces.kt`（L176-194「EditFormState（変更後）」, L253-324「EditScreenViewModel 変更後シグネチャ」）
  - `docs/design/llm-memo-rewrite/design-interview.md`（Q2: Hilt化, Q3: SharedFlow によるエラー通知）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 コンストラクタ（Hilt注入）🔵

- **入力**: `llmRewriteRepository: LlmRewriteRepository`, `llmSettingsRepository: LlmSettingsRepository`（`@Inject constructor`）
- 参照: `interfaces.kt` L269「class EditScreenViewModel @Inject constructor(...)」
- 既存インターフェース: `LlmRewriteRepository.rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult`（`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`）、`LlmSettingsRepository.getSettings(): Flow<LlmSettings>`（同 `LlmSettingsRepository.kt`）

### 2.2 `initialize()` の入力・出力 🔵

- **入力パラメータ**（既存シグネチャは TASK-0062 で追加済み・本タスクでは処理を追加）:
  - `processed: ProcessedContent`
  - `config: NoteConfig`
  - `customFields: List<CustomFieldState> = emptyList()`
  - `sourceContent: String = ""`（LLM入力用の元コンテンツ、REQ-406）
  - `bodyLlmPrompt: String = ""`（テンプレートの本文用プロンプト、REQ-102）
- **出力（状態変化）**: `_formState` に `rewriteBodyEnabled = bodyLlmPrompt.isNotBlank()` を含む `EditFormState` を設定する（REQ-102）。`initialized` フラグにより2回目以降の呼び出しは早期リターン（EDGE-101 の重複初期化防止）。
- 参照: `interfaces.kt` L295-297, TASK-0063.md 実装詳細3

### 2.3 `rewriteBody()` の入力・出力 🔵

- **入力**: 引数なし。内部で保持している `sourceContent`（`formState.body` ではない）と `bodyLlmPrompt`、および `llmSettingsRepository.getSettings().first()` で取得した `LlmSettings` を使用する。
- **出力（状態変化・イベント）**:
  - 実行開始時: `formState.isRewritingBody = true`（REQ-201）
  - 成功時（`LlmRewriteResult.Success`）: `formState.body = result.text`（REQ-003 即時上書き）
  - 失敗時（`LlmRewriteResult.Failure`）: `errorEvents` に `result.messageResId`（`Int`, string resource ID）を1件 emit（NFR-201）。`formState.body` は変更しない。
  - 完了時: `formState.isRewritingBody = false`
- 参照: `interfaces.kt` L302-310, TASK-0063.md 実装詳細5

### 2.4 `errorEvents`（SharedFlow）の出力 🔵

- **型**: `SharedFlow<Int>`（値は string resource ID）
- **公開形式**: `private val _errorEvents = MutableSharedFlow<Int>()` / `val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()`
- **意味**: 一回限りのエラーイベント。EditScreen 側（TASK-0065）で購読し Toast 表示する。
- 参照: `interfaces.kt` L281-282, design-interview.md Q3

### 2.5 `EditFormState`（変更後）🔵

既存フィールド（`vault`, `title`, `body`, `tagsText`, `folder`, `customFields`）に以下を追加:

| フィールド | 型 | デフォルト | 意味 | 根拠 |
|-----------|----|-----------|------|------|
| `isRewritingBody` | `Boolean` | `false` | 本文リライト中のローディング状態 | REQ-201 |
| `rewriteBodyEnabled` | `Boolean` | `false` | 「メモを更改」ボタンの活性判定（`initialize()` 時に `bodyLlmPrompt.isNotBlank()` から算出） | REQ-102 |

- 参照: `interfaces.kt` L185-194, 実装ファイル `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`

### 2.6 入出力の関係性・データフロー 🔵

```
「メモを更改」ボタン押下
  → rewriteBody()
    → isRewritingBody = true
    → llmSettingsRepository.getSettings().first() で LlmSettings 取得
    → llmRewriteRepository.rewrite(settings, bodyLlmPrompt, sourceContent)
      → Success: formState.body = result.text
      → Failure: errorEvents.emit(result.messageResId)
    → isRewritingBody = false
  → EditScreen が state/イベントを購読して UI 更新
```

- 参照: `docs/design/llm-memo-rewrite/dataflow.md`, note.md §6 データフロー

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **入力ソースの制約（REQ-002, REQ-406）**: `rewriteBody()` は必ず `sourceContent` を入力とする。EditScreen 上でユーザーが編集した可能性がある `formState.body` を入力にしてはならない。`sourceContent`・`bodyLlmPrompt` は `EditFormState` に含めず、ViewModel のプライベート状態として保持する。
- 🔵 **空文字ガード禁止（EDGE-101）**: `sourceContent` が空文字であっても `rewriteBody()` は早期リターンせず、そのまま LLM API に送信する。ボタンの活性/非活性は `bodyLlmPrompt` の有無（`rewriteBodyEnabled`）のみで判定する。
- 🔵 **ローディング状態（REQ-201, NFR-202）**: LLM 呼び出し中は `isRewritingBody = true` とし、完了後 `false` に戻す。UI 表示制御は EditScreen 側に委譲する。
- 🔵 **タイムアウト（REQ-202, NFR-001）**: 30秒タイムアウトは `LlmModule` の Ktor HttpClient（`requestTimeoutMillis = 30_000`）でリポジトリ層が処理済み。ViewModel 側で明示的なタイムアウト処理は追加しない。タイムアウトは `LlmRewriteResult.Failure.Timeout` として返る。
- 🔵 **エラー表示（NFR-201）**: エラーメッセージは string resource ID として `errorEvents` に emit する。日本語テキストは `res/values/strings.xml` で管理し、ViewModel は resource ID のみ扱う。
- 🔵 **重複初期化防止（EDGE-101）**: 既存の `initialized` フラグを維持し、画面回転時に `initialize()` が再呼び出しされても `formState`・`sourceContent`・`bodyLlmPrompt` を上書きしない。
- 🔵 **Hilt化の互換性制約**: `@HiltViewModel` 化後も `MainActivity` の `private val viewModel: EditScreenViewModel by viewModels()` はそのまま動作しなければならない（既存の取得方法を変更しない）。
- 🔵 **既存テスト非破壊**: 既存の単体テスト（`EditScreenViewModelTest`, `EditScreenViewModelInitializeTest`）がすべて通ること。
- 🟡 **非同期処理制約**: `rewriteBody()` は `viewModelScope.launch` で実行し、`suspend` 呼び出し（`getSettings().first()`, `rewrite(...)`）を含む。テストは `kotlinx-coroutines-test`（`runTest` + `StandardTestDispatcher` + `advanceUntilIdle`）で制御する。

- **参照したEARS要件**: REQ-002, REQ-102, REQ-201, REQ-202, REQ-406, NFR-001, NFR-201, NFR-202, EDGE-101
- **参照した設計文書**: `architecture.md`（EditScreenViewModel 設計変更・LLMリクエスト設計）, `LlmModule.kt`（タイムアウト設定）, note.md §6

---

## 4. 想定される使用例（EARS Edgeケース・データフローベース）

### 4.1 基本的な使用パターン（REQ-002, REQ-003）🔵

1. テンプレートに `bodyLlmPrompt` が設定された状態で共有 → `initialize()` で `rewriteBodyEnabled = true`
2. ユーザーが「メモを更改」ボタン押下 → `rewriteBody()`
3. 成功 → `formState.body` が LLM 応答テキストで即時上書きされる

### 4.2 プロンプト未設定（REQ-102）🔵

- `bodyLlmPrompt = ""` で `initialize()` → `rewriteBodyEnabled = false`（ボタン非活性）。

### 4.3 エラーケース（EDGE-001〜004, NFR-201）🔵

- `rewrite(...)` が `Failure.NetworkError` / `AuthError` / `Timeout` / `EmptyOrInvalidResponse` / `Unknown` を返す → `formState.body` は不変、`errorEvents` に対応する `messageResId` を1件発行。

### 4.4 エッジケース: 元コンテンツ空文字（EDGE-101）🔵

- `sourceContent = ""` でも `rewriteBody()` はガードせず `rewrite(settings, bodyLlmPrompt, "")` を実際に呼び出し、成功時は `formState.body` を上書きする。

### 4.5 エッジケース: ユーザー編集後のリライト（REQ-002, REQ-406）🔵

- `initialize(sourceContent = "元コンテンツ")` 後にユーザーが `updateBody("編集した本文")` → `rewriteBody()` 呼び出し時、`rewrite()` の `content` 引数は `"元コンテンツ"` であり `"編集した本文"` ではない。

### 4.6 ローディング状態遷移（REQ-201）🔵

- `rewriteBody()` 実行中は `isRewritingBody = true`、完了後 `false` に戻る。

- **参照したEARS要件**: REQ-002, REQ-003, REQ-102, REQ-201, REQ-406, EDGE-101, EDGE-001〜004（`LlmRewriteResult.Failure` 種別）
- **参照した設計文書**: `dataflow.md`, `LlmRewriteResult.kt`（Failure 種別定義）

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: LLM による本文リライト（共有前に本文を整形・要約する）
- **参照した機能要件**:
  - REQ-002（元コンテンツ+プロンプトを LLM に送信）
  - REQ-003（応答で本文フィールドを即時上書き）
  - REQ-102（プロンプト未設定時にボタン非活性化 = `rewriteBodyEnabled`）
  - REQ-201（LLM呼び出し中のローディング表示 = `isRewritingBody`）
  - REQ-202（30秒タイムアウト、リポジトリ層で処理）
  - REQ-406（元コンテンツをテンプレート適用・ユーザー編集と独立して保持）
- **参照した非機能要件**: NFR-001（100ms/30sタイムアウト）, NFR-201（Toastエラー表示 = `errorEvents`）, NFR-202（ローディング明示）
- **参照したEdgeケース**: EDGE-101（元コンテンツ空文字でもボタン活性・空文字送信）, EDGE-001〜004（`LlmRewriteResult.Failure` 各種別）
- **参照した受け入れ基準**: TASK-0063.md 単体テスト要件 TC-1〜TC-7
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`「EditScreenViewModel 設計変更」
  - **データフロー**: `docs/design/llm-memo-rewrite/dataflow.md`（リライト処理フロー）
  - **型定義**: `docs/design/llm-memo-rewrite/interfaces.kt` L176-194（EditFormState）, L253-324（EditScreenViewModel）
  - **設計ヒアリング**: `docs/design/llm-memo-rewrite/design-interview.md` Q2（Hilt化）, Q3（SharedFlow エラー通知）
- **参照した既存実装**:
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（変更対象・sourceContent/bodyLlmPrompt は TASK-0062 で保持済み）
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`（変更対象）
  - `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt` / `LlmSettingsRepository.kt` / `LlmRewriteResult.kt`（依存先）
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（`@HiltViewModel` 参考パターン）

---

## 品質判定

```
✅ 高品質:
- 要件の曖昧さ: なし（入出力・状態遷移が具体的なメソッドシグネチャと state フィールドで確定）
- 入出力定義: 完全（コンストラクタ・initialize()・rewriteBody()・errorEvents・EditFormState を網羅）
- 制約条件: 明確（入力ソース制約・空文字ガード禁止・Hilt互換性・既存テスト非破壊）
- 実装可能性: 確実（依存リポジトリ・DTO・DIモジュールは TASK-0058〜0062 で実装済み、シグネチャ確認済み）
- 信頼性レベル: 🔵 が大半（EARS要件・interfaces.kt・既存コードで裏付け）
```

### 信頼性レベル分布

| 信号 | 件数（概数） | 主な項目 |
|------|-------------|---------|
| 🔵 青 | 大半 | 機能概要・入出力・制約・使用例のほぼ全項目 |
| 🟡 黄 | 少数 | 非同期処理のテスト実行方式（`runTest`/`StandardTestDispatcher` の選択） |
| 🔴 赤 | 0 | なし |

**総合評価**: ✅ 高品質。EARS要件定義書・設計文書・既存実装コードとの照合が取れており、推測に依存する箇所はほぼない。
