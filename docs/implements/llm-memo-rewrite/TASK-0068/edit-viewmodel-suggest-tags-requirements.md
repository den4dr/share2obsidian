# TDD要件定義書 - TASK-0068: EditScreenViewModel suggestTags()実装

- **機能名**: edit-viewmodel-suggest-tags（EditScreenViewModel タグ提案）
- **タスクID**: TASK-0068
- **要件名**: llm-memo-rewrite
- **フェーズ**: Phase 5 - タグ提案（Should Have）
- **作成日**: 2026-07-08
- **参照ノート**: `docs/implements/llm-memo-rewrite/TASK-0068/note.md`

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: `EditScreenViewModel` に `suggestTags()` メソッドを追加する。共有/取得直後の元コンテンツ（`sourceContent`。本文リライトと同一の入力ソース）と、アプリ内固定プロンプトを外部LLM API（OpenAI互換 Chat Completions形式）へ送信し、生成されたタグ候補を **既存の `tagsText` に追加**（置換ではない）する。
  - *参照: requirements.md REQ-301, REQ-302 / TASK-0068.md タスク概要*
- 🔵 **どのような問題を解決するか**: ユーザーがメモ保存前に、内容に即したタグを自分で考える手間を減らし、LLMにタグ候補を提案させることで入力負荷を軽減する。
  - *参照: user-stories.md（タグ提案ストーリー）/ requirements.md REQ-103*
- 🔵 **想定されるユーザー**: 他アプリからテキストを共有し、Obsidianへ保存する前に EditScreen 上でメモを編集する利用者。
  - *参照: requirements.md 概要 / user-stories.md*
- 🔵 **システム内での位置づけ**: MVVM + Repository アーキテクチャの ViewModel 層。`EditScreenViewModel`（状態管理・ビジネスロジック）→ `LlmRewriteRepository`（LLM API通信）→ Ktor Client の呼び出しフローに属する。ローディング状態・エラー処理は既存 `rewriteBody()`（TASK-0063）と同一パターンを踏襲する。
  - *参照: architecture.md「EditScreenViewModel 設計変更」/ dataflow.md「機能2: タグ提案」*

- **参照したEARS要件**: REQ-103, REQ-301, REQ-302, REQ-406, EDGE-101
- **参照した設計文書**: architecture.md「EditScreenViewModel 設計変更」, dataflow.md「機能2: タグ提案（Should Have）」

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 入力パラメータ

- 🔵 **メソッドシグネチャ**: `fun suggestTags()`（引数なし）。ViewModelが保持済みの状態から入力を組み立てる。
  - *参照: TASK-0068.md 実装詳細2 / interfaces.kt EditScreenViewModelSpec*
- 🔵 **LLM入力コンテンツ**: `sourceContent: String`（ViewModelプロパティ、`initialize()` で受領・保持）。本文リライトと同一の入力ソース。空文字も許容し、空チェックによるガードは行わない。
  - *参照: requirements.md REQ-302, REQ-406, EDGE-101 / EditScreenViewModel.kt sourceContent*
- 🔵 **プロンプト**: アプリ内固定プロンプト `TAG_SUGGESTION_PROMPT`。テンプレート単位の `bodyLlmPrompt` とは異なり、`strings.xml` の `llm_tag_suggestion_prompt` から取得する統一文字列。
  - *参照: TASK-0068.md 実装詳細3 / note.md §6 技術的制約*
- 🔵 **LLM接続設定**: `llmSettingsRepository.getSettings().first()` で取得する `LlmSettings`（endpointUrl / apiKey / model）。
  - *参照: EditScreenViewModel.kt rewriteBody() / architecture.md「LLM設定管理設計」*

### 出力値

- 🔵 **成功時（`LlmRewriteResult.Success(text)`）**: `formState.tagsText` を更新する。
  - `tagsText` が空白（`isBlank()`）の場合: `result.text` のみを設定
  - 既存タグがある場合: `"$current, ${result.text}"` としてカンマ+スペース区切りで連結（追加、置換しない）
  - *参照: requirements.md REQ-302 / TASK-0068.md 実装詳細4*
