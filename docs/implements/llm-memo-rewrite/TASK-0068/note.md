# TDD開発コンテキスト - TASK-0068: EditScreenViewModel suggestTags()実装

**タスク**: EditScreenViewModelに`suggestTags()`メソッドを追加。元コンテンツとアプリ内固定プロンプトを使ってLLMがタグ候補を生成し、既存タグに追加する（置換ではない）。
**タスクID**: TASK-0068
**対応要件**: REQ-103, REQ-301, REQ-302, REQ-406 / テスト要件5ケース
**テストフレームワーク**: JUnit 4 + MockK + kotlinx-coroutines-test

---

## 1. 技術スタック

### 言語・フレームワーク
- **Kotlin 2.2+**: すべてのViewModel・ドメインモデル実装
- **Jetpack Compose**: UI層（EditScreen）で購読・状態管理
- **MVVM + Repository**: `EditScreenViewModel` → `LlmRewriteRepository` → Ktor Client
- **参照元**: docs/tech-stack.md

### ビルドツール・言語互換性
- **Gradle 9.3+** (Kotlin DSL)
- **Java 11互換性**
- **最低SDK**: 33（Android 13）, 対象SDK: 36
- **参照元**: docs/tech-stack.md, gradle/libs.versions.toml

### 非同期処理パターン
- **Kotlin Coroutines**: `viewModelScope.launch()` による`EditScreenViewModel`内の非同期メソッド実装
- **StateFlow**: `EditFormState` の状態管理
- **SharedFlow**: エラー通知（`errorEvents`、一回限りのメッセージフロー）
- **参照元**: docs/design/llm-memo-rewrite/architecture.md §「EditScreenViewModel 設計変更」

### 依存性注入（DI）
- **Hilt 2.56+**: `EditScreenViewModel` は`@HiltViewModel`で`LlmRewriteRepository`・`LlmSettingsRepository`をコンストラクタ注入
- **LlmModule.kt**: Ktor HttpClient・EncryptedSharedPreferences・各Repositoryの提供
- **参照元**: docs/design/llm-memo-rewrite/architecture.md §「新規追加コンポーネント」

### HTTP通信・シリアライズ
- **Ktor Client (CIO エンジン)**: OpenAI互換 Chat Completions API呼び出し（既に TASK-0060 で実装済み）
- **kotlinx-serialization**: Chat Completions DTO のシリアライズ
- **HttpTimeout プラグイン**: `requestTimeoutMillis = 30_000` で30秒タイムアウト（NFR-001）
- **参照元**: docs/design/llm-memo-rewrite/api-endpoints.md

### 暗号化ストレージ
- **androidx.security-crypto**: `EncryptedSharedPreferences` で LLM APIキーを暗号化保存（REQ-401, NFR-101）
- **参照元**: docs/design/llm-memo-rewrite/architecture.md §「LLM設定管理設計」

---

## 2. 開発ルール

### TASK-0063 で確立された実装パターン（要踏襲）
- `suggestTags()` は `rewriteBody()` と同様のメソッドシグネチャ・エラー処理パターンを踏襲する
- **ローディング状態遷移**: `isSuggestingTags: Boolean` で false→true→false を管理（REQ-201）
- **エラー処理**: 失敗時は`formState.tagsText`を変更せず、`errorEvents.emit(result.messageResId)` で通知（NFR-201）
- **nullチェック**:`bodyLlmPrompt`のようにガード不可。`sourceContent`が空文字でも実行（EDGE-101）
- **参照元**: docs/tasks/llm-memo-rewrite/TASK-0063.md, app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt（rewriteBody()実装）

### テストパターン（MockK + kotlinx-coroutines-test）
- **Hiltなし直接インスタンス化**: MockK スタブ（`LlmRewriteRepository`・`LlmSettingsRepository`）をコンストラクタに直接渡す
- **共有TestCoroutineScheduler**: `StandardTestDispatcher` を `Dispatchers.setMain()` と `runTest()` の両方に明示的に渡し、`viewModelScope.launch` と測定ロジックを同期する
- **SharedFlow購読**: `backgroundScope.launch` ではなく通常の `launch` を使用し、検証後に明示的に `job.cancel()` する（本プロジェクト環境の既知パターン）
- **advanceUntilIdle()**: 非同期処理完了後の状態検証に使用
- **CompletableDeferred制御**: ローディング状態の途中観測（`true`/`false` 遷移確認）に使用
- **参照元**: app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelRewriteBodyTest.kt（TASK-0063の実装例）

### コーディング規約
- **ktlint準拠**: コードフォーマットは自動チェック（`mise exec -- ./gradlew lint`）
- **Kotlin慣習**: data class・sealed class・flow API等の標準パターン
- **参照元**: docs/tech-stack.md §「品質基準」

