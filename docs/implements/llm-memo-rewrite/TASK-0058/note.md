# TASK-0058 開発コンテキストノート

**タスクID**: TASK-0058  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: LlmSettings・LlmSettingsRepository・LlmSettingsRepositoryImpl実装

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **最低SDK**: API 33 (Android 13)
- **ターゲットSDK**: API 36
- **Java互換性**: 11
- **Compose BOM**: 2024.09.00

### アーキテクチャパターン
- **設定管理**: DataStore Preferences（endpointUrl/model）+ EncryptedSharedPreferences（apiKey）
- **Repository**: リポジトリパターン（既存 `NoteSettingsRepository` 踏襲）
- **Flow**: Kotlin Coroutines Flow（DataStore + EncryptedSharedPreferences の `combine()` でマージ）
- **DI**: Hilt（新規 Module 追加）

### 関連技術
- **データストレージ**: 
  - `androidx.datastore:datastore-preferences` - DataStore Preferences（endpointUrl/model）
  - `androidx.security:security-crypto` - EncryptedSharedPreferences（apiKey）
- **テストフレームワーク**: JUnit 4 + Robolectric（DataStore テスト）+ MockK
- **Gradle**: AGP 9.1.0、version catalog (`gradle/libs.versions.toml`)

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ・Compose BOM版ピン
- `app/build.gradle.kts` - Gradle設定・テスト依存関係
- `app/src/main/java/com/den4dr/share2Obsidian/di/DataStoreModule.kt` - 既存DataStore提供パターン

---

## 2. 開発ルール

### TDD開発フロー
1. `/tsumiki:tdd-requirements TASK-0058` - 詳細要件定義
2. `/tsumiki:tdd-testcases` - テストケース作成（3個の単体テスト + 1個の統合テスト）
3. `/tsumiki:tdd-red` - テスト実装（失敗状態）
4. `/tsumiki:tdd-green` - 最小実装（テスト通過）
5. `/tsumiki:tdd-refactor` - リファクタリング（品質向上）
6. `/tsumiki:tdd-verify-complete` - 品質確認・完了

### テスト実行コマンド
```bash
# ユニットテスト実行（Repository, DataStore テスト）
mise exec -- ./gradlew test

# 特定テストクラス実行
mise exec -- ./gradlew test --tests "*LlmSettingsRepository*"

# インストルメント化テスト実行（EncryptedSharedPreferences実装テスト）
mise exec -- ./gradlew connectedAndroidTest

# 全体チェック
mise exec -- ./gradlew clean lint build test connectedAndroidTest
```

### DataStore・EncryptedSharedPreferences実装ルール
- **DataStore パターン**: 既存 `NoteSettingsRepositoryImpl` の実装パターン踏襲（`stringPreferencesKey()` + `map()` + `edit()`）
- **EncryptedSharedPreferences**: `androidx.security-crypto` の `MasterKey.Builder()` + `EncryptedSharedPreferences.create()` で初期化
- **Flow合成**: `dataStore.data.map().combine(encryptedApiKeyFlow())` で 2つのソースをマージ
- **変更通知**: EncryptedSharedPreferences は `SharedPreferences.OnSharedPreferenceChangeListener` を `callbackFlow` でラップ（非同期化）
- **非同期化**: `saveApiKey()` は `withContext(Dispatchers.IO)` で実行（EncryptedSharedPreferences は同期API）

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0058.md` - 完了条件・実装詳細
- `docs/design/llm-memo-rewrite/architecture.md` - LLM設定管理設計・技術的制約
- `docs/design/llm-memo-rewrite/interfaces.kt` - LlmSettings・LlmSettingsRepository 型定義

---

## 3. 関連実装

### 参考実装（既存）

#### 1. NoteSettingsRepositoryImpl.kt（参考パターン）
**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/data/datastore/NoteSettingsRepositoryImpl.kt`

**パターン**: DataStore Preferences 読み書き（単一ソース）
```kotlin
internal val VAULT_KEY = stringPreferencesKey("vault")
internal val FOLDER_KEY = stringPreferencesKey("folder")

class NoteSettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : NoteSettingsRepository {
    override fun getSettings(): Flow<NoteSettings> = dataStore.data.map { prefs ->
        NoteSettings(vault = prefs[VAULT_KEY] ?: "", folder = prefs[FOLDER_KEY] ?: "")
    }
    override suspend fun saveVault(vault: String) { dataStore.edit { it[VAULT_KEY] = vault } }
}
```

**本タスクとの対応**:
- `endpointUrl`/`model` の保存・読み出しは同じパターンで実装
- `apiKey` のみ EncryptedSharedPreferences に分離