- 🔵 **失敗時（`LlmRewriteResult.Failure`）**: `formState.tagsText` を変更せず、`_errorEvents.emit(result.messageResId)` でEditScreen向けにエラー表示用 string resource ID を発行する。
  - *参照: requirements.md NFR-201, EDGE-004 / TASK-0068.md 実装詳細5*
- 🔵 **ローディング状態**: `EditFormState.isSuggestingTags: Boolean`（新規追加、デフォルト `false`）。呼び出し開始時 `true`、完了時（成功・失敗いずれも）`false`。
  - *参照: requirements.md REQ-201 / TASK-0068.md 実装詳細1*

### 入出力の関係性

- 🔵 入力 `sourceContent` は書き換え対象ではなく、`tagsText` を更新するための材料である（本文 `body` は変更しない）。
  - *参照: requirements.md REQ-302 / dataflow.md「機能2: タグ提案」*

### データフロー

- 🔵 EditScreen（後続TASK-0069のボタン）→ `suggestTags()` → `LlmSettingsRepository.getSettings()` → `LlmRewriteRepository.rewrite(settings, TAG_SUGGESTION_PROMPT, sourceContent)` → 外部LLM API → 結果を `tagsText` へマージ or `errorEvents` へ発行。
  - *参照: dataflow.md「機能2: タグ提案（Should Have）」シーケンス図*

- **参照したEARS要件**: REQ-301, REQ-302, REQ-406, EDGE-101, EDGE-004, NFR-201, REQ-201
- **参照した設計文書**: interfaces.kt（`EditScreenViewModel` / `EditFormState` / `LlmRewriteResult` / `LlmSettings`）, LlmRewriteRepository.kt `rewrite(settings, prompt, content)`, LlmRewriteResult.kt（`Success(text)` / `Failure.messageResId`）

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

### パフォーマンス要件

- 🔵 **NFR-001**: LLM呼び出しのタイムアウトは30秒。Ktor Client `HttpTimeout` プラグイン（`requestTimeoutMillis = 30_000`）で実現（TASK-0060で実装済み。`suggestTags()` は既存Repositoryを利用するため追加実装不要）。
  - *参照: requirements.md NFR-001, REQ-202 / api-endpoints.md*

### セキュリティ要件

- 🔵 **NFR-101 / REQ-401**: LLM APIキーは `EncryptedSharedPreferences` に暗号化保存（TASK-0060で実装済み）。`suggestTags()` は `LlmSettingsRepository` 経由で取得する。
- 🔵 **NFR-102**: APIキー等の機微情報をログ・クラッシュレポートに出力しない。失敗は種別のみを `LlmRewriteResult.Failure` にマッピングし、`messageResId` のみをUIへ渡す。
  - *参照: requirements.md NFR-101, NFR-102, REQ-401 / note.md §6 セキュリティ要件*

### 互換性・動作要件

- 🔵 **REQ-402**: LLMリクエストはOpenAI互換 Chat Completions形式（既存Repositoryに準拠）。
- 🔵 **REQ-302（追加方針）**: 生成結果は既存タグの置換ではなく追加。既存タグを破壊してはならない。
- 🔵 **EDGE-101**: `sourceContent` が空文字であってもボタン非活性化・呼び出しスキップを行わず、空文字のままLLM APIへ送信する。`rewriteBody()` と同様に `sourceContent` の空チェックガードを設けない。
  - *参照: requirements.md REQ-402, REQ-302, EDGE-101*

### アーキテクチャ制約

- 🔵 **既存パターン踏襲**: `rewriteBody()` と同一の非同期処理パターン（`viewModelScope.launch` + `StateFlow` 更新 + `SharedFlow` エラー通知）を用いる。
  - *参照: architecture.md「EditScreenViewModel 設計変更」/ EditScreenViewModel.kt rewriteBody()*
- 🟡 **プロンプト供給方法**: テンプレート単位のカスタムプロンプトは要件上明示されていないため、アプリ内固定プロンプトを採用する設計判断。`strings.xml` の `llm_tag_suggestion_prompt` を string resource として定義する。
  - *参照: TASK-0068.md 実装詳細3 / dataflow.md「機能2: タグ提案」/ design-interview.md「残課題」*