### アーキテクチャガイドライン
- **責務分離**: ViewModel（状態管理・ビジネスロジック）・Repository（LLM API通信）・State（UI状態）の責務明確化
- **テンプレート依存の削減**: `bodyLlmPrompt`とは異なり、`suggestTags()` はアプリ内固定プロンプト（`strings.xml` の `llm_tag_suggestion_prompt`）を使用。テンプレート単位のカスタムプロンプトは不要（REQ-406 の補足設計）
- **入力ソース統一**: 本文リライトと同様に `sourceContent`（テンプレート適用前の元コンテンツ）を入力（REQ-302, REQ-406）
- **参照元**: docs/design/llm-memo-rewrite/architecture.md, docs/spec/llm-memo-rewrite/requirements.md

---

## 3. 関連実装

### 既に完了している関連タスク

| タスク | 内容 | 参照元 |
|--------|------|--------|
| **TASK-0055〜0062** | LLM機能の基盤実装（LlmModule・LlmSettings・LlmRewriteRepository等） | docs/tasks/llm-memo-rewrite/overview.md |
| **TASK-0063** | `@HiltViewModel` 化・`rewriteBody()` 実装・テストパターン確立（14テストケース） | docs/tasks/llm-memo-rewrite/TASK-0063.md |
| **TASK-0064**  | EditScreen UI「メモを更改」ボタン実装（TASK-0063の結果を UI 反映） | docs/tasks/llm-memo-rewrite/TASK-0064.md |

### 実装対象ファイル（TASK-0068特有の変更）

| ファイル | 変更内容 | 影響範囲 |
|---------|---------|---------|
| **app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt** | `isSuggestingTags: Boolean = false` 追加（`isRewritingBody` と同パターン） | UI状態管理 |
| **app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt** | `suggestTags()` メソッド追加、`TAG_SUGGESTION_PROMPT` プライベートプロパティ追加 | ViewModel ビジネスロジック |
| **app/src/main/res/values/strings.xml** | `<string name="llm_tag_suggestion_prompt">…</string>` 追加（日本語プロンプトテンプレート） | リソース・UI表示文字列 |

### 既に存在する関連実装の参照

| ファイル | 用途 | 参照理由 |
|---------|------|---------|
| **data/llm/LlmRewriteRepository.kt** | LLM呼び出し API | `suggestTags()` で呼び出す`rewrite(settings, prompt, sourceContent)` の定義確認 |
| **data/llm/LlmSettingsRepository.kt** | LLM設定管理 | `suggestTags()` で `getSettings().first()` を呼び出す |
| **data/llm/LlmRewriteResult.kt** | LLM結果型（Success/Failure） | エラーハンドリングの型安全性確認 |
| **ui/EditFormState.kt** (既存部分) | `tagsText: String` プロパティ | 既存タグとの連結ロジック実装時の参照 |

**参照元**: docs/design/llm-memo-rewrite/architecture.md §「新規追加コンポーネント」・§「変更が必要な既存コンポーネント」

---

## 4. 設計文書

### 要件定義書
- **ファイル**: docs/spec/llm-memo-rewrite/requirements.md
- **関連要件**: REQ-103（タグ提案ボタン表示）、REQ-301（タグ提案オプション要件）、REQ-302（既存タグに追加）、REQ-406（sourceContent保持）、EDGE-101（空文字入力も実行）

### アーキテクチャ設計
- **ファイル**: docs/design/llm-memo-rewrite/architecture.md
- **関連セクション**:
  - §「EditScreenViewModel 設計変更」→ `suggestTags()` のメソッドシグネチャ・流れ
  - §「ProcessedContent 保持設計」→ `sourceContent` の必要性と保持方法
  - §「非機能要件の実現方法」→ タイムアウト・エラー処理・ローディング表示

### データフロー図
- **ファイル**: docs/design/llm-memo-rewrite/dataflow.md
- **関連機能**: 機能2「タグ提案（Should Have）」のシーケンス図
- **内容**: EditScreen → ViewModel → LlmRewriteRepository → 外部LLM API の往復フロー

### インターフェース型定義
- **ファイル**: docs/design/llm-memo-rewrite/interfaces.kt
- **内容**: `EditScreenViewModel.suggestTags()` / `EditFormState` / `LlmRewriteResult` / `LlmSettings` の型シグネチャ

### API仕様
- **ファイル**: docs/design/llm-memo-rewrite/api-endpoints.md
- **内容**: OpenAI互換 Chat Completions リクエスト/レスポンス形式、タイムアウト設定

---

## 5. テスト関連情報