#### 2. DataStoreModule.kt（参考パターン）
**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/di/DataStoreModule.kt`

**パターン**: Hilt Module で DataStore 提供
```kotlin
private val Context.noteSettingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "note_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideNoteSettingsRepository(
        @ApplicationContext context: Context,
    ): NoteSettingsRepository = NoteSettingsRepositoryImpl(context.noteSettingsDataStore)
}
```

**本タスクとの対応**:
- 新規 `LlmModule` または既存 `DataStoreModule` の拡張で LlmSettingsRepository を提供
- `llmSettingsDataStore` + `encryptedPrefs` の両方を Hilt で管理

### 新規実装対象ファイル

#### 1. LlmSettings.kt
**内容**: LLM API設定を保持するドメインモデル
**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt`

**信頼性**: 🔵 REQ-004, REQ-401・interfaces.ktより

**構造**:
```kotlin
data class LlmSettings(
    val endpointUrl: String = "",  // OpenAI互換 Chat Completions エンドポイント
    val apiKey: String = "",       // 暗号化ストレージ保存
    val model: String = "",        // 使用モデル名
)
```

#### 2. LlmSettingsRepository.kt
**内容**: LLM設定の読み書きインターフェース
**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt`

**信頼性**: 🔵 interfaces.ktより

**メソッド**:
- `getSettings(): Flow<LlmSettings>` - DataStore・EncryptedSharedPreferences双方の変更を通知
- `saveEndpointUrl(url: String)` - DataStore に保存
- `saveApiKey(apiKey: String)` - EncryptedSharedPreferences に保存
- `saveModel(model: String)` - DataStore に保存

#### 3. LlmSettingsRepositoryImpl.kt
**内容**: DataStore + EncryptedSharedPreferences による実装
**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`

**信頼性**: 🟡 architecture.md「LLM設定管理設計」（実装パターンの詳細は妥当な推測）

**実装詳細**:
- `dataStore.data.map()` で (endpointUrl, model) をタプル化
- `encryptedApiKeyFlow()` で EncryptedSharedPreferences の変更通知を Flow 化（`callbackFlow` + `OnSharedPreferenceChangeListener`）
- `.combine()` で 2つの Flow をマージ → 単一 `Flow<LlmSettings>`
- `saveApiKey()` は `withContext(Dispatchers.IO)` で実行（EncryptedSharedPreferences は同期API）

```kotlin
internal val ENDPOINT_URL_KEY = stringPreferencesKey("llm_endpoint_url")
internal val MODEL_KEY = stringPreferencesKey("llm_model")
internal const val API_KEY_KEY = "llm_api_key"

class LlmSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val encryptedPrefs: SharedPreferences,  // EncryptedSharedPreferences
) : LlmSettingsRepository {
    override fun getSettings(): Flow<LlmSettings> =
        dataStore.data.map { prefs ->
            (prefs[ENDPOINT_URL_KEY] ?: "") to (prefs[MODEL_KEY] ?: "")
        }.combine(encryptedApiKeyFlow()) { (endpointUrl, model), apiKey ->
            LlmSettings(endpointUrl = endpointUrl, apiKey = apiKey, model = model)
        }

    private fun encryptedApiKeyFlow(): Flow<String> = callbackFlow {
        trySend(encryptedPrefs.getString(API_KEY_KEY, "") ?: "")
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == API_KEY_KEY) trySend(prefs.getString(API_KEY_KEY, "") ?: "")
        }
        encryptedPrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { encryptedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun saveEndpointUrl(url: String) {
        dataStore.edit { it[ENDPOINT_URL_KEY] = url }
    }

    override suspend fun saveApiKey(apiKey: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(API_KEY_KEY, apiKey).apply()
        }
    }

    override suspend fun saveModel(model: String) {
        dataStore.edit { it[MODEL_KEY] = model }
    }
}
```