- 🟡 **プロンプト取得のための Context 依存**: 現行 `EditScreenViewModel` は `LlmRewriteRepository` / `LlmSettingsRepository` のみをコンストラクタ注入しており `Context` を保持しない。`R.string.llm_tag_suggestion_prompt` の解決には `@ApplicationContext` の注入等が必要となる（実装/設計フェーズで方式決定。テストでは固定文字列で代替可能）。
  - *参照: EditScreenViewModel.kt コンストラクタ定義 / TASK-0068.md 実装詳細3（推測を含むため要確認）*

### API制約

- 🔵 `LlmRewriteRepository.rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult` を利用する。第2引数 `prompt` に固定プロンプト、第3引数 `content` に `sourceContent` を渡す。
  - *参照: LlmRewriteRepository.kt / api-endpoints.md*

- **参照したEARS要件**: NFR-001, NFR-101, NFR-102, REQ-201, REQ-202, REQ-401, REQ-402, REQ-302, EDGE-101
- **参照した設計文書**: architecture.md「EditScreenViewModel 設計変更」「LLM設定管理設計」, api-endpoints.md, LlmRewriteRepository.kt, LlmRewriteResult.kt

---

## 4. 想定される使用例（EARS Edgeケース・データフローベース）

### 基本的な使用パターン

- 🔵 **正常系（既存タグあり）**: `tagsText = "private, tag1"` の状態で `suggestTags()` を呼び、LLMが `"tag2, tag3"` を返すと、`tagsText = "private, tag1, tag2, tag3"` になる。
  - *参照: TASK-0068.md テストケース1 / REQ-302*
- 🔵 **正常系（既存タグなし）**: `tagsText = ""` の状態で呼び、LLMが `"tag1, tag2"` を返すと、`tagsText = "tag1, tag2"`（生成結果のみ）になる。
  - *参照: TASK-0068.md テストケース2 / 完了条件*

### データフロー

- 🔵 呼び出し直後に `isSuggestingTags = true` → LLM往復 → 結果反映後 `isSuggestingTags = false`。UI（TASK-0069）はこの状態を購読しスピナー表示・ボタン非活性化を制御する。
  - *参照: dataflow.md「機能2: タグ提案」/ REQ-201*

### エッジケース

- 🔵 **EDGE-101（空文字入力）**: `sourceContent = ""` でもガードせず、`rewrite(settings, TAG_SUGGESTION_PROMPT, "")` を1回呼び出す（スキップしない）。
  - *参照: requirements.md EDGE-101 / TASK-0068.md テストケース5*

### エラーケース

- 🔵 **EDGE-004 / ネットワーク・認証・タイムアウト・空応答**: `LlmRewriteResult.Failure`（`NetworkError` / `AuthError` / `Timeout` / `EmptyOrInvalidResponse` / `Unknown`）を受けた場合、`tagsText` を変更せず `errorEvents` に `messageResId` を発行する。
  - *参照: requirements.md EDGE-001〜004, NFR-201 / TASK-0068.md テストケース3*
  - 補足: TASK-0068.md 例中の `R.string.error_llm_network` は、実装上 `LlmRewriteResult.Failure.NetworkError(messageResId).messageResId` として渡される値に相当する。🟡（対応関係の推測）

- **参照したEARS要件**: EDGE-101, EDGE-001, EDGE-002, EDGE-003, EDGE-004, NFR-201
- **参照した設計文書**: dataflow.md「機能2: タグ提案（Should Have）」, LlmRewriteResult.kt（Failure サブクラス）

---

## 5. テスト要件（TASK-0068単体テスト要件より）

