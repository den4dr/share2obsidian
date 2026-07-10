# TASK-0066 開発コンテキストノート

**タスクID**: TASK-0066  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: SettingsViewModel LLM設定対応

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **最低SDK**: API 33 (Android 13)
- **ターゲットSDK**: API 36
- **Java互換性**: 11

### UI・フレームワーク
- **UI**: Jetpack Compose + Material3 (BOM 2024.09.00)
- **アーキテクチャパターン**: 単一アクティビティ + Compose UI + MVVM + Repository + Hilt DI
- **DI**: Hilt（`@HiltViewModel` + `@Inject constructor`）
- **非同期処理**: Kotlin Coroutines + viewModelScope + Flow + combine()

### テスト・フレームワーク
- **テストフレームワーク**: JUnit 4 + kotlinx-coroutines-test
- **テストランナー**: JUnit 4（ローカルJVM）
- **モック・スタブ**: MockK
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/ui/`

### 依存関係（既に導入済み）
- `junit` - ユニットテスト用
- `kotlinx-coroutines-test` - 非同期テスト用
- `mockk` - モック・スタブ作成用

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ
- `app/build.gradle.kts` - 依存関係定義

---

## 2. 開発ルール

### TDD開発フロー（推奨）
1. `/tsumiki:tdd-requirements TASK-0066` - 詳細要件定義
2. `/tsumiki:tdd-testcases` - テストケース作成
3. `/tsumiki:tdd-red` - テスト実装（失敗）
4. `/tsumiki:tdd-green` - 最小実装
5. `/tsumiki:tdd-refactor` - リファクタリング
6. `/tsumiki:tdd-verify-complete` - 品質確認

### コーディング規約

#### 実装パターン（TASK-0066の要点）

**SettingsUiState へのLLM設定フィールド追加**:
```kotlin
data class SettingsUiState(
    val vault: String = "",
    val folder: String = "",
    val llmEndpointUrl: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
)
```

**LlmSettingsRepository のコンストラクタ注入**:
```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val noteSettingsRepository: NoteSettingsRepository,
    private val llmSettingsRepository: LlmSettingsRepository,
) : ViewModel() {
    // ...
}
```

**uiState のcombine()化**:
```kotlin
val uiState: StateFlow<SettingsUiState> = combine(
    noteSettingsRepository.getSettings(),
    llmSettingsRepository.getSettings(),
) { note, llm ->
    SettingsUiState(
        vault = note.vault,
        folder = note.folder,
        llmEndpointUrl = llm.endpointUrl,
        llmApiKey = llm.apiKey,
        llmModel = llm.model,
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
```

**update系関数の追加**:
```kotlin
fun updateLlmEndpointUrl(url: String) {
    viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.saveEndpointUrl(url) }
}

fun updateLlmApiKey(apiKey: String) {
    viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.saveApiKey(apiKey) }
}

fun updateLlmModel(model: String) {
    viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.saveModel(model) }
}
```

#### テスト命名規約（既存パターンから）
- テストクラス: `SettingsViewModelTest.kt`（既存ファイル）
- テストメソッド: ```fun `説明的なテスト名`() {}``` （バッククォートで日本語使用可）
- Arrange-Act-Assert（AAA）パターンで構成
- MockK + kotlinx-coroutines-test を使用

#### テストパターン（MockK + runTest）
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `テスト説明`() = runTest {
        val mockRepo = mockk<LlmSettingsRepository>()
        coEvery { mockRepo.saveEndpointUrl(any()) } just Runs
        val viewModel = SettingsViewModel(
            mockk<NoteSettingsRepository>(),
            mockRepo
        )

        viewModel.updateLlmEndpointUrl("https://example.com")
        advanceUntilIdle()

        coVerify(timeout = 2000) { mockRepo.saveEndpointUrl("https://example.com") }
    }
}
```

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0066.md` - タスク定義・テスト要件詳細
- `docs/design/llm-memo-rewrite/architecture.md` - 「LLM設定管理設計」セクション
- `docs/spec/llm-memo-rewrite/requirements.md` - REQ-004

---

## 3. 関連実装

### 既に実装済みのコンポーネント

#### LlmSettingsRepository（TASK-0058で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt`
- **インターフェース**:
  - `getSettings(): Flow<LlmSettings>`
  - `saveEndpointUrl(url: String)`
  - `saveApiKey(apiKey: String)`
  - `saveModel(model: String)`