#### 4. LlmModule.kt（新規作成 or 既存 DataStoreModule 拡張）
**内容**: Hilt DI 設定（HttpClient・EncryptedSharedPreferences・各Repository）
**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/di/LlmModule.kt` （新規）または `DataStoreModule.kt` （拡張）

**信頼性**: 🔵 REQ-401, REQ-402・Hilt公式パターンより

**責務**:
- `HttpClient` 提供（Ktor Client CIO + HttpTimeout(30_000ms)）
- `EncryptedSharedPreferences` 提供（MasterKey + AES256_GCM/AES256_SIV）
- `LlmSettingsRepository` 提供（`llmSettingsDataStore` + `encryptedPrefs` 注入）
- `LlmRewriteRepository` 提供（HttpClient 注入）【TASK-0059で実装】

---

## 4. 設計文書

### 主要設計ドキュメント

#### architecture.md
**内容**: システムアーキテクチャ・コンポーネント関係
- 「新規追加コンポーネント」テーブル（行 46-62）
- 「LLM設定管理設計」セクション（行 129-146）
- 「技術的制約」セクション（行 239-244）

**ファイルパス**: `docs/design/llm-memo-rewrite/architecture.md`

#### interfaces.kt
**内容**: Kotlin インターフェース・型定義
- `LlmSettings` data class 定義（行 93-97）
- `LlmSettingsRepository` interface 定義（行 104-117）
- `LlmSettingsRepositoryImpl` 実装例（行 340-377）
- `LlmModule` Hilt Module 例（行 432-467）

**ファイルパス**: `docs/design/llm-memo-rewrite/interfaces.kt`

#### requirements.md
**要件番号**: REQ-004, REQ-401, NFR-101, NFR-102
- **REQ-004**: LLM APIエンドポイント・APIキー・モデル名をSettingsScreenで入力 🔵
- **REQ-401**: APIキーは暗号化ストレージに保存（平文DataStore禁止） 🔵
- **NFR-101**: EncryptedSharedPreferences等での暗号化保存 🔵
- **NFR-102**: APIキーをログ・クラッシュレポートに出力しない 🔵

**ファイルパス**: `docs/spec/llm-memo-rewrite/requirements.md`

#### api-endpoints.md
**内容**: Ktor Client 実装・OpenAI互換 Chat Completions仕様
- リクエストヘッダ/ボディ形式
- レスポンス形式・タイムアウト設定

**ファイルパス**: `docs/design/llm-memo-rewrite/api-endpoints.md`

### 参照元
- `docs/spec/llm-memo-rewrite/acceptance-criteria.md` - 受け入れ基準・テスト項目
- `docs/tasks/llm-memo-rewrite/TASK-0058.md` - タスク定義・完了条件・実装詳細
- `docs/tasks/llm-memo-rewrite/overview.md` - プロジェクト全体概要

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### ユニットテスト
- **フレームワーク**: JUnit 4
- **非同期テスト**: Kotlin Coroutines Test（`runTest { ... }`）
- **Mocking**: MockK（Kotlin ネイティブ）
- **DataStore テスト**: Robolectric（`@RunWith(RobolectricTestRunner::class)`）
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/data/llm/`

#### インストルメント化テスト（EncryptedSharedPreferences）
- **フレームワーク**: JUnit 4
- **テスト対象**: 実 EncryptedSharedPreferences（暗号化確認）
- **テストディレクトリ**: `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/`
- **実行要件**: Android デバイス/エミュレータが必要（`connectedAndroidTest`）

#### Gradle設定
```gradle
testImplementation(libs.junit)                        // JUnit 4
testImplementation(libs.robolectric)                  // Robolectric（DataStore）
testImplementation(libs.mockk)                        // MockK
testImplementation(libs.kotlinx.coroutines.test)      // Coroutines テスト
androidTestImplementation(libs.androidx.security.crypto)  // EncryptedSharedPreferences テスト
```

**ファイルパス**: `app/build.gradle.kts`

### 既存テストパターン

#### DataStore テスト（Robolectric）
**参考**: `app/src/test/java/com/den4dr/share2Obsidian/...` の既存 Repository テスト
- `runTest { ... }` で非同期処理をブロック実行
- `FakeDataStore` または実 DataStore（Robolectric）で検証
- `Flow.first()` で最初の値を取得

#### EncryptedSharedPreferences テスト
**参考**: AndroidX Security 公式テストパターン
- 実 `EncryptedSharedPreferences.create()` で暗号化確認
- SharedPreferences ファイルの内容検査（平文チェック）

### テストケース（TASK-0058で実装予定）

#### テストケース1: saveEndpointUrl/saveModel保存後にgetSettings()が更新値を返すこと 🔵
**テストパターン**: DataStore ユニットテスト
```
Given: 未初期化のDataStoreを持つ LlmSettingsRepositoryImpl
When:  saveEndpointUrl("https://api.example.com/v1/chat") と saveModel("gpt-4o-mini") を呼び出す
Then:  getSettings().first() が endpointUrl = "https://api.example.com/v1/chat", model = "gpt-4o-mini" を含む LlmSettings を返す
信頼性: 🔵 既存 NoteSettingsRepositoryImpl テストパターン踏襲
```