**テストフレームワーク**: JUnit 4 + Robolectric + MockK + kotlinx-coroutines-test
**テストクラス（想定）**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt`
**踏襲元**: `EditScreenViewModelRewriteBodyTest.kt`（TASK-0063）

| TC ID | カテゴリ | Given | When | Then | 信頼性 |
|-------|---------|-------|------|------|--------|
| TC-0068-N01 | 正常系 | `tagsText="private, tag1"`, `rewrite(...)`→`Success("tag2, tag3")` | `suggestTags()` | `tagsText="private, tag1, tag2, tag3"`（既存保持・追加） | 🔵 REQ-302 |
| TC-0068-N02 | 正常系 | `tagsText=""`, `rewrite(...)`→`Success("tag1, tag2")` | `suggestTags()` | `tagsText="tag1, tag2"`（生成結果のみ） | 🔵 完了条件 |
| TC-0068-E01 | エラー系 | `tagsText="private"`, `rewrite(...)`→`Failure(NetworkError)` | `suggestTags()` | `tagsText="private"`（不変）, `errorEvents` にmessageResId発行 | 🔵 EDGE-004 |
| TC-0068-B01 | ローディング | `rewrite(...)` を `CompletableDeferred` で保留 | `suggestTags()` | 呼出直後 `isSuggestingTags=true`, 完了後 `false` | 🔵 REQ-201 |
| TC-0068-B02 | 境界値 | `sourceContent=""` で初期化, `rewrite(...)` モック | `suggestTags()` | `rewrite(settings, TAG_SUGGESTION_PROMPT, "")` が1回呼ばれる | 🔵 EDGE-101 |

**テスト実装の注意点**:
- 🔵 `errorEvents` は `suggestTags()` 呼び出し前に購読を開始し、検証後に `job.cancel()`（本プロジェクト既知パターン）。
- 🔵 `CompletableDeferred` によるローディング中間観測は `advanceUntilIdle()` 前に `isSuggestingTags == true` を検証してから `deferred.complete(...)`。
- 🔵 `Dispatchers.setMain(testDispatcher)` / `@After` で `Dispatchers.resetMain()`。`@Config(sdk = [34])`。
  - *参照: note.md §5 テスト関連情報 / EditScreenViewModelRewriteBodyTest.kt*

---

## 6. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: タグ提案（Should Have）ストーリー（user-stories.md）
- **参照した機能要件**: REQ-103, REQ-201, REQ-202, REQ-301, REQ-302, REQ-401, REQ-402, REQ-406
- **参照した非機能要件**: NFR-001, NFR-101, NFR-102, NFR-201
- **参照したEdgeケース**: EDGE-001, EDGE-002, EDGE-003, EDGE-004, EDGE-101
- **参照した受け入れ基準**: TASK-0068.md 単体テスト要件（テストケース1〜5）
- **参照した設計文書**:
  - **アーキテクチャ**: architecture.md「EditScreenViewModel 設計変更」「新規追加コンポーネント」「LLM設定管理設計」
  - **データフロー**: dataflow.md「機能2: タグ提案（Should Have）」
  - **型定義**: interfaces.kt（`EditScreenViewModel` / `EditFormState` / `LlmRewriteResult` / `LlmSettings`）
  - **データベース**: 該当なし（本タスクはDBスキーマに影響しない）
  - **API仕様**: api-endpoints.md（OpenAI互換 Chat Completions）

### 実装対象ファイル

| ファイル | 変更内容 |
|---------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` | `isSuggestingTags: Boolean = false` 追加 |
| `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` | `suggestTags()` メソッド・`TAG_SUGGESTION_PROMPT` 追加 |
| `app/src/main/res/values/strings.xml` | `<string name="llm_tag_suggestion_prompt">…</string>` 追加 |
| `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt` | 5テストケース新規作成 |

---

## 品質判定

- ✅ **要件の曖昧さ**: なし（既存 `rewriteBody()` パターンと同型で明確）
- ✅ **入出力定義**: 完全（入力=sourceContent+固定プロンプト+settings、出力=tagsTextマージ/errorEvents/isSuggestingTags）
- ✅ **制約条件**: 明確（NFR/REQ/EDGE を既存実装で充足、追加はViewModel層のみ）
- ⚠️ **実装可能性の留意点**: 固定プロンプトの解決に `Context` 注入が必要（現行ViewModelは未保持）。方式は設計/実装フェーズで確定する（🟡）。
- **信頼性レベル分布**: 🔵 多数（本文の大半）、🟡 3件（固定プロンプト設計判断・Context注入方式・messageResId対応関係）、🔴 なし

**総合判定**: 高品質（🔵優勢、🟡は設計判断・実装方式に限定、🔴ゼロ）
