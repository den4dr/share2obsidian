# TASK-0060 開発コンテキストノート

**タスクID**: TASK-0060  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: LlmRewriteRepository・LlmRewriteRepositoryImpl実装

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **最低SDK**: API 33 (Android 13)
- **ターゲットSDK**: API 36
- **Java互換性**: 11

### プロジェクト構成
- **アーキテクチャパターン**: 単一アクティビティ + Compose UI + MVVM + Repository
- **DI**: Hilt（既存パターン踏襲）
- **非同期処理**: Kotlin Coroutines + viewModelScope
- **HTTP通信**: Ktor Client (CIO エンジン) ✨ **本タスク対象**
- **シリアライズ**: kotlinx-serialization
- **エラーマッピング**: OpenAI互換 Chat Completions API仕様に基づく

### 依存関係（既にインストール済み）
- `Ktor Client (CIO)` - HTTP通信用
- `Ktor Client Content Negotiation` - JSON Content-Type交渉用
- `Ktor Serialization kotlinx.json` - Ktor用 JSON シリアライザー
- `kotlinx-serialization` - JSON シリアライズ用
- `androidx-security-crypto` - APIキー暗号化ストレージ用
- `JUnit 4` - ユニットテスト用
- `MockK` - モック・スタブ用（リポジトリテスト）
- `Kotlin Coroutines Test` - 非同期テスト用
- `Robolectric` - Android リソース依存テスト用

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ
- `app/build.gradle.kts` - 依存関係定義

---

## 2. 開発ルール

### TDD開発フロー（推奨）
1. `/tsumiki:tdd-requirements TASK-0060` - 詳細要件定義（REQ-402, NFR-001, EDGE-001〜004）
2. `/tsumiki:tdd-testcases` - テストケース作成（5個の単体テスト + 2個の統合テスト）
3. `/tsumiki:tdd-red` - テスト実装（失敗状態）
4. `/tsumiki:tdd-green` - 最小実装（テスト通過）
5. `/tsumiki:tdd-refactor` - リファクタリング（品質向上）
6. `/tsumiki:tdd-verify-complete` - 品質確認・完了

### コーディング規約

#### インターフェース定義
```kotlin
// app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt
interface LlmRewriteRepository {
    suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult
}
```

#### 実装パターン
- **メソッド**: 非同期処理のため `suspend fun` で定義（Coroutines対応）
- **リクエスト送信**: Ktor Client (CIO) + `httpClient.post()` で OpenAI互換 Chat Completions APIに送信
- **エラーハンドリング**: 例外キャッチ → `LlmRewriteResult.Failure` へのマッピング
- **ログ出力**: APIキーを含む例外オブジェクト・リクエストヘッダはログに出力禁止（NFR-102）

#### リクエスト構築パターン
```kotlin
val response: ChatCompletionResponseDto = httpClient.post(settings.endpointUrl) {
    header(HttpHeaders.Authorization, "Bearer ${settings.apiKey}")
    contentType(ContentType.Application.Json)
    setBody(
        ChatCompletionRequestDto(
            model = settings.model,
            messages = listOf(
                ChatMessageDto(role = "system", content = prompt),
                ChatMessageDto(role = "user", content = content),
            ),
        )
    )
}.body()
```

#### エラーマッピング例
| 例外 | `LlmRewriteResult.Failure` |
|-----|---------------------------|
| `HttpRequestTimeoutException` | `Timeout` |
| `ClientRequestException` (401/403) | `AuthError` |
| `IOException` | `NetworkError` |
| `choices` 空 / `content` 空 | `EmptyOrInvalidResponse` |
| その他予期しない例外 | `Unknown` |

#### テスト命名規約（既存パターンから）
- テストファイル: `${対象クラス}Test.kt`
- テストメソッド: ```fun `説明的なテスト名`() {}``` （バッククォートで日本語使用可）
- Arrange-Act-Assert（AAA）パターンで構成

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0060.md` - タスク定義・要件
- `docs/design/llm-memo-rewrite/architecture.md` - アーキテクチャ・コンポーネント設計
- `docs/design/llm-memo-rewrite/api-endpoints.md` - API仕様・エラーマッピング
- 既存テスト例: `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`

---

## 3. 関連実装

### 既に実装済みの前提条件（TASK-0059完了）

#### 1. LlmRewriteResult.kt
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt`

**型定義**:
```kotlin
sealed class LlmRewriteResult {
    data class Success(val text: String) : LlmRewriteResult()
    
    sealed class Failure(open val messageResId: Int) : LlmRewriteResult() {
        data class NetworkError(override val messageResId: Int) : Failure(messageResId)
        data class AuthError(override val messageResId: Int) : Failure(messageResId)
        data class Timeout(override val messageResId: Int) : Failure(messageResId)
        data class EmptyOrInvalidResponse(override val messageResId: Int) : Failure(messageResId)
        data class Unknown(override val messageResId: Int) : Failure(messageResId)
    }
}
```