#### LlmModule Hilt DI設定（TASK-0061で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/di/LlmModule.kt`
- **提供**: LlmSettingsRepository のシングルトンインスタンス

### TASK-0066で実装する部分

#### 1. SettingsUiState の拡張
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
- **作業内容**:
  - `llmEndpointUrl: String = ""`
  - `llmApiKey: String = ""`
  - `llmModel: String = ""`
  - 各フィールドを data class に追加

#### 2. SettingsViewModel のコンストラクタ拡張
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
- **作業内容**:
  - `private val llmSettingsRepository: LlmSettingsRepository` をコンストラクタ注入
  - `@HiltViewModel` と `@Inject constructor` はすでに存在（既に Hilt化済み）

#### 3. uiState の combine() 実装
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
- **作業内容**:
  - `combine(noteSettingsRepository.getSettings(), llmSettingsRepository.getSettings())` を使用
  - 両Repositoryの値をマッピングして SettingsUiState を構築

#### 4. update系関数の追加
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
- **作業内容**:
  - `updateLlmEndpointUrl(url: String)` - llmSettingsRepository.saveEndpointUrl() を呼び出し
  - `updateLlmApiKey(apiKey: String)` - llmSettingsRepository.saveApiKey() を呼び出し
  - `updateLlmModel(model: String)` - llmSettingsRepository.saveModel() を呼び出し

### 既存実装（参考パターン）

#### SettingsViewModel の既存パターン
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
- **参照例**:
  - `@HiltViewModel` デコレータ
  - `@Inject constructor(noteSettingsRepository)` パターン
  - `uiState: StateFlow` の `stateIn()` での構築
  - `updateVault()`/`updateFolder()` の実装パターン

#### 既存テストパターン
- **位置**: `app/src/test/java/com/den4dr/share2Obsidian/ui/SettingsViewModelTest.kt`
- **参照例**:
  - MockK による Repository モック化
  - `coVerify()` による非同期関数呼び出しの検証
  - `runTest()` + `advanceUntilIdle()` での非同期処理テスト

---

## 4. 設計文書

### 要件定義
- **位置**: `docs/spec/llm-memo-rewrite/requirements.md`
- **対応要件**: REQ-004（LLM API設定の入力項目提供）, REQ-401（APIキー暗号化保存）

### アーキテクチャ設計
- **位置**: `docs/design/llm-memo-rewrite/architecture.md`
- **重要セクション**:
  - 「LLM設定管理設計」- DataStore + EncryptedSharedPreferences の分離保存
  - 「新規追加コンポーネント」表 - LlmSettingsRepository の役割
  - 「変更が必要な既存コンポーネント」- SettingsViewModel への LlmSettingsRepository 注入

### タスク定義
- **位置**: `docs/tasks/llm-memo-rewrite/TASK-0066.md`
- **重要項目**:
  - 実装詳細: 4個（SettingsUiState拡張、コンストラクタ注入、combine()化、update関数）
  - テスト要件: 4ケース（TC-1～TC-4）
  - 信頼性: 全項目 🔵 青信号

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### JUnit 4 + kotlinx-coroutines-test
- **設定ファイル**: `app/build.gradle.kts`
  - `testImplementation(libs.junit)`
  - `testImplementation(libs.kotlinx.coroutines.test)`
  - `testImplementation(libs.mockk)`
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/ui/`
- **テストランナー**: JUnit 4（ローカルJVM、デバイス不要）

### 既存テストのディレクトリ構成

```
app/src/test/java/com/den4dr/share2Obsidian/ui/
├── SettingsViewModelTest.kt （既存: vault/folder 設定テスト）
├── EditScreenViewModelTest.kt
├── TemplateEditViewModelTest.kt
└── ...
```

### テストユーティリティ・モック設定

#### UnconfinedTestDispatcher設定
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```