### テストフレームワーク・設定
- **単体テストフレームワーク**: JUnit 4（`@Test` annotation）+ Robolectric（`@RunWith(RobolectricTestRunner.class)`）
- **モック・スタブライブラリ**: MockK（Kotlin向け`mockk()` API）
- **非同期テスト**: kotlinx-coroutines-test（`runTest()`, `StandardTestDispatcher`, `advanceUntilIdle()`）
- **設定ファイル**: なし（`build.gradle.kts` の `testOptions` で `isIncludeAndroidResources = true`）
- **実行コマンド**: `mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"`
- **参照元**: docs/tech-stack.md §「開発環境」, app/build.gradle.kts

### 既存テストのディレクトリ構成・命名パターン
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/ui/`
- **テストクラス名**: `{対象クラス名}{機能名}Test.kt`（例: `EditScreenViewModelRewriteBodyTest.kt`）
- **テストメソッド名**: `` TC-{TASK-ID}-{CATEGORY}{NUMBER} {日本語説明}` `` 形式
  - 例: `TC-0063-N01 rewriteBody 成功時 formState body が LLM 応答テキストに更新される`
  - カテゴリ: N=正常系, B=境界値, E=エラー系
- **参照元**: app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelRewriteBodyTest.kt

### テストユーティリティ・モック設定
- **共通テストデータ**: `processedContent()` ヘルパー関数（`ProcessedContent` インスタンス生成）
- **モック設定**: `coEvery { mockRewrite.rewrite(...) } returns LlmRewriteResult.Success(...)`
- **SharedFlow購読ヘルパー**: `collectErrorEvents(viewModel: EditScreenViewModel): Pair<MutableList<Int>, Job>`
  - `errorEvents` から messageResId を受信し、テスト終了時に `job.cancel()` で購読を終了
- **Dispatcher制御**: `Dispatchers.setMain(testDispatcher)` / `Dispatchers.resetMain()` のセットアップ/ティアダウン
- **参照元**: app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelRewriteBodyTest.kt §「ヘルパー」

### TASK-0068のテストケース（5ケース）

| TC ID | カテゴリ | テスト内容 | モック結果 | 期待値 | 信頼性 |
|-------|---------|-----------|-----------|--------|--------|
| **TC-0068-N01** | 正常系 | suggestTags()成功時に既存tagsTextに生成結果が追加される | `Success("tag2, tag3")` | tagsText: `"private, tag1, tag2, tag3"` | 🔵 REQ-302 |
| **TC-0068-N02** | 正常系 | 既存tagsTextが空文字の場合は生成結果のみがtagsTextに設定される | `Success("tag1, tag2")` | tagsText: `"tag1, tag2"` | 🔵 完了条件 |
| **TC-0068-E01** | エラー系 | suggestTags()失敗時にtagsTextが変更されず、errorEventsが発行される | `Failure(messageResId = R.string.error_llm_network)` | tagsText: 不変, errorEvents発行 | 🔵 EDGE-004 |
| **TC-0068-B01** | ローディング状態 | suggestTags()実行中はisSuggestingTagsがtrueになり、完了後falseに戻る | `CompletableDeferred` で制御 | 遷移: false→true→false | 🔵 REQ-201 |
| **TC-0068-B02** | 境界値 | sourceContentが空文字の状態でもsuggestTags()がガードされず実行される | `Success("tag")` 受信 | `rewrite()` が1回呼ばれる（空文字で呼ばれる） | 🔵 EDGE-101 |

**参照元**: docs/tasks/llm-memo-rewrite/TASK-0068.md §「単体テスト要件」

### E2E/UI テスト（後続タスク）
- **UI テスト**: TASK-0069「EditScreen『タグを提案』ボタン UI 追加」で UI 動作確認
- **統合テスト**: `app/src/androidTest/` での Compose UI Test（画面回転・ボタン押下・Toast表示等）
- **参照元**: docs/tasks/llm-memo-rewrite/TASK-0069.md

---

## 6. 注意事項

### 技術的制約

| 項目 | 制約内容 | 対応方法 |
|------|--------|---------|
| **sourceContent保持** | `EditScreenViewModel.initialize()` は`sourceContent`（テンプレート適用前）を必ず受け取る必要がある（REQ-406） | `MainActivity` で別変数として`ProcessedContent.body`（適用前）を退避し、`initialize()` の新規引数として渡す（既に TASK-0062 で実装） |
| **アプリ内固定プロンプト** | `suggestTags()` は テンプレート単位の`bodyLlmPrompt`ではなく、アプリ内統一の文字列を使用（テンプレートのカスタムプロンプトは不要） | `strings.xml` に `<string name="llm_tag_suggestion_prompt">…</string>` として定義し、`context.getString(R.string.llm_tag_suggestion_prompt)` で取得 |
| **既存タグの保持** | 生成結果は置換ではなく既存タグに追加（REQ-302） | `if (current.isBlank()) result.text else "$current, ${result.text}"` でカンマ区切り連結 |
| **空文字入力** | `sourceContent` が空文字でもボタンを非活性化せず、LLM呼び出しを実行（EDGE-101） | `bodyLlmPrompt` の有無のみをガード（`rewriteBody()` と同パターン）。`sourceContent` の空チェック不要 |
| **ローディング状態管理** | LLM呼び出し中は UI のボタンを非活性化し、`CircularProgressIndicator` を表示（REQ-201） | `EditFormState` に`isSuggestingTags: Boolean`を追加し、true/false で UI 側が制御（後続 TASK-0069で UI 実装） |

