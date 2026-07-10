# TASK-0063 開発コンテキストノート

**タスクID**: TASK-0063  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: EditScreenViewModel Hilt化・rewriteBody()実装

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **最低SDK**: API 33 (Android 13)
- **ターゲットSDK**: API 36
- **Java互換性**: 11

### プロジェクト構成
- **アーキテクチャパターン**: 単一アクティビティ + Compose UI + MVVM + Repository + Hilt DI
- **DI**: Hilt（`@HiltViewModel` + `@Inject constructor`）
- **非同期処理**: Kotlin Coroutines + viewModelScope + SharedFlow
- **テストフレームワーク**: JUnit 4 + Robolectric + MockK + kotlinx-coroutines-test
- **HTTP通信**: Ktor Client (CIO エンジン)

### 依存関係（既に導入済み）
- `Hilt` - DI フレームワーク
- `Kotlin Coroutines Test` - 非同期テスト用
- `MockK` - モック・スタブ作成用
- `Robolectric` - Android リソース依存テスト用
- `Ktor Client (CIO)` - HTTP通信用
- `androidx-security-crypto` - 暗号化ストレージ用

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ
- `app/build.gradle.kts` - 依存関係定義
- `app/src/main/java/com/den4dr/share2Obsidian/di/LlmModule.kt` - Hilt設定

---

## 2. 開発ルール

### TDD開発フロー（推奨）
1. `/tsumiki:tdd-requirements TASK-0063` - 詳細要件定義
2. `/tsumiki:tdd-testcases` - テストケース作成
3. `/tsumiki:tdd-red` - テスト実装（失敗状態）
4. `/tsumiki:tdd-green` - 最小実装（テスト通過）
5. `/tsumiki:tdd-refactor` - リファクタリング
6. `/tsumiki:tdd-verify-complete` - 品質確認・完了

### コーディング規約

#### 実装パターン（TASK-0063の要点）

**EditScreenViewModelの構造変更**:
```kotlin
@HiltViewModel
class EditScreenViewModel @Inject constructor(
    private val llmRewriteRepository: LlmRewriteRepository,
    private val llmSettingsRepository: LlmSettingsRepository,
) : ViewModel() {
    // 既存: _formState, initialized, sourceContent, bodyLlmPrompt
    
    // 新規追加
    private val _errorEvents = MutableSharedFlow<Int>()
    val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()
    
    // 新規メソッド
    fun rewriteBody() {
        viewModelScope.launch {
            _formState.update { it.copy(isRewritingBody = true) }
            val settings = llmSettingsRepository.getSettings().first()
            when (val result = llmRewriteRepository.rewrite(settings, bodyLlmPrompt, sourceContent)) {
                is LlmRewriteResult.Success ->
                    _formState.update { it.copy(body = result.text) }
                is LlmRewriteResult.Failure ->
                    _errorEvents.emit(result.messageResId)
            }
            _formState.update { it.copy(isRewritingBody = false) }
        }
    }
}
```

#### EditFormState の構造変更
```kotlin
data class EditFormState(
    val vault: String,
    val folder: String,
    val title: String,
    val body: String,
    val tagsText: String,
    val customFields: List<CustomFieldState> = emptyList(),
    val isRewritingBody: Boolean = false,      // 新規: REQ-201
    val rewriteBodyEnabled: Boolean = false,   // 新規: REQ-102（initialize()時に bodyLlmPrompt.isNotBlank() から算出）
)
```

#### テスト命名規約（既存パターンから）
- テストクラス: `EditScreenViewModelRewriteBodyTest.kt`
- テストメソッド: ```fun `説明的なテスト名`() {}``` （バッククォートで日本語使用可）
- Arrange-Act-Assert（AAA）パターンで構成

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0063.md` - タスク定義・テスト要件詳細
- `docs/design/llm-memo-rewrite/architecture.md` - 「EditScreenViewModel設計変更」セクション
- `docs/design/llm-memo-rewrite/design-interview.md` - Q2（Hilt化）, Q3（errorEvents）

---

## 3. 関連実装

### 既に実装済みのコンポーネント

#### LLM リポジトリ層（TASK-0058〜0061で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/`
- **内容**:
  - `LlmSettings.kt` - LLM API設定データクラス
  - `LlmSettingsRepository.kt` / `LlmSettingsRepositoryImpl.kt` - 設定の読み書き
  - `LlmRewriteRepository.kt` / `LlmRewriteRepositoryImpl.kt` - API呼び出し
  - `LlmRewriteResult.kt` - 成功/失敗結果型
  - `dto/ChatCompletionDto.kt` - リクエスト/レスポンス DTO