#### テストケース2: saveApiKey保存後にgetSettings()が更新値を返すこと 🔵
**テストパターン**: EncryptedSharedPreferences ユニット/統合テスト
```
Given: 未初期化のEncryptedSharedPreferences相当のモック/実体を持つ LlmSettingsRepositoryImpl
When:  saveApiKey("sk-test-12345") を呼び出す
Then:  getSettings().first() が apiKey = "sk-test-12345" を含む LlmSettings を返す
信頼性: 🔵 architecture.md「LLM設定管理設計」、SharedPreferences.OnSharedPreferenceChangeListener パターン
```

#### テストケース3: 初期状態（未保存）ではLlmSettings()のデフォルト値が返ること 🔵
**テストパターン**: 初期化テスト
```
Given: 何も保存していない状態の LlmSettingsRepositoryImpl
When:  getSettings().first() を呼び出す
Then:  LlmSettings(endpointUrl = "", apiKey = "", model = "") と等しい値が返る
信頼性: 🔵 interfaces.kt デフォルト値定義より
```

#### テストケース4: DataStoreとEncryptedSharedPreferencesの変更が独立して反映されること 🟡
**テストパターン**: Flow合成テスト
```
Given: endpointUrl/model を保存済みの LlmSettingsRepositoryImpl
When:  saveApiKey() のみを呼び出す
Then:  getSettings().first() の endpointUrl/model は変更前の値を維持したまま、apiKey のみ更新される
信頼性: 🟡 combine() によるFlow合成の一般的な挙動からの妥当な推測
```

### テスト実行手順

```bash
# ステップ1: ユニットテスト実行（DataStore + EncryptedSharedPreferences モック）
mise exec -- ./gradlew test --tests "*LlmSettings*"

# ステップ2: インストルメント化テスト実行（実 EncryptedSharedPreferences、デバイス/エミュレータ必須）
mise exec -- ./gradlew connectedAndroidTest --tests "*LlmSettings*"

# ステップ3: 全体チェック（Lint + Build + Test）
mise exec -- ./gradlew clean lint build test connectedAndroidTest
```

---

## 6. 注意事項

### 実装時の重要なポイント

#### 1. EncryptedSharedPreferences の初期化
- **MasterKey.Builder()**: `MasterKey.KeyScheme.AES256_GCM` で初期化
- **暗号化方式**: `PrefKeyEncryptionScheme.AES256_SIV` + `PrefValueEncryptionScheme.AES256_GCM`
- **ファイル名**: `"llm_secure_prefs"` （任意、ただし既存と重複しないこと）
- **Singleton**: Hilt で Singleton として提供（毎回生成しない）

#### 2. Flow合成パターン（callbackFlow + combine）
- **実装例**: interfaces.kt 行 352-360 参照
- **注意点**: `callbackFlow` の `awaitClose` で リスナー登録解除を必ず行う
- **未検証**: 本プロジェクトでの実績がないため、動作確認を優先（tdd-red/tdd-green フェーズで重点）

#### 3. APIキーのセキュリティ
- **ログ出力禁止**: `saveApiKey()` 実行時に apiKey をログ出力しない（NFR-102）
- **例外メッセージ**: EncryptedSharedPreferences の例外オブジェクトをそのまま出力しない
- **ファイル検査**: 統合テストで SharedPreferences ファイルが平文を含まないことを確認

#### 4. Dispatchers.IO での実行
- **理由**: `EncryptedSharedPreferences.edit()` は同期ブロッキング操作
- **実装**: `saveApiKey()` 内で `withContext(Dispatchers.IO) { ... }` でラップ
- **他のメソッド**: `saveEndpointUrl()` / `saveModel()` は DataStore なので IO切り替え不要

#### 5. DataStore KeyName の命名規則
- **既存パターン**: `vault`, `folder` （小文字、アンダースコア区切り）
- **本タスク**: `llm_endpoint_url`, `llm_model` （ll-memo-rewrite 機能を示す接頭辞）
- **一貫性**: 既存キーと衝突しないよう確認

#### 6. Repository の責務分離
- **NoteSettingsRepository**: vault/folder（Note保存先）
- **LlmSettingsRepository**: endpointUrl/apiKey/model（LLM API）
- **理由**: 責務・セキュリティレベル（APIキー）の違い

### セキュリティ・パフォーマンス要件
- **REQ-401**: APIキーは暗号化ストレージのみ（DataStore に保存しない）
- **NFR-101**: EncryptedSharedPreferences で暗号化
- **NFR-102**: ログ出力禁止
- **タイムアウト**: 本タスクは含まない（TASK-0059 で LlmRewriteRepository に実装）

