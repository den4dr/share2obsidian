# TASK-0067 開発コンテキストノート

**タスクID**: TASK-0067  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: SettingsScreen LLM設定入力欄追加

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
- **UIテストフレームワーク**: Jetpack Compose UI Test + JUnit 4 + AndroidJUnit4
- **テストランナー**: AndroidJUnit4（実機/エミュレータ上で実行）
- **テストディレクトリ**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/`

### 依存関係（既に導入済み）
- `androidx-compose-ui` - Compose UI フレームワーク
- `androidx-compose-ui-test` - Compose UI テスト
- `junit` - テストフレームワーク
- Material3 - Design System

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ
- `app/build.gradle.kts` - 依存関係定義
- `docs/tech-stack.md` - 技術スタック全体定義

---

## 2. 開発ルール

### TDD開発フロー（推奨）
1. `/tsumiki:tdd-requirements TASK-0067` - 詳細要件定義
2. `/tsumiki:tdd-testcases` - テストケース作成
3. `/tsumiki:tdd-red` - テスト実装（失敗）
4. `/tsumiki:tdd-green` - 最小実装
5. `/tsumiki:tdd-refactor` - リファクタリング
6. `/tsumiki:tdd-verify-complete` - 品質確認

### コーディング規約

#### Compose UI パターン（TASK-0067の要点）

**OutlinedTextField の基本パターン**:
```kotlin
OutlinedTextField(
    value = uiState.llmEndpointUrl,
    onValueChange = viewModel::updateLlmEndpointUrl,
    label = { Text(stringResource(R.string.settings_llm_endpoint_label)) },
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .testTag("settings_llm_endpoint_field"),
)
```

**APIキーマスク表示パターン**:
```kotlin
OutlinedTextField(
    value = uiState.llmApiKey,
    onValueChange = viewModel::updateLlmApiKey,
    label = { Text(stringResource(R.string.settings_llm_apikey_label)) },
    visualTransformation = PasswordVisualTransformation(),
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .testTag("settings_llm_apikey_field"),
)
```

**HorizontalDivider セクション区切り**:
```kotlin
HorizontalDivider()  // vault/folder セクションとの区切り
```

#### テスト命名規約（既存パターンから）
- テストクラス: `SettingsScreenTest.kt`（既存ファイル）
- テストメソッド: ```fun `説明的なテスト名`() {}``` （バッククォートで日本語使用可）
- テストパターン: Jetpack Compose UI Test（`composeTestRule.onNode*` の操作と `assert*` 検証）

#### Compose UI テストパターン（既存パターン）
```kotlin
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun createViewModel(
        settings: NoteSettings = NoteSettings(),
    ): SettingsViewModel = SettingsViewModel(
        FakeNoteSettingsRepository(settings),
        FakeLlmSettingsRepository()
    )

    @Test
    fun llmEndpointField_updatesViewModelOnChange() {
        var updatedValue = ""
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(),
            )
        }
        composeTestRule
            .onNodeWithTag("settings_llm_endpoint_field")
            .performTextInput("https://example.com")
        // 検証: viewModel が updateLlmEndpointUrl で正しい値で呼ばれたか確認
    }
}
```

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0067.md` - タスク定義・テスト要件詳細
- `docs/design/llm-memo-rewrite/architecture.md` - 「LLM設定管理設計」セクション
- `docs/spec/llm-memo-rewrite/requirements.md` - REQ-004
- `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt` - 既存テストパターン

---

## 3. 関連実装

### 既に実装済みのコンポーネント

#### SettingsViewModel LLM設定対応（TASK-0066で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
- **提供するメソッド**:
  - `uiState: StateFlow<SettingsUiState>` - LLM3項目を含む状態
  - `updateLlmEndpointUrl(url: String)`
  - `updateLlmApiKey(apiKey: String)`
  - `updateLlmModel(model: String)`

#### SettingsUiState の拡張（TASK-0066で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt` または別ファイル
- **フィールド**:
  - `llmEndpointUrl: String = ""`
  - `llmApiKey: String = ""`
  - `llmModel: String = ""`