#### Repository モック（MockK）
```kotlin
val mockNoteSettingsRepository = mockk<NoteSettingsRepository>()
every { mockNoteSettingsRepository.getSettings() } returns 
    flowOf(NoteSettings(vault = "MyVault", folder = "Notes"))

val mockLlmSettingsRepository = mockk<LlmSettingsRepository>()
every { mockLlmSettingsRepository.getSettings() } returns 
    flowOf(LlmSettings(endpointUrl = "https://...", apiKey = "key", model = "gpt-4o"))

coEvery { mockLlmSettingsRepository.saveEndpointUrl(any()) } just Runs
coEvery { mockLlmSettingsRepository.saveApiKey(any()) } just Runs
coEvery { mockLlmSettingsRepository.saveModel(any()) } just Runs
```

#### combine() テストパターン
```kotlin
@Test
fun `uiState reflects both note and llm repository settings`() = runTest {
    val mockNoteRepo = mockk<NoteSettingsRepository>()
    val mockLlmRepo = mockk<LlmSettingsRepository>()
    
    every { mockNoteRepo.getSettings() } returns 
        flowOf(NoteSettings(vault = "V1", folder = "F1"))
    every { mockLlmRepo.getSettings() } returns 
        flowOf(LlmSettings(endpointUrl = "url", apiKey = "key", model = "m1"))
    
    val viewModel = SettingsViewModel(mockNoteRepo, mockLlmRepo)
    
    val collected = mutableListOf<SettingsUiState>()
    val job = launch { viewModel.uiState.collect { collected.add(it) } }
    advanceUntilIdle()
    job.cancel()
    
    val state = collected.last()
    assertEquals("V1", state.vault)
    assertEquals("url", state.llmEndpointUrl)
    assertEquals("key", state.llmApiKey)
}
```

### テスト実行コマンド

```bash
# ユニットテスト（ローカルJVM）
mise exec -- ./gradlew test

# SettingsViewModel 関連テストのみ実行
mise exec -- ./gradlew test --tests "*SettingsViewModelTest*"

# 特定テストメソッド実行
mise exec -- ./gradlew test --tests "*SettingsViewModelTest*updateLlmEndpointUrl*"
```

### テストカバレッジ期待値

**対象**: SettingsViewModel の updateLlmEndpointUrl/updateLlmApiKey/updateLlmModel、および uiState の combine() 構築

**テストケース**（TASK-0066.md より）:
- TC-1: updateLlmEndpointUrl() が saveEndpointUrl() を呼び出すこと
- TC-2: updateLlmApiKey() が saveApiKey() を呼び出すこと
- TC-3: updateLlmModel() が saveModel() を呼び出すこと
- TC-4: uiState が noteSettingsRepository と llmSettingsRepository 両方の値を反映して構築されること

---

## 6. 注意事項

### 技術的制約

#### SettingsViewModel の Hilt化
- SettingsViewModel はすでに `@HiltViewModel` + `@Inject constructor(noteSettingsRepository)` 化されている
- TASK-0066 では `llmSettingsRepository` を追加注入するだけ

#### combine() の StateFlow 構築
- `combine(flow1, flow2)` は Flow<T> を返すため、`.stateIn()` で StateFlow<T> に変換
- `SharingStarted.WhileSubscribed(5_000)` で 5秒後に Flow キャンセル（リソースリーク対策）
- `SettingsUiState()` で初期値を指定（デフォルト値）

#### Dispatchers.IO での保存処理
- update系関数（`updateLlmEndpointUrl()` 等）は `viewModelScope.launch(Dispatchers.IO)` で背景スレッドで実行
- EncryptedSharedPreferences の同期API呼び出しをメインスレッド以外で実行するため