### 技術的制約
- **EncryptedSharedPreferences**: `androidx.security-crypto` 1.1.0+ 必須
- **callbackFlow**: Kotlin 1.4+ で利用可能
- **DataStore**: 既存 `preferencesDataStore` 初期化パターン踏襲

### ビルド・実行手順（参考）
Java は `.mise.toml` で管理されているため、Gradle コマンドは必ず `mise exec --` 経由で実行：
```bash
# ビルド
mise exec -- ./gradlew build

# テスト実行
mise exec -- ./gradlew test connectedAndroidTest

# クリーンビルド + Lint + テスト
mise exec -- ./gradlew clean lint build test connectedAndroidTest
```

---

## 7. ファイル一覧（参照用）

### 新規作成ファイル（実装・テスト）
| ファイル | 内容 | 信頼性 |
|---------|------|--------|
| `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt` | LlmSettings data class | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt` | LlmSettingsRepository interface | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt` | LlmSettingsRepositoryImpl 実装 | 🟡 |
| `app/src/main/java/com/den4dr/share2Obsidian/di/LlmModule.kt` | Hilt DI Module (新規 or DataStoreModule 拡張) | 🔵 |
| `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt` | ユニットテスト | 🔵 |
| `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt` | 統合テスト（EncryptedSharedPreferences） | 🔵 |

### 参照ドキュメント（読み込み専用）
| ファイル | 概要 | 信頼性 |
|---------|------|--------|
| `docs/design/llm-memo-rewrite/architecture.md` | システムアーキテクチャ（LLM設定管理設計セクション） | 🔵 |
| `docs/design/llm-memo-rewrite/interfaces.kt` | LlmSettings・LlmSettingsRepository・LlmModule 型定義 | 🔵 |
| `docs/design/llm-memo-rewrite/api-endpoints.md` | API 仕様・Ktor Client 設定 | 🔵 |
| `docs/spec/llm-memo-rewrite/requirements.md` | 機能要件定義（REQ-004, REQ-401, NFR-101, NFR-102） | 🔵 |
| `docs/spec/llm-memo-rewrite/acceptance-criteria.md` | 受け入れ基準 | 🔵 |
| `docs/tasks/llm-memo-rewrite/TASK-0058.md` | タスク定義・完了条件・実装詳細 | 🔵 |
| `docs/tasks/llm-memo-rewrite/overview.md` | プロジェクト全体概要・フェーズ構成 | 🔵 |
| `app/build.gradle.kts` | Gradle設定・テスト依存関係 | 🔵 |
| `gradle/libs.versions.toml` | バージョンカタログ | 🔵 |

### 参考実装（既存、読み込み専用）
| ファイル | 用途 | 信頼性 |
|---------|------|--------|
| `app/src/main/java/com/den4dr/share2Obsidian/data/datastore/NoteSettingsRepositoryImpl.kt` | DataStore 実装パターン参考 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/di/DataStoreModule.kt` | Hilt DI 提供パターン参考 | 🔵 |

### 関連タスク・ファイル
| 依存関係 | タスク | ファイル |
|---------|--------|---------|
| 前提タスク | TASK-0055 | `docs/tasks/llm-memo-rewrite/TASK-0055.md` - 依存関係追加 |
| 後続タスク | TASK-0061 | `docs/tasks/llm-memo-rewrite/TASK-0061.md` - 本文リライト UI |
| 並行タスク | TASK-0059 | `docs/tasks/llm-memo-rewrite/TASK-0059.md` - LLM呼び出しロジック |

---

## 8. 信頼性レベルサマリー

### 項目別信頼性

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 実装詳細 | 3 | 1 | 0 | 4 |
| テスト設計 | 3 | 1 | 0 | 4 |
| DI設定 | 1 | 0 | 0 | 1 |

### 全体評価

- **総項目数**: 9項目
- 🔵 **青信号**: 7項目 (78%)
- 🟡 **黄信号**: 2項目 (22%)
- 🔴 **赤信号**: 0項目 (0%)

**品質評価**: ✅ 高品質

**推測が多い項目**: 
- EncryptedSharedPreferences の変更通知を Flow化するパターン（`callbackFlow` + `OnSharedPreferenceChangeListener`）は、ヒアリング対象外の妥当な推測であり実装時に別パターン（都度読み取り等）への変更余地がある

---

**このノートは TDD RED フェーズ開始前のコンテキスト情報集約です。実装時に参照。**