#### LlmModule Hilt DI設定（TASK-0061で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/di/LlmModule.kt`
- **提供**: LlmSettingsRepository のシングルトンインスタンス

### TASK-0067で実装する部分

#### 1. SettingsScreen への LLM設定セクション追加
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsScreen.kt`
- **作業内容**:
  - vault/folder セクション後に `HorizontalDivider()` を追加
  - 以下の3つの `OutlinedTextField` を追加:
    - `settings_llm_endpoint_field` - endpointUrl入力欄
    - `settings_llm_apikey_field` - apiKey入力欄（マスク表示）
    - `settings_llm_model_field` - model入力欄
  - 各欄に `onValueChange` で対応する ViewModel メソッドを呼び出し

#### 2. Compose UI テスト実装
- **変更箇所**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt`
- **作業内容**:
  - LLM3項目の入力・表示テスト追加
  - ViewModel との連携テスト（`performTextInput` + 検証）
  - マスク表示のセマンティクステスト
  - 初期値表示テスト

### 既存実装（参考パターン）

#### SettingsScreen の既存パターン
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsScreen.kt`
- **参照例**:
  - vault/folder OutlinedTextField の構造
  - HorizontalDivider による セクション分割
  - padding (16.dp, 4.dp) 設定
  - testTag の命名パターン

#### 既存 Compose UI テストパターン
- **位置**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt`
- **参照例**:
  - `createAndroidComposeRule<ComponentActivity>()` のセットアップ
  - `composeTestRule.setContent { SettingsScreen(...) }` での画面設定
  - `onNodeWithTag()` / `onNodeWithText()` のノード選択
  - `performTextInput()` / `performClick()` のユーザー操作
  - `assertIsDisplayed()` の表示確認
  - `FakeNoteSettingsRepository` / `FakeLlmSettingsRepository` のフェイク実装

---

## 4. 設計文書

### 要件定義
- **位置**: `docs/spec/llm-memo-rewrite/requirements.md`
- **対応要件**: 
  - REQ-004（LLM API設定の入力項目提供）
  - REQ-401（APIキー暗号化保存）
  - REQ-102（プロンプト未設定時はボタン非活性化 - 後続 TASK）
  - NFR-201（エラー時のToast表示）

### アーキテクチャ設計
- **位置**: `docs/design/llm-memo-rewrite/architecture.md`
- **重要セクション**:
  - 「LLM設定管理設計」- endpointUrl/model (DataStore) + apiKey (暗号化ストレージ) の分離保存
  - 「新規追加コンポーネント」表 - SettingsScreen への UI追加
  - 「変更が必要な既存コンポーネント」- SettingsScreen にLLM設定セクション追加
  - 「ユーザビリティ（NFR-201, NFR-202）」- エラーメッセージ・ローディング表示

### タスク定義
- **位置**: `docs/tasks/llm-memo-rewrite/TASK-0067.md`
- **重要項目**:
  - 実装詳細: 3個（LLM設定セクション、apiKeyマスク、onValueChangeによるViewModel呼び出し）
  - テスト要件: 5ケース（TC-1〜TC-5）
  - 信頼性: 🔵 青信号 6項目 (60%), 🟡 黄信号 4項目 (40%)

### テスト仕様書
- **位置**: `docs/spec/llm-memo-rewrite/acceptance-criteria.md`
- **対応テスト**: REQ-004・REQ-401 の受け入れ基準（TC-004-01 以降）

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### Jetpack Compose UI Test
- **設定ファイル**: `app/build.gradle.kts`
  - `androidTestImplementation(libs.androidx.compose.ui.test.junit4)`
  - `androidTestImplementation(libs.androidx.test.ext.junit)`
  - `androidTestImplementation(libs.androidx.test.runner)`
- **テストディレクトリ**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/`
- **テストランナー**: AndroidJUnit4（実機/エミュレータ上で実行）

### 既存テストのディレクトリ構成

```
app/src/androidTest/java/com/den4dr/share2Obsidian/ui/
├── SettingsScreenTest.kt （既存: vault/folder テスト）
└── ...