#### Hilt Module（TASK-0061で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/di/LlmModule.kt`
- **内容**: HttpClient・EncryptedSharedPreferences・各Repositoryのシングルトン提供

#### EditScreenViewModel の状態管理（TASK-0062で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`
- **既実装**:
  - `sourceContent: String` プロパティ（LLM入力用の元コンテンツ）
  - `bodyLlmPrompt: String` プロパティ（テンプレートの本文用プロンプト）
  - `initialize()` のシグネチャに上記2引数を追加

### TASK-0063で実装する部分

#### 1. EditScreenViewModel を @HiltViewModel 化
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`
- **作業内容**:
  - クラス定義を `@HiltViewModel` デコレータを追加
  - `@Inject constructor(llmRewriteRepository, llmSettingsRepository)` で依存関係注入
  - ViewModel() の呼び出しはそのまま維持（継承）

#### 2. errorEvents (SharedFlow) の追加
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`
- **作業内容**:
  - `private val _errorEvents = MutableSharedFlow<Int>()`
  - `val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()`
  - エラー発生時に `_errorEvents.emit(messageResId)` でstring resource IDを発行

#### 3. EditFormState への項目追加
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`
- **作業内容**:
  - `isRewritingBody: Boolean = false` を追加（ローディング状態管理）
  - `rewriteBodyEnabled: Boolean = false` を追加（ボタン活性判定）

#### 4. rewriteBody() メソッドの実装
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`
- **作業内容**:
  - viewModelScope.launch で非同期処理
  - isRewritingBody = true → API呼び出し → isRewritingBody = false
  - 成功時: formState.body を上書き
  - 失敗時: errorEvents に messageResId を emit

### 既存実装（参考パターン）

#### Hilt @HiltViewModel 化の参考
- `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt` - 既にHilt化済み
- `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModel.kt` - 既にHilt化済み

#### SharedFlow によるイベント通知の参考
- 既存実装で SharedFlow の一般的な使用パターンを参照
- `errorEvents` は一度の emit で1件のイベントを発行する想定（一回限りイベント）

#### viewModelScope.launch の既存パターン
- `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt` - 非同期処理実装パターン

---

## 4. 設計文書

### 要件定義
- **位置**: `docs/spec/llm-memo-rewrite/requirements.md`
- **対応要件**: 
  - REQ-002 - LLM呼び出しで元コンテンツ+プロンプトを送信
  - REQ-003 - API応答で本文フィールド即時上書き
  - REQ-101 - テンプレートの bodyLlmPrompt 使用
  - REQ-102 - プロンプト未設定時のボタン非活性化
  - REQ-201 - LLM呼び出し中のローディング状態管理
  - REQ-202 - 30秒タイムアウト処理
  - REQ-406 - 元コンテンツの保持・管理

### アーキテクチャ設計
- **位置**: `docs/design/llm-memo-rewrite/architecture.md`
- **重要セクション**:
  - 「EditScreenViewModel 設計変更」 - Hilt化・sourceContent/bodyLlmPrompt・rewriteBody()
  - 「LLMリクエスト/レスポンス設計」 - OpenAI互換形式・タイムアウト設定
  - 「ProcessedContent 保持設計」 - 元コンテンツがEditScreenに到達する仕組み

### 設計ヒアリング
- **位置**: `docs/design/llm-memo-rewrite/design-interview.md`
- **重要確認事項**:
  - Q2: EditScreenViewModel の Hilt化を許容
  - Q3: SharedFlow によるエラー通知パターン

### インターフェース・型定義
- **位置**: `docs/design/llm-memo-rewrite/interfaces.kt`
- **重要情報**:
  - EditScreenViewModel（変更後）の完全なシグネチャ
  - EditFormState（変更後）のフィールド定義
  - LlmRewriteResult（Success/Failure）の型定義
  - errorEvents: SharedFlow<Int> の仕様

### タスク定義
- **位置**: `docs/tasks/llm-memo-rewrite/TASK-0063.md`
- **重要項目**:
  - 実装詳細: 7個の実装パターン
  - 単体テスト要件: 7ケース（TC-1〜TC-7）
  - 完了条件チェックリスト

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### ユニットテスト（JUnit 4 + Robolectric + MockK）
- **設定ファイル**: `app/build.gradle.kts`
  - `testImplementation(libs.junit)`
  - `testImplementation(libs.robolectric)`
  - `testImplementation(libs.mockk)`
  - `testImplementation(libs.kotlinx.coroutines.test)`
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/ui/`
- **テストランナー**: JUnit 4（Robolectric対応）

### 既存テストのディレクトリ構成

```
app/src/test/java/com/den4dr/share2Obsidian/ui/
├── EditScreenViewModelTest.kt （既存: initialize()・updateXxx()のテスト）
├── EditScreenViewModelInitializeTest.kt （既存: sourceContent/bodyLlmPrompt関連テスト）
├── SettingsViewModelTest.kt （参考: @HiltViewModelのテストパターン）
└── template/
    └── TemplateEditViewModelTest.kt （参考: @HiltViewModelのテストパターン）