### セキュリティ・パフォーマンス要件

| 項目 | 要件 | 対応方法 |
|------|------|---------|
| **API キー暗号化** | LLM API キーは平文 DataStore に保存禁止（REQ-401, NFR-101） | `EncryptedSharedPreferences` に保存（既に TASK-0060で実装） |
| **タイムアウト** | LLM 呼び出しは 30 秒でタイムアウト（NFR-001, REQ-202） | Ktor Client の `HttpTimeout` プラグイン `requestTimeoutMillis = 30_000` を使用（既に TASK-0060で実装） |
| **エラーメッセージ** | API キー等機微情報をログ・クラッシュレポートに出力禁止（NFR-102） | 例外を捕捉し、種別のみを `LlmRewriteResult.Failure` にマッピング。ログ出力は機能レベルのメッセージのみ |
| **日本語ローカライズ** | ユーザー向けメッセージはすべて日本語（既存アプリパターン） | `strings.xml` に日本語文字列を定義し、`stringResource(id)` で UI に反映（エラーメッセージも日本語） |

### テスト実装の注意点

| 項目 | 注意点 |
|------|--------|
| **SharedFlow 購読タイミング** | `rewriteBody()` 呼び出し前に `errorEvents` を購読してから `advanceUntilIdle()` を実行。呼び出し後の購読ではイベント取りこぼしの可能性がある |
| **CompletableDeferred 完了制御** | ローディング状態の中間観測時は、`advanceUntilIdle()` の前に `isRewritingBody == true` を検証し、その後 `deferred.complete(result)` で完了させる |
| **Dispatchers.Main の復元** | `@After` で必ず `Dispatchers.resetMain()` を実行し、後続テストへの汚染を防ぐ |
| **Robolectric 設定** | `@Config(sdk = [34])` で API レベル 34 を指定（本プロジェクトの minSdk = 33, targetSdk = 36 に対応） |

### 開発ワークフロー（TDD）

1. **準備フェーズ**: このノートの内容確認 → タスク TASK-0068.md の詳細確認
2. **Red フェーズ**: `/tsumiki:tdd-red TASK-0068` で 5 つのテストケースをすべて実装（失敗状態で開始）
3. **Green フェーズ**: `/tsumiki:tdd-green TASK-0068` で最小実装により全テスト成功を目指す
4. **Refactor フェーズ**: `/tsumiki:tdd-refactor TASK-0068` でコード品質向上
5. **検証フェーズ**: `/tsumiki:tdd-verify-complete TASK-0068` で完了確認、`mise exec -- ./gradlew test` 全通過確認

**参照元**: docs/tasks/llm-memo-rewrite/TASK-0068.md §「実装手順」

---

## 関連ファイル一覧

### 要件・設計
- docs/spec/llm-memo-rewrite/requirements.md
- docs/spec/llm-memo-rewrite/user-stories.md
- docs/design/llm-memo-rewrite/architecture.md
- docs/design/llm-memo-rewrite/dataflow.md
- docs/design/llm-memo-rewrite/api-endpoints.md
- docs/design/llm-memo-rewrite/interfaces.kt

### タスク・計画
- docs/tasks/llm-memo-rewrite/overview.md
- docs/tasks/llm-memo-rewrite/TASK-0063.md（前提タスク、rewriteBody() 実装パターン参照）
- docs/tasks/llm-memo-rewrite/TASK-0068.md（本タスク定義）

### 実装参考
- app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt（rewriteBody() 実装）
- app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt（既存状態）
- app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt
- app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt
- app/src/main/res/values/strings.xml（日本語リソース文字列定義）

### テスト参考
- app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelRewriteBodyTest.kt（TASK-0063のテスト実装例）
- gradle/libs.versions.toml（テスト依存関係）
- app/build.gradle.kts（ビルド設定、testOptions）

---

**生成日**: 2026-07-08
**生成元**: tsumiki:tdd-tasknote TASK-0068
**ステータス**: TDD Red フェーズ開始準備完了