app/src/test/java/com/den4dr/share2Obsidian/ui/
├── SettingsViewModelTest.kt （単体テスト: ViewModel ロジック）
└── ...
```

### テストユーティリティ・フェイク実装

#### FakeNoteSettingsRepository（既存）
```kotlin
private class FakeNoteSettingsRepository(
    private val settings: NoteSettings = NoteSettings(),
) : NoteSettingsRepository {
    override fun getSettings(): Flow<NoteSettings> = flowOf(settings)
    override suspend fun saveVault(vault: String) {}
    override suspend fun saveFolder(folder: String) {}
}
```

#### FakeLlmSettingsRepository（既存・TASK-0066で追加）
```kotlin
private class FakeLlmSettingsRepository(
    private val settings: LlmSettings = LlmSettings(),
) : LlmSettingsRepository {
    override fun getSettings(): Flow<LlmSettings> = flowOf(settings)
    override suspend fun saveEndpointUrl(url: String) {}
    override suspend fun saveApiKey(apiKey: String) {}
    override suspend fun saveModel(model: String) {}
}
```

### Compose UI テスト基本パターン

```kotlin
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ノード選択
    composeTestRule.onNodeWithTag("settings_llm_endpoint_field")
    composeTestRule.onNodeWithText("LLM エンドポイント")
    
    // ユーザー操作
    .performTextInput("https://...")        // テキスト入力
    .performClick()                         // クリック
    
    // アサーション
    .assertIsDisplayed()                    // 表示確認
    .assertTextContains("value")            // テキスト内容確認
}
```

### テスト実行コマンド

```bash
# SettingsScreenTest（Compose UI テスト）実行
# 実機またはエミュレータが起動している状態で実行
mise exec -- ./gradlew connectedAndroidTest

# SettingsScreenTest のみ実行
mise exec -- ./gradlew connectedAndroidTest --tests "*SettingsScreenTest*"

# 単体テスト（SettingsViewModelTest）実行
mise exec -- ./gradlew test --tests "*SettingsViewModelTest*"
```

### テストカバレッジ期待値

**対象**: SettingsScreen の LLM設定 3フィールド表示・入力・反映

**テストケース**（TASK-0067.md より）:
- TC-1: endpointUrl欄への入力でupdateLlmEndpointUrl()が呼ばれること
- TC-2: apiKey欄への入力でupdateLlmApiKey()が呼ばれること
- TC-3: model欄への入力でupdateLlmModel()が呼ばれること
- TC-4: apiKey欄の表示内容がマスクされていること（PasswordVisualTransformation）
- TC-5: 画面起動時にuiStateの初期値が各入力欄に反映されていること

---

## 6. 注意事項

### 技術的制約

#### Compose testTag の命名
- `settings_llm_endpoint_field`, `settings_llm_apikey_field`, `settings_llm_model_field` を使用
- 既存の `settings_vault_field`, `settings_folder_field` と同じ命名パターン

#### PasswordVisualTransformation の使用
- `import androidx.compose.material.PasswordVisualTransformation` または `androidx.compose.foundation.text.PasswordVisualTransformation`
- apiKey 欄のみ指定。endpointUrl・model 欄には不要

#### 画面レイアウト
- 既存の vault/folder と同じ padding: `horizontal = 16.dp, vertical = 4.dp`
- 既存の vault/folder と同じ singleLine は指定しない（複数行対応）
- 既存の vault/folder と同じ fillMaxWidth() を使用

#### HorizontalDivider の配置
- vault/folder セクション後、LLM設定セクション前に追加
- 既存のテンプレート管理 ListItem との間に配置

### セキュリティ・パフォーマンス要件

#### APIキーの表示マスキング
- `visualTransformation = PasswordVisualTransformation()` により、画面上に平文表示されない
- LlmSettingsRepository が既に暗号化ストレージ (EncryptedSharedPreferences) で保存しているため、UI層での追加暗号化は不要

#### 入力値の変更への対応
- `onValueChange` で即座に `viewModel.updateLlmApiKey()` 等を呼び出し
- Repository が非同期 (Dispatchers.IO) で保存するが、UI側は同期的に反映（StateFlow の Flow を通じて）

#### null安全性
- 全フィールドのデフォルト値は `""` （null ではない）
- ViewModel の uiState は StateFlow で即座に初期値が emit される

### 参考ドキュメント関連図

**UI更新フロー** (TASK-0067 実装部分):
```
SettingsScreen 表示
    ↓