#### 2. ChatCompletionDto.kt（DTO定義）
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`

**OpenAI互換形式**:
```kotlin
@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
)

@Serializable
data class ChatMessageDto(
    val role: String,  // "system" or "user"
    val content: String,
)

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto>,
)

@Serializable
data class ChoiceDto(
    val message: MessageDto,
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String?,
)
```

#### 3. LlmSettings.kt（設定データクラス）
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt`

```kotlin
data class LlmSettings(
    val endpointUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)
```

#### 4. LlmSettingsRepository（インターフェース）
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt`

```kotlin
interface LlmSettingsRepository {
    fun getSettings(): Flow<LlmSettings>
    suspend fun saveEndpointUrl(url: String)
    suspend fun saveApiKey(key: String)
    suspend fun saveModel(model: String)
}
```

#### 5. LlmSettingsRepositoryImpl（設定リポジトリ実装）
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`

DataStore（endpointUrl/model）+ EncryptedSharedPreferences（apiKey）による実装

### 本タスクで実装する新規ファイル

#### 1. LlmRewriteRepository.kt（インターフェース）
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`

LLM呼び出し処理のインターフェース定義

#### 2. LlmRewriteRepositoryImpl.kt（実装）
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`

Ktor Client (CIO) による OpenAI互換 Chat Completions API呼び出し実装

### 関連する既存実装

#### 既存リポジトリパターン
- `app/src/main/java/com/den4dr/share2Obsidian/data/datastore/NoteSettingsRepositoryImpl.kt` - DataStore リポジトリパターン参考
- `app/src/main/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImpl.kt` - Hilt導入パターン参考

#### 既存テストパターン
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt` - Robolectric + DataStore テストパターン
- `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt` - Kotlin テストパターン（命名規約）

---

## 4. 設計文書

### 要件定義
- **位置**: `docs/spec/llm-memo-rewrite/requirements.md`
- **対応要件**: REQ-402（OpenAI互換形式）, NFR-001（30秒タイムアウト）, NFR-102（ログ出力禁止）, EDGE-001〜004（エラーマッピング）
- **内容**: 機能要件・非機能要件・エッジケース・制約要件

### アーキテクチャ設計
- **位置**: `docs/design/llm-memo-rewrite/architecture.md`
- **関連項目**: 
  - 「新規追加コンポーネント」表（TASK-0060対応コンポーネント）
  - 「LLMリクエスト/レスポンス設計」（API形式・タイムアウト設定）

### API連携仕様
- **位置**: `docs/design/llm-memo-rewrite/api-endpoints.md`
- **重要項目**:
  - 「リクエスト仕様」: messages配列のrole/contentの詳細
  - 「エラーレスポンスとマッピング」: 例外 → LlmRewriteResult の対応表
  - 「タイムアウト設定」: `HttpTimeout { requestTimeoutMillis = 30_000 }`

### タスク定義
- **位置**: `docs/tasks/llm-memo-rewrite/TASK-0060.md`
- **重要項目**:
  - 実装詳細セクション（リクエスト構築・エラーマッピングの具体例）
  - 単体テスト要件（5ケース）
  - 統合テスト要件（2ケース）

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### ユニットテスト（JUnit 4 + MockK）
- **設定ファイル**: `app/build.gradle.kts`
  - `testImplementation(libs.junit)`
  - `testImplementation(libs.mockk)`
  - `testImplementation(libs.kotlinx.coroutines.test)`
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/data/llm/`
- **テストランナー**: JUnit 4（Robolectric組み込み対応）

#### 統合テスト（Androidテスト + Ktor MockEngine）
- **テストランナー**: `androidx.test.runner.AndroidJUnitRunner`
- **テストディレクトリ**: `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/`
- **Ktor MockEngine**: リクエスト検証・遅延レスポンス実装用

### 既存テストのディレクトリ構成

```
app/src/test/java/com/den4dr/share2Obsidian/
├── data/
│   └── llm/
│       ├── ChatCompletionDtoTest.kt（TASK-0059）
│       ├── LlmRewriteResultTest.kt（TASK-0059）
│       ├── LlmSettingsRepositoryImplTest.kt（TASK-0058）
│       └── LlmRewriteRepositoryImplTest.kt（← TASK-0060で新規）
└── ...

app/src/androidTest/java/com/den4dr/share2Obsidian/
├── data/
│   └── llm/
│       └── LlmRewriteRepositoryIntegrationTest.kt（← TASK-0060で新規）
└── ...
```

### テストユーティリティ・モック設定

