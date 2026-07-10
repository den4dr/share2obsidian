# TASK-0072 TDD要件定義書

**機能名**: EditScreenViewModel `generateCustomFieldValue()`・EditScreen UI「生成」ボタン追加
**タスクID**: TASK-0072
**要件名**: llm-memo-rewrite
**タスクタイプ**: TDD（Phase 6 - カスタムフィールドのLLM生成、Could Have）
**作成日**: 2026-07-09

---

## 信頼性レベル凡例

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測をしている
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: `EditScreenViewModel` に `generateCustomFieldValue(index: Int)` を追加し、`customFields[index].valueSource == FieldValueSource.LLM` のカスタムフィールドについて、そのフィールドの `llmPrompt` と共有/取得直後の `sourceContent` を入力としてLLM呼び出しを行い、応答テキストで該当インデックスの `value` のみを更新する。EditScreen 側では `valueSource == LLM` のフィールドにのみ「生成」ボタンを表示する。
- 🔵 **どのような問題を解決するか**: カスタムフィールドの値を手入力せず、元コンテンツをもとにLLMで自動生成できるようにする。ユーザーはフィールドごとに任意のタイミングで生成を実行できる（As a: メモを整理したいユーザー／So that: 定型フィールドの値をLLMに補完させたい）。
- 🔵 **想定されるユーザー**: Share2Obsidian でテンプレートを利用し、カスタムフィールドにLLM生成ソースを設定したユーザー。
- 🟡 **システム内での位置づけ**: 本文リライト（`rewriteBody()`, TASK-0063）・タグ提案（`suggestTags()`, TASK-0068）と同一の「`sourceContent` を入力とするLLM呼び出し」パターンを踏襲する第3の機能。MVVM + Repository アーキテクチャ上で `EditScreenViewModel` が `LlmRewriteRepository`／`LlmSettingsRepository` に依存する（生成タイミングはテンプレート適用時ではなく EditScreen 上のボタン押下時に統一する設計判断）。
- **参照したEARS要件**: REQ-104, REQ-303, REQ-304
- **参照した設計文書**: architecture.md「EditScreenViewModel への変更」「EditScreen への変更」、dataflow.md「機能3: カスタムフィールドのLLM生成」

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 入力パラメータ

- 🔵 **`generateCustomFieldValue(index: Int)`**
  - `index`: 生成対象カスタムフィールドの `formState.value.customFields` におけるインデックス（0始まり）。
- 🔵 **暗黙の入力（ViewModel 内部状態）**:
  - `formState.value.customFields[index].llmPrompt`: LLMに渡すプロンプト（`CustomFieldState.llmPrompt`）。
  - `sourceContent: String`: 共有/取得直後の元コンテンツ。`initialize()` で保持され、ユーザーの本文編集（`formState.body`）とは独立（REQ-406）。
  - `llmSettingsRepository.getSettings().first()`: LLM API 接続設定（endpointUrl/apiKey/model）。

### 出力値

- 🔵 **成功時**: `updateCustomField(index, result.text)` により `formState.value.customFields[index].value` のみが応答テキストで更新される。他インデックスの `CustomFieldState`（全プロパティ）は不変。
- 🔵 **失敗時**: `formState.value.customFields[index].value` は変更されず、`errorEvents`（`SharedFlow<Int>`）に対応する `messageResId`（`R.string.error_llm_*`）を1件発行する。
- 🟡 **UI 状態（オプション）**: 個別フィールドのローディング表示のため、`EditFormState` に `generatingFieldIndex: Int?` を追加する想定（生成中のインデックス、null=非生成中）。既存の `isRewritingBody`/`isSuggestingTags` とは別に、フィールド単位のローディング管理が必要なため。

### 入出力の関係性・データフロー

- 🔵 入力 `index` → `customFields[index].llmPrompt` を取得 → `rewrite(settings, prompt, sourceContent)` → Success なら `customFields[index].value` を上書き / Failure なら `errorEvents` に発行。
- 🔵 呼び出し入力は `rewriteBody()`/`suggestTags()` と同様に**常に `sourceContent`** であり、`formState.body`（ユーザー編集後の本文）は使用しない。

- **参照したEARS要件**: REQ-104, REQ-303, REQ-304, REQ-406
- **参照した設計文書**: dataflow.md「機能3」シーケンス図、interfaces.kt（`CustomFieldState`, `LlmRewriteResult`, `EditScreenViewModelSpec`）、既存 `EditScreenViewModel.kt`（`updateCustomField`, `runLlmRequest`）

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **状態の不変更新**: `EditFormState`・`CustomFieldState` は `.copy()` で新インスタンスを生成して更新する（直接フィールド変更禁止）。既存 `updateCustomField(index, value)` を再利用する。
- 🔵 **非同期実行**: LLM呼び出しは `viewModelScope.launch` 内で実行する。API 呼び出し自体の Dispatcher 切替（IO）は `llmRewriteRepository` 側で処理済み。
- 🔵 **入力ソースの固定**: `sourceContent` は不変であり、ユーザーが `formState.body` を編集しても影響を受けない（REQ-406）。生成入力には常に `sourceContent` を使用する。
- 🔵 **エラー処理**: 失敗時は状態を変更せず `errorEvents` にメッセージ resource ID を発行する（NFR-201 相当、既存 `runLlmRequest` パターン）。
- 🔵 **文字列リソース**: UI 文言はすべて `strings.xml` に登録する（ハードコード禁止）。追加候補: `field_generate_button`（"生成"）, `field_generating`（"生成中..."）。
- 🟡 **DI**: `EditScreenViewModel` は `@HiltViewModel` でコンストラクタ注入済み（TASK-0063）。新規依存の追加は不要。
- 🟡 **フィールド単位ローディング**: 生成中はそのフィールドのみローディング表示とし、他フィールドの入力は継続可能とする（新規要素）。