#### 非同期テストのセットアップ
- テスト開始前に `Dispatchers.setMain(dispatcher)` で テストディスパッチャーを設定
- テスト終了後に `Dispatchers.resetMain()` で リセット（必須、メモリリーク対策）

### セキュリティ・パフォーマンス要件

#### APIキーの取り扱い
- `llmSettingsRepository` はすでに EncryptedSharedPreferences で暗号化保存（TASK-0058 実装済み）
- TASK-0066 では API キー値そのものを ViewModel で参照しない（LlmSettingsRepository から Flow で取得するだけ）

#### StateFlow の初期値
- SettingsUiState のデフォルト値（空文字）で初期化（null安全性）
- UI側が subscribe した時点で即座に値が流れる（CodesShare パターン）

### 参考ドキュメント関連図

**データフロー** (TASK-0066 実装部分):
```
NoteSettingsRepository.getSettings()
    ↓ (Flow<NoteSettings>)
combine() ←─┐
    ↓       │
    └───────┴─ LlmSettingsRepository.getSettings()
              (Flow<LlmSettings>)
    ↓
combine { note, llm -> SettingsUiState(...) }
    ↓
.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
    ↓
uiState: StateFlow<SettingsUiState>
    ↓
SettingsScreen がUIに反映
    ↓
UI側で updateLlmEndpointUrl() / updateLlmApiKey() / updateLlmModel() を呼び出し
    ↓
viewModelScope.launch(Dispatchers.IO)
    ↓
llmSettingsRepository.saveEndpointUrl() / saveApiKey() / saveModel()
    ↓
DataStore + EncryptedSharedPreferences に保存
    ↓
LlmSettingsRepository.getSettings() Flow が新規値を emit
    ↓
combine() が再実行、新規 SettingsUiState を構築
    ↓
UI自動更新
```

**参照元**: `docs/design/llm-memo-rewrite/architecture.md` 「LLM設定管理設計」セクション

---

## 実装チェックリスト（目安）

### 実装フェーズ
- [ ] SettingsUiState データクラス拡張
  - [ ] `llmEndpointUrl: String = ""`
  - [ ] `llmApiKey: String = ""`
  - [ ] `llmModel: String = ""`
- [ ] SettingsViewModel コンストラクタ拡張
  - [ ] `@Inject constructor(..., private val llmSettingsRepository: LlmSettingsRepository)`
- [ ] uiState StateFlow 構築
  - [ ] `combine(noteSettingsRepository.getSettings(), llmSettingsRepository.getSettings())`
  - [ ] `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())`
- [ ] update系関数追加
  - [ ] `updateLlmEndpointUrl(url: String)`
  - [ ] `updateLlmApiKey(apiKey: String)`
  - [ ] `updateLlmModel(model: String)`
  - [ ] 各関数で `viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.save*() }`

### テストフェーズ
- [ ] 単体テスト4ケース
  - [ ] TC-1: updateLlmEndpointUrl が saveEndpointUrl を呼び出すこと
  - [ ] TC-2: updateLlmApiKey が saveApiKey を呼び出すこと
  - [ ] TC-3: updateLlmModel が saveModel を呼び出すこと
  - [ ] TC-4: uiState が両Repository値を反映して構築されること
- [ ] 既存テストスイートの確認（後方互換性）
  - [ ] SettingsViewModelTest（既存テスト）の確認・実行

---

## 関連タスク

### 前提タスク（完了済み）
- **TASK-0058**: LlmSettingsRepository・DataStore+EncryptedSharedPreferences実装
- **TASK-0061**: LlmModule Hilt DI設定

### 後続タスク
- **TASK-0067**: SettingsScreen LLM設定入力欄追加

---

**作成日**: 2026-07-06 by tsumiki:tdd-tasknote  
**最終確認**: TASK-0066 TDD開発開始前