```

### テストユーティリティ・モック設定

#### Robolectric による Android ContextTest
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditScreenViewModelRewriteBodyTest {
    // ...
}
```

#### MockK によるリポジトリモック
```kotlin
val mockLlmRewriteRepository = mockk<LlmRewriteRepository>()
coEvery { mockLlmRewriteRepository.rewrite(any(), any(), any()) } returns 
    LlmRewriteResult.Success("書き換え後テキスト")

val mockLlmSettingsRepository = mockk<LlmSettingsRepository>()
coEvery { mockLlmSettingsRepository.getSettings() } returns 
    flowOf(LlmSettings(endpointUrl = "...", apiKey = "...", model = "..."))
```

#### Coroutines Test の実行環境
```kotlin
val dispatcherRule = StandardTestDispatcher()

// テスト内で viewModelScope の launch を制御
runTest(dispatcherRule) {
    viewModel.rewriteBody()
    advanceUntilIdle()  // すべてのコルーチンが完了するまで待機
    // 結果を検証
}
```

#### 既存テスト参考パターン
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt` - EditScreenViewModel テストの基本パターン
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImplTest.kt` - LLM API呼び出しテストパターン

### テスト実行コマンド

```bash
# ユニットテスト（ローカル JVM）
mise exec -- ./gradlew test

# TASK-0063 関連テストのみ実行
mise exec -- ./gradlew test --tests "*EditScreenViewModel*Test"

# 特定のテストクラス実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelRewriteBodyTest"
```

### テストカバレッジ期待値

- **対象**: rewriteBody() メソッド実装・errorEvents emit・isRewritingBody 状態管理
- **期待カバレッジ**: 
  - 正常系（成功時の本文上書き）
  - エラー系（各失敗種別での errorEvents emit）
  - 状態遷移（isRewritingBody の on→off）
  - エッジケース（sourceContent 空文字での動作）

---

## 6. 注意事項

### 技術的制約

#### EditScreenViewModel のライフサイクル
- TASK-0062 での `initialized` フラグは引き続き維持する
- 画面回転時に initialize() が再度呼ばれても、ViewModel は生存し、formState の既存値を上書きしない（EDGE-101）

#### Hilt @HiltViewModel への変更による影響
- MainActivity の `private val viewModel: EditScreenViewModel by viewModels()` はそのまま動作する
- Hilt ViewModelは by viewModels() で自動取得可能
- 既存の依存関係注入パターンは変わらない

#### sourceContent と bodyLlmPrompt の不変性
- TASK-0063 では、これらの値は initialize() で設定された後、rewriteBody() まで変更されない
- EditScreen上でユーザーが body を編集しても、sourceContent は常に元の値を保持
- REQ-406（「元コンテンツの保持」）の実現方法

#### rewriteBody() のガード処理
- `sourceContent` が空文字でも、ガード（早期return）をしない（EDGE-101）
- ボタン活性/非活性は `bodyLlmPrompt.isNotBlank()` で判定（`rewriteBodyEnabled`）
- ただし防御的には `rewriteBody()` 内で `bodyLlmPrompt.isBlank()` チェックをしてもよい（task定義に明記）

### セキュリティ・パフォーマンス要件

#### LLM APIキーの取り扱い
- `llmSettingsRepository.getSettings().first()` で取得される `LlmSettings` には暗号化済みの apiKey が含まれる（TASK-0058 で EncryptedSharedPreferences に保存済み）
- rewriteBody() 内では平文で取り扱う必要があるが（HTTP送信時）、メモリ上でのみ存在

#### タイムアウト処理
- Ktor HttpClient の timeout は LlmModule で `requestTimeoutMillis = 30_000` に設定済み（REQ-202, NFR-001）
- rewriteBody() での明示的なタイムアウト追加は不要（リポジトリレイヤーで処理）

#### エラーメッセージの日本語化
- `errorEvents` に emit される messageResId は `app/src/main/res/values/strings.xml` に定義された string resource ID
- エラーメッセージの日本語テキストはリソースファイルで管理（NFR-201）