- **参照したEARS要件**: REQ-406, NFR-201, REQ-201（拡張）
- **参照した設計文書**: architecture.md「EditScreenViewModel への変更」、note.md §6 注意事項、既存 `EditScreenViewModel.kt` `runLlmRequest`

---

## 4. 想定される使用例（Edgeケース・データフローベース）

### 基本的な使用パターン

- 🔵 **正常系**: `valueSource == LLM` のフィールド横の「生成」ボタン押下 → `generateCustomFieldValue(index)` → `rewrite` Success → 該当フィールドの `value` が応答テキストで更新される。
- 🔵 **入力ソースの独立性**: `initialize(sourceContent="元コンテンツ")` 後にユーザーが `updateBody("編集後本文")` しても、生成入力は `sourceContent="元コンテンツ"` が使われる（REQ-406, REQ-002/REQ-302 と同一方針）。

### データフロー

- 🟡 EditScreen（生成ボタン押下） → ViewModel `generateCustomFieldValue(index)` → `LlmRewriteRepository.rewrite(settings, llmPrompt, sourceContent)` → 外部LLM API → `LlmRewriteResult.Success(text)` → `updateCustomField(index, text)` → `formState` 更新 → EditScreen 再描画（dataflow.md「機能3」）。

### エッジケース・エラーケース

- 🔵 **LLM呼び出し失敗**（NetworkError/ApiError/ParseError 等）: 該当フィールドの `value` は不変、`errorEvents` に `messageResId` を1件発行 → EditScreen が Toast 表示。
- 🟡 **UI 表示条件**: `valueSource` が FIXED/HTML_META/URL/EMPTY のフィールドには「生成」ボタンを表示しない（LLM のみ表示）。
- 🟡 **複数フィールドの独立性**: 対象インデックス以外の `CustomFieldState`（`value` 含む全プロパティ）は生成前後で変化しない。

- **参照したEARS要件**: REQ-104, REQ-304, REQ-406
- **参照した設計文書**: dataflow.md「機能3」、TASK-0072.md 単体テスト要件（テストケース1〜3）

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: 「テンプレートのカスタムフィールドをLLMで自動生成したい」（Phase 6, Could Have）
- **参照した機能要件**:
  - REQ-104: カスタムフィールドのLLM生成が有効な場合、テンプレート編集画面で当該フィールド用のプロンプト入力欄を表示 🔵
  - REQ-303: `FieldValueSource` に `LLM` を追加してもよい（Could Have）🔵
  - REQ-304: `FieldValueSource == LLM` のフィールドについてLLMで値を生成してもよい 🟡（生成タイミングは EditScreen ボタン押下時に統一）
  - REQ-406: LLM入力用に `sourceContent` をユーザー編集と独立して保持 🟡
- **参照した非機能要件**: NFR-201（エラー時のユーザー通知）
- **参照したEdgeケース**: 生成失敗時の状態非変更＋エラー通知、非LLMフィールドでのボタン非表示
- **参照した受け入れ基準**（TASK-0072 単体テスト要件）:
  - TC1: `generateCustomFieldValue(index)` で該当インデックスの `value` のみ更新、他フィールドは不変
  - TC2: 失敗時に `value` 不変かつ `errorEvents` 1件発行
  - TC3: 入力に `sourceContent` を使い `formState.body` を使わない
- **参照した設計文書**:
  - **アーキテクチャ**: architecture.md「EditScreenViewModel への変更」「EditScreen への変更」
  - **データフロー**: dataflow.md「機能3: カスタムフィールドのLLM生成」シーケンス図
  - **型定義**: interfaces.kt（`CustomFieldState.valueSource`/`llmPrompt`, `LlmRewriteResult`）、`EditFormState`
  - **既存実装**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（`updateCustomField`, `runLlmRequest`, `rewriteBody`, `suggestTags`）

---

## 6. 実装・テスト対象ファイル

### 実装対象

- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` — `generateCustomFieldValue(index: Int)` 追加（必要に応じ `runLlmRequest` 再利用／フィールド単位ローディング対応）
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt` — カスタムフィールド表示ループに `valueSource == LLM` 限定の「生成」ボタン追加
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` — （オプション）`generatingFieldIndex: Int?` 追加
- `app/src/main/res/values/strings.xml` — 「生成」ボタンラベル等の文字列追加

### テスト対象

- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelGenerateCustomFieldValueTest.kt` — 新規テストクラス（TC1〜TC3）
- 参考: `EditScreenViewModelRewriteBodyTest.kt`, `EditScreenViewModelSuggestTagsTest.kt`

---

## 7. 品質判定

| 判定項目 | 状態 |
|---------|------|
| 要件の曖昧さ | なし（入出力・失敗時挙動が完了条件で明確） |
| 入出力定義 | 完全（入力: index/llmPrompt/sourceContent/settings、出力: 該当 value 更新 or errorEvents） |
| 制約条件 | 明確（不変更新・sourceContent 固定・エラー処理・文字列リソース） |
| 実装可能性 | 確実（既存 `rewriteBody`/`suggestTags`/`updateCustomField` パターンの組合せで実装可能） |

### 信頼性レベル分布

- 🔵 青信号: 概ね多数（完了条件・既存実装パターン・REQ-104 に直接対応する項目）
- 🟡 黄信号: 生成タイミングの設計判断（REQ-304）、`sourceContent` 保持（REQ-406）、フィールド単位ローディング（新規UI要素）
- 🔴 赤信号: なし

**総合判定**: ✅ 高品質（🔴 赤信号なし。🟡 は設計判断・新規UI要素に限定され、いずれも設計文書から妥当に導出可能）
