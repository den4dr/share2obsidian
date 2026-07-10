---
name: llm-memo-rewrite-architecture
description: LLMによるメモ更改機能の追加 アーキテクチャ設計
metadata:
  type: project
---

# LLMによるメモ更改機能の追加 アーキテクチャ設計

**作成日**: 2026-07-05
**関連要件定義**: [requirements.md](../../spec/llm-memo-rewrite/requirements.md)
**ヒアリング記録**: [design-interview.md](design-interview.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: EARS要件定義書・設計文書・ユーザヒアリングを参考にした確実な設計
- 🟡 **黄信号**: EARS要件定義書・設計文書・ユーザヒアリングから妥当な推測による設計
- 🔴 **赤信号**: EARS要件定義書・設計文書・ユーザヒアリングにない推測による設計

---

## システム概要 🔵

**信頼性**: 🔵 *requirements.md REQ-001〜REQ-406より*

既存の `template-content-management` 実装（TASK-0043〜0054 完了）の上に、以下を追加する。

1. **本文（body）のLLMリライト（Must Have）**: EditScreen上の「メモを更改」ボタン押下で、共有/取得直後の元コンテンツ（`ProcessedContent.body`）とテンプレートに保存されたプロンプトをLLM APIへ送信し、応答で本文フィールドを即時上書きする
2. **タグ提案（Should Have）**: EditScreen上の「タグを提案」ボタン押下で、同じ元コンテンツを入力としてLLMがタグ候補を生成し、既存タグに追加する
3. **カスタムフィールドのLLM生成（Could Have）**: `FieldValueSource` に `LLM` を追加し、テンプレート編集画面でフィールドごとのプロンプトを設定可能にする
4. **LLM設定の追加**: SettingsScreenにAPIエンドポイントURL・APIキー（暗号化保存）・モデル名の入力欄を追加する

---

## アーキテクチャパターン 🔵

**信頼性**: 🔵 *design-interview.md Q1・Q2・既存アーキテクチャより*

- **パターン**: 既存の単一アクティビティ + Compose UI + MVVM + Repository を継続
- **DI**: Hilt（既存）。今回 `EditScreenViewModel` を素の `ViewModel()` から `@HiltViewModel` へ変更する（design-interview.md Q2）
- **非同期処理**: Kotlin Coroutines + `viewModelScope`（既存パターン踏襲）
- **HTTP通信**: Ktor Client (CIO エンジン) を新規導入（design-interview.md Q1）
- **シリアライズ**: kotlinx-serialization を新規導入（design-interview.md Q1）
- **暗号化ストレージ**: `androidx.security-crypto`（EncryptedSharedPreferences）を新規導入（REQ-401, NFR-101）

---

## 新規追加コンポーネント 🔵

**信頼性**: 🔵 *REQ-002, REQ-004, REQ-401, REQ-402・design-interview.md Q1〜Q3より*

| ファイル | 区分 | 役割 | 対応要件 |
|---------|------|------|---------|
| `data/llm/LlmSettings.kt` | **新規** | LLM API設定（endpointUrl/apiKey/model）を保持するドメインデータクラス | REQ-004 |
| `data/llm/LlmSettingsRepository.kt` | **新規** | LLM設定の読み書きインターフェース | REQ-004, REQ-401 |
| `data/llm/LlmSettingsRepositoryImpl.kt` | **新規** | DataStore（endpointUrl/model）+ EncryptedSharedPreferences（apiKey）による実装 | REQ-401, NFR-101 |
| `data/llm/LlmRewriteResult.kt` | **新規** | LLM呼び出し結果を表す sealed class（成功/失敗種別） | EDGE-001〜004 |
| `data/llm/LlmRewriteRepository.kt` | **新規** | LLM呼び出しインターフェース | REQ-002, REQ-402 |
| `data/llm/LlmRewriteRepositoryImpl.kt` | **新規** | Ktor Client による OpenAI互換 Chat Completions 実装 | REQ-402, NFR-001 |
| `data/llm/dto/ChatCompletionDto.kt` | **新規** | リクエスト/レスポンスの kotlinx.serialization DTO | REQ-402 |
| `di/LlmModule.kt` | **新規** | Hilt Module（HttpClient・EncryptedSharedPreferences・各Repositoryの提供） | REQ-401, REQ-402 |
| `ui/SettingsViewModel.kt` | **変更** | `LlmSettingsRepository` を追加注入し、LLM設定の状態・更新関数を追加 | REQ-004 |
| `ui/SettingsScreen.kt` | **変更** | LLM設定セクション（endpointUrl/apiKey/model入力欄）を追加 | REQ-004 |

---

## 変更が必要な既存コンポーネント 🔵

**信頼性**: 🔵 *requirements.md 全体・既存実装調査より*

| ファイル | 変更内容 | 対応要件 |
|---------|---------|---------|
| `domain/model/Template.kt` | `bodyLlmPrompt: String = ""` を追加 | REQ-101, REQ-102 |
| `domain/model/TemplateField.kt` | `llmPrompt: String = ""` を追加（`valueSource == LLM` の場合のみ使用） | REQ-104, REQ-303 |
| `domain/model/FieldValueSource.kt` | `LLM` を追加 | REQ-303 |
| `domain/model/CustomFieldState.kt` | `valueSource: FieldValueSource`, `llmPrompt: String` を追加（EditScreen側でLLM生成ボタン表示判定に使用） | REQ-104, REQ-304 |
| `data/db/TemplateEntity.kt` | `bodyLlmPrompt: String = ""` カラム追加 | REQ-101 |
| `data/db/TemplateFieldEntity.kt` | `llmPrompt: String = ""` カラム追加 | REQ-104 |
| `data/db/AppDatabase.kt` | version 2→3、`MIGRATION_2_3` 追加 | REQ-101, REQ-104, NFR-001（既存パターン踏襲） |
| `data/repository/TemplateRepositoryImpl.kt` | `toDomain()`/`toEntity()` に `bodyLlmPrompt`/`llmPrompt` のマッピングを追加 | REQ-101, REQ-104 |
| `TemplateApplicator.kt` | `buildCustomFields()` に `FieldValueSource.LLM -> ""`（初期値は空。実際の生成はEditScreen上のボタン押下時） | REQ-304 |
| `MainActivity.kt` | `defaultTemplate.bodyLlmPrompt` と共有元テキスト（`processed.body`、テンプレート解決前）を `viewModel.initialize()` に渡すよう変更 | REQ-002, REQ-406 |
| `ui/EditScreenViewModel.kt` | `@HiltViewModel` 化。`sourceContent`（LLM入力用の元コンテンツ）・`bodyLlmPrompt` を保持。`rewriteBody()`/`suggestTags()`/`generateCustomFieldValue(index)`・`errorEvents: SharedFlow<Int>` を追加 | REQ-002, REQ-003, REQ-201, REQ-202, REQ-301, REQ-302, REQ-406 |
| `ui/EditFormState.kt` | `isRewritingBody`, `isSuggestingTags`, `rewriteBodyEnabled`, `generatingFieldIndex` を追加 | REQ-102, REQ-201 |
| `ui/EditScreen.kt` | 「メモを更改」ボタン（body横）、「タグを提案」ボタン（tags横）、カスタムフィールドの「生成」ボタン、ローディング表示、`errorEvents` 購読によるToast表示を追加 | REQ-001, REQ-103, REQ-104, REQ-201, NFR-201, NFR-202 |
| `ui/template/TemplateEditViewModel.kt` | `TemplateEditUiState` に `bodyLlmPrompt` 追加、`updateBodyLlmPrompt()` 追加。`TemplateFieldEditState` に `llmPrompt` 追加 | REQ-101, REQ-104 |
| `ui/template/TemplateEditScreen.kt` | 本文用LLMプロンプト入力欄を追加。`FieldAddDialog` の値取得方法ラジオボタンに「LLM」を追加し、選択時にプロンプト入力欄を表示 | REQ-104, REQ-405 |
| `gradle/libs.versions.toml` / `app/build.gradle.kts` | Ktor Client (CIO)・kotlinx-serialization・androidx-security-crypto の依存関係追加 | REQ-402, REQ-401 |
| `res/values/strings.xml` | ボタンラベル・エラーメッセージ等の新規文字列追加（日本語） | NFR-201 |

---

## `ProcessedContent` 保持設計（重要な変更点） 🔵

**信頼性**: 🔵 *REQ-002, REQ-302, REQ-406・ヒアリングQ21〜Q24より*

### 課題

現状 `MainActivity.onCreate()` は次の順序で処理する（既存実装確認済み）:

```kotlin
val processed = /* ContentProcessor.process() の結果 */
val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
viewModel.initialize(processed.copy(body = resolvedBody), config, customFields)
```

`viewModel.initialize()` に渡される `ProcessedContent.body` は、テンプレート適用（`{{content}}` 解決）**後**の値で上書きされてしまうため、元の共有コンテンツ（LLM入力として必要な値、REQ-002/REQ-302）は `EditScreenViewModel` に到達しない。

### 解決方針

`MainActivity` は「テンプレート適用前の `processed.body`」を別変数として保持し、`initialize()` に追加引数として渡す。

```kotlin
val processed = /* ContentProcessor.process() の結果 */
val sourceContent = processed.body  // 🔵 REQ-406: テンプレート適用前の元コンテンツを退避
val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
viewModel.initialize(
    processed = processed.copy(body = resolvedBody),
    config = config,
    customFields = customFields,
    sourceContent = sourceContent,           // 🔵 新規引数
    bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty(),  // 🔵 新規引数
)
```

`EditScreenViewModel` はこの `sourceContent` / `bodyLlmPrompt` を `EditFormState` に含めず（ユーザーが直接編集する項目ではないため）、ViewModel内部のプライベートプロパティとして保持する。

---

## LLM設定管理設計 🔵

**信頼性**: 🔵 *REQ-004, REQ-401, NFR-101・design-interview.md Q1より*

- `NoteSettings`（vault/folder）とは責務を分離し、新規 `LlmSettings`（endpointUrl/apiKey/model）を新設する
- `endpointUrl`/`model` は既存の DataStore Preferences パターン（`NoteSettingsRepositoryImpl` と同様）を踏襲
- `apiKey` のみ `EncryptedSharedPreferences`（`androidx.security-crypto` の `MasterKey` + `EncryptedSharedPreferences.create()`）に分離保存する（REQ-401: 平文DataStoreへの保存禁止）
- `LlmSettingsRepositoryImpl.getSettings()` は DataStore の Flow と EncryptedSharedPreferences の変更通知（`SharedPreferences.OnSharedPreferenceChangeListener` を `callbackFlow` でラップ）を `combine()` して単一の `Flow<LlmSettings>` として公開する 🟡（実装パターンの詳細はヒアリング対象外の妥当な推測）

```kotlin
data class LlmSettings(
    val endpointUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)
```

---

## LLMリクエスト/レスポンス設計 🔵

**信頼性**: 🔵 *REQ-402・ヒアリングQ5（OpenAI互換）より*

- OpenAI互換 Chat Completions 形式（`POST {endpointUrl}`、`Authorization: Bearer {apiKey}`）
- リクエストボディ: `model`, `messages: [{role: "system", content: プロンプト}, {role: "user", content: 元コンテンツ}]` 🟡（system/userロールの割り当てはOpenAI Chat Completionsの一般的な慣習からの妥当な推測。直接ヒアリングはしていない）
- レスポンス: `choices[0].message.content` を書き換え結果として使用する 🔵 *OpenAI Chat Completions API仕様より*
- タイムアウト: `HttpTimeout` プラグインで `requestTimeoutMillis = 30_000`（REQ-202, NFR-001）
- 詳細は [api-endpoints.md](api-endpoints.md) を参照

---

## `EditScreenViewModel` 設計変更 🔵

**信頼性**: 🔵 *REQ-002, REQ-003, REQ-201, REQ-202, REQ-301, REQ-302, REQ-406・design-interview.md Q2・Q3より*

### 追加する状態・依存関係

- `@HiltViewModel` 化し、`LlmRewriteRepository` / `LlmSettingsRepository` をコンストラクタ注入する
- `private var sourceContent: String = ""`: LLM入力用の元コンテンツ（`initialize()` で設定、ユーザー編集の影響を受けない）
- `private var bodyLlmPrompt: String = ""`: テンプレートの本文用プロンプト（`initialize()` で設定）
- `private val _errorEvents = MutableSharedFlow<Int>()`: エラー時に string resource ID を1回限り発行するイベント（design-interview.md Q3）
- `val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()`

### 追加するメソッド

```kotlin
fun rewriteBody() {
    if (bodyLlmPrompt.isBlank()) return  // REQ-102: プロンプト未設定時はボタン非活性のため通常到達しないが防御的にガード
    viewModelScope.launch {
        _formState.value = _formState.value.copy(isRewritingBody = true)  // REQ-201
        val settings = llmSettingsRepository.getSettings().first()
        when (val result = llmRewriteRepository.rewrite(settings, bodyLlmPrompt, sourceContent)) {
            is LlmRewriteResult.Success ->
                _formState.value = _formState.value.copy(body = result.text)  // REQ-003: 即時上書き
            is LlmRewriteResult.Failure ->
                _errorEvents.emit(result.messageResId)  // NFR-201: Toast表示用
        }
        _formState.value = _formState.value.copy(isRewritingBody = false)
    }
}

fun suggestTags() {
    viewModelScope.launch {
        _formState.value = _formState.value.copy(isSuggestingTags = true)
        val settings = llmSettingsRepository.getSettings().first()
        val prompt = /* アプリ内固定プロンプト（string resource） */ ""
        when (val result = llmRewriteRepository.rewrite(settings, prompt, sourceContent)) {
            is LlmRewriteResult.Success -> {
                val current = _formState.value.tagsText
                val merged = if (current.isBlank()) result.text else "$current, ${result.text}"
                _formState.value = _formState.value.copy(tagsText = merged)  // REQ-302: 既存タグに追加
            }
            is LlmRewriteResult.Failure -> _errorEvents.emit(result.messageResId)
        }
        _formState.value = _formState.value.copy(isSuggestingTags = false)
    }
}
```

- `rewriteBody()`/`suggestTags()` はいずれも `sourceContent` を入力とする（`formState.body` ではない。REQ-002/REQ-302/REQ-406）
- `rewriteBodyEnabled`（`EditFormState` のプロパティ）は `initialize()` 時に `bodyLlmPrompt.isNotBlank()` から算出し格納する（REQ-102）
- `sourceContent` が空文字であっても `rewriteBody()`/`suggestTags()` はガードせず実行する（EDGE-101）。ガードするのは `bodyLlmPrompt` の有無のみ

---

## 非機能要件の実現方法

### パフォーマンス（NFR-001） 🔵

**信頼性**: 🔵 *NFR-001・Ktor HttpTimeout公式パターンより*

- Ktor Client に `HttpTimeout` プラグインを `install` し `requestTimeoutMillis = 30_000` を設定する
- タイムアウト発生時は `HttpRequestTimeoutException` を捕捉し `LlmRewriteResult.Failure.Timeout` に変換する

### セキュリティ（REQ-401, NFR-101, NFR-102） 🔵

**信頼性**: 🔵 *REQ-401, NFR-101, NFR-102・ヒアリングより*

- APIキーは `EncryptedSharedPreferences` に保存し、平文DataStoreには保存しない
- APIキーはログ・例外メッセージ・クラッシュレポートに出力しない。`LlmRewriteRepositoryImpl` 内で例外を捕捉する際、例外オブジェクトをそのままログ出力せず、種別のみを `LlmRewriteResult.Failure` にマッピングする

### ユーザビリティ（NFR-201, NFR-202） 🔵

**信頼性**: 🔵 *NFR-201, NFR-202・design-interview.md Q3より*

- LLM呼び出し中は `isRewritingBody`/`isSuggestingTags` に応じて `CircularProgressIndicator` をボタン内に表示し、ボタンを非活性化する 🟡（UIコンポーネントの具体的な表現はMaterial3の一般的な慣習からの妥当な推測）
- エラー時は `errorEvents` 経由でEditScreen側が `Toast.makeText(context, stringResource(id), Toast.LENGTH_LONG)` を表示する（既存の `ActivityNotFoundException` Toastパターンを踏襲）

---

## 技術的制約

- **Ktor CIOエンジンのAndroid対応**: CIOエンジンは純Kotlin実装でAndroidでも動作するが、`minSdk 33` で問題なく動作することは公式ドキュメント上確認できるものの、本プロジェクトでの実機検証は未実施 🟡 *設計時点では未検証*
- **EncryptedSharedPreferences の非同期化**: `EncryptedSharedPreferences` のAPIは同期的（`SharedPreferences`ベース）なため、Repository実装内で `Dispatchers.IO` に明示的に切り替える必要がある 🔵 *AndroidX Security 公式パターンより*
- **Room DROP COLUMN**: 本マイグレーションはカラム追加のみで `DROP COLUMN` を使用しないため、SQLiteバージョン制約の影響を受けない 🔵
- **単一Activity維持**: 既存アーキテクチャを継続。NavController/Fragmentは不使用 🔵

---

## 関連文書

- **データフロー**: [dataflow.md](dataflow.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **DBスキーマ**: [database-schema.kt](database-schema.kt)
- **外部API連携仕様**: [api-endpoints.md](api-endpoints.md)
- **要件定義**: [requirements.md](../../spec/llm-memo-rewrite/requirements.md)

## 信頼性レベルサマリー

- 🔵 青信号: 29件 (88%)
- 🟡 黄信号: 4件 (12%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: ✅ 高品質