#### MockK による HttpClient モック
```kotlin
val mockHttpClient = mockk<HttpClient>()
coEvery { 
    mockHttpClient.post<ChatCompletionResponseDto>(any()) { ... } 
} returns response
```

#### Ktor MockEngine による統合テスト
```kotlin
val engine = MockEngine { request ->
    // リクエスト検証・カスタムレスポンス返却
    respond(...)
}
val httpClient = HttpClient(engine)
```

#### 既存フェイク実装パターン
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt` の `FakeSharedPreferences` クラスを参考
- SharedPreferences.OnSharedPreferenceChangeListener の mock方法

### 参考となる既存テストファイル

#### ユニットテスト例（MockK）
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`
  - Robolectric + DataStore テストパターン
  - FakeSharedPreferences による EncryptedSharedPreferences 代替
  - runBlocking による suspend 関数テスト

#### テストケース設計パターン
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResultTest.kt`
  - 成功系・失敗系の代表テスト
  - data class の等価性確認
  - sealed class の exhaustive 分岐確認

### テスト実行コマンド

```bash
# ユニットテスト（ローカル JVM）
mise exec -- ./gradlew test

# 計器テスト（Android デバイス/エミュレータ）
mise exec -- ./gradlew connectedAndroidTest

# 特定のテストクラスのみ実行
mise exec -- ./gradlew test --tests LlmRewriteRepositoryImplTest
```

### テストカバレッジ期待値
- **対象**: `LlmRewriteRepositoryImpl` の全 public メソッド
- **期待カバレッジ**: 正常系（成功）+ エラー系（全5種）+ 統合テスト（リクエスト形式・タイムアウト）

---

## 6. 注意事項

### 技術的制約

#### APIキー取り扱い
- **厳禁**: APIキーをログ・クラッシュレポートに出力（NFR-102）
- **方法**: 例外オブジェクト全体のログはNG。定型メッセージのみ出力

#### タイムアウト設定
- **設定値**: 30秒（30,000ms）
- **位置**: `HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = 30_000 } }`
- **対応要件**: NFR-001, REQ-202

#### メッセージ順序（OpenAI互換）
- **messages[0]**: role = "system"（プロンプト）
- **messages[1]**: role = "user"（コンテンツ）
- **参考**: `docs/design/llm-memo-rewrite/api-endpoints.md` リクエスト仕様表

### セキュリティ・パフォーマンス要件

#### エラーログ出力ルール
- ❌ APIキー、リクエストヘッダ情報を含む例外オブジェクトのログ出力
- ✅ 「認証エラーが発生しました」等の定型エラーメッセージのみログ出力

#### キャンセルとクリーンアップ
- Coroutines スコープ内での実行を保証（viewModelScope推奨）
- リソースリーク回避（HttpClient のインスタンス再利用）

### 参考ドキュメント関連図

**データフロー** (TASK-0060による追加箇所):
```
EditScreenViewModel.rewriteBody()
  ↓
LlmRewriteRepository.rewrite()  ← 本タスク実装箇所
  ├→ HttpClient.post(endpointUrl)
  ├→ リクエスト: { model, messages: [system, user] }
  ├→ レスポンス解析: ChatCompletionResponseDto
  └→ 結果マッピング: LlmRewriteResult
       ├→ Success(text)
       └→ Failure (NetworkError, AuthError, Timeout, EmptyOrInvalidResponse, Unknown)
  ↓
UIレイヤー: Toast表示 / EditScreen更新
```

**参照元**: `docs/design/llm-memo-rewrite/dataflow.md`

---

## 実装チェックリスト（目安）

### 実装フェーズ
- [ ] LlmRewriteRepository インターフェース定義
- [ ] LlmRewriteRepositoryImpl クラス定義・HttpClient依存注入
- [ ] rewrite() メソッド実装
  - [ ] リクエスト構築（model, messages配列）
  - [ ] リクエスト送信（post + Authorization ヘッダ）
  - [ ] レスポンス解析
  - [ ] 空応答チェック
  - [ ] 例外マッピング（5種類）
- [ ] タイムアウト設定（30秒）

### テストフェーズ
- [ ] 単体テスト5ケース（MockK + httpClient mock）
  - [ ] TC-1: 正常応答 → Success
  - [ ] TC-2: 401/403 → AuthError
  - [ ] TC-3: Timeout例外 → Timeout
  - [ ] TC-4: IOException → NetworkError
  - [ ] TC-5: 空応答 → EmptyOrInvalidResponse
- [ ] 統合テスト2ケース（MockEngine）
  - [ ] TC-1: リクエスト形式検証
  - [ ] TC-2: タイムアウト検証

---

**作成日**: 2026-07-06 by tsumiki:tdd-tasknote  
**最終確認**: TASK-0060 開発開始前