uiState から llmEndpointUrl / llmApiKey / llmModel 読み取り
    ↓
OutlinedTextField に value=uiState.llm* を設定
    ↓
ユーザー入力（テキスト入力）
    ↓
onValueChange 呼び出し
    ↓
viewModel.updateLlmEndpointUrl() / updateLlmApiKey() / updateLlmModel() 呼び出し
    ↓
viewModelScope.launch(Dispatchers.IO)
    ↓
llmSettingsRepository.saveEndpointUrl() / saveApiKey() / saveModel()
    ↓
DataStore + EncryptedSharedPreferences に保存
    ↓
LlmSettingsRepository.getSettings() Flow が新規値を emit
    ↓
SettingsViewModel の uiState が新規値で更新
    ↓
SettingsScreen が StateFlow 購読により再構成（recompose）
    ↓
OutlinedTextField が新しい値で表示更新
```

**参照元**: `docs/design/llm-memo-rewrite/architecture.md` 「LLM設定管理設計」セクション

---

## 実装チェックリスト（目安）

### 実装フェーズ
- [ ] LLM設定セクションのレイアウト構築
  - [ ] `HorizontalDivider()` を vault/folder 後に追加
  - [ ] LLM設定 Column/LazyColumn 構成
  - [ ] 3つの `OutlinedTextField` 追加
- [ ] endpointUrl入力欄実装
  - [ ] `testTag("settings_llm_endpoint_field")`
  - [ ] `onValueChange = viewModel::updateLlmEndpointUrl`
  - [ ] ラベル: `stringResource(R.string.settings_llm_endpoint_label)`
  - [ ] padding / fillMaxWidth 設定
- [ ] apiKey入力欄実装（マスク表示）
  - [ ] `testTag("settings_llm_apikey_field")`
  - [ ] `onValueChange = viewModel::updateLlmApiKey`
  - [ ] `visualTransformation = PasswordVisualTransformation()`
  - [ ] ラベル: `stringResource(R.string.settings_llm_apikey_label)`
  - [ ] padding / fillMaxWidth 設定
- [ ] model入力欄実装
  - [ ] `testTag("settings_llm_model_field")`
  - [ ] `onValueChange = viewModel::updateLlmModel`
  - [ ] ラベル: `stringResource(R.string.settings_llm_model_label)`
  - [ ] padding / fillMaxWidth 設定

### テストフェーズ（Compose UI Test）
- [ ] 5つのテストケース
  - [ ] TC-1: endpointUrl欄入力テスト
  - [ ] TC-2: apiKey欄入力テスト
  - [ ] TC-3: model欄入力テスト
  - [ ] TC-4: apiKey欄マスク表示テスト
  - [ ] TC-5: 画面起動時初期値反映テスト
- [ ] 既存テストスイートの確認（後方互換性）
  - [ ] SettingsScreenTest（既存テスト）の確認・実行

### リソース・文字列管理
- [ ] `res/values/strings.xml` への文字列追加確認
  - [ ] `settings_llm_endpoint_label` - "LLM エンドポイント" 等
  - [ ] `settings_llm_apikey_label` - "APIキー" 等
  - [ ] `settings_llm_model_label` - "モデル名" 等

---

## 関連タスク

### 前提タスク（完了済み）
- **TASK-0058**: LlmSettingsRepository・DataStore+EncryptedSharedPreferences実装
- **TASK-0061**: LlmModule Hilt DI設定
- **TASK-0066**: SettingsViewModel LLM設定対応

### 後続タスク
- **TASK-0068**: EditScreen 「メモを更改」ボタン追加

---

**作成日**: 2026-07-06 by tsumiki:tdd-tasknote  
**最終確認**: TASK-0067 TDD開発開始前