#### ローディング状態の管理
- `isRewritingBody` は EditScreen 側で LaunchedEffect で購読し、ローディングインジケータの表示/非表示を制御（REQ-201, NFR-202）
- ViewModel 側では単に状態を管理するのみ（UI制御は EditScreen に委譲）

### 参考ドキュメント関連図

**データフロー** (TASK-0063 処理箇所):
```
EditScreen「メモを更改」ボタン押下
  ↓
EditScreenViewModel.rewriteBody()
  ↓
1. isRewritingBody = true へ状態更新
  ↓
2. llmSettingsRepository.getSettings() で API接続情報を取得
  ↓
3. llmRewriteRepository.rewrite(settings, bodyLlmPrompt, sourceContent) を呼び出し
  ↓
4. API呼び出し成功時: formState.body を上書き
     API呼び出し失敗時: errorEvents に messageResId を emit
  ↓
5. isRewritingBody = false へ状態更新
  ↓
EditScreen が状態変化を購読して UI更新
  ├→ isRewritingBody 購読: ローディング表示制御
  ├→ body 購読: 本文更新表示
  └→ errorEvents 購読: エラーToast表示
```

**参照元**: `docs/design/llm-memo-rewrite/dataflow.md`

---

## 実装チェックリスト（目安）

### 実装フェーズ
- [ ] EditScreenViewModel クラス定義
  - [ ] `@HiltViewModel` デコレータを追加
  - [ ] `@Inject constructor(llmRewriteRepository, llmSettingsRepository)` の依存関係注入を追加
- [ ] errorEvents の追加
  - [ ] `private val _errorEvents = MutableSharedFlow<Int>()` を追加
  - [ ] `val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()` を追加
- [ ] EditFormState の拡張
  - [ ] `isRewritingBody: Boolean = false` を追加
  - [ ] `rewriteBodyEnabled: Boolean = false` を追加
- [ ] initialize() の拡張
  - [ ] rewriteBodyEnabled を bodyLlmPrompt.isNotBlank() から算出する処理を追加
- [ ] rewriteBody() メソッドの実装
  - [ ] viewModelScope.launch での非同期処理
  - [ ] isRewritingBody = true/false の状態管理
  - [ ] llmSettingsRepository.getSettings().first() での設定取得
  - [ ] llmRewriteRepository.rewrite() の呼び出し
  - [ ] 成功時の formState.body 上書き
  - [ ] 失敗時の errorEvents.emit(messageResId)

### テストフェーズ
- [ ] 単体テスト7ケース（TASK-0063.md 定義分）
  - [ ] TC-1: rewriteBody() 成功時の body 上書き
  - [ ] TC-2: rewriteBody() 失敗時の errorEvents emit
  - [ ] TC-3: initialize() で bodyLlmPrompt 空の場合 rewriteBodyEnabled = false
  - [ ] TC-4: initialize() で bodyLlmPrompt 非空の場合 rewriteBodyEnabled = true
  - [ ] TC-5: sourceContent 空字でも rewriteBody() が実行される
  - [ ] TC-6: rewriteBody() 実行中 isRewritingBody = true → false への遷移
  - [ ] TC-7: rewriteBody() が sourceContent（formState.body ではない）を入力に使用
- [ ] 既存テストスイートの確認（後方互換性）
  - [ ] EditScreenViewModelTest（既存テスト）
  - [ ] EditScreenViewModelInitializeTest（既存テスト）

---

## 関連タスク

### 前提タスク（完了済み）
- **TASK-0055**: Template/TemplateField ドメインモデル・DB マイグレーション準備
- **TASK-0056**: Template ドメインモデル LLM フィールド追加（bodyLlmPrompt）
- **TASK-0057**: DB マイグレーション version 2→3
- **TASK-0058**: LlmSettingsRepository・DataStore+EncryptedSharedPreferences実装
- **TASK-0059**: LlmRewriteResult・DTO実装
- **TASK-0060**: LlmRewriteRepository実装
- **TASK-0061**: LlmModule Hilt DI設定
- **TASK-0062**: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加

### 後続タスク
- **TASK-0065**: EditScreen UI「メモを更改」ボタン・ローディング・エラーToast実装
- **TASK-0068**: タグ提案機能（Should Have）
- **TASK-0072**: カスタムフィールドのLLM生成（Could Have）

---

**作成日**: 2026-07-06 by tsumiki:tdd-tasknote  
**最終確認**: TASK-0063 開発開始前
