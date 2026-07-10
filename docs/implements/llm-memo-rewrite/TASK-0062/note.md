# TASK-0062 開発コンテキストノート

**タスクID**: TASK-0062  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加

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
- **非同期処理**: Kotlin Coroutines + lifecycleScope
- **テストフレームワーク**: JUnit 4 + Robolectric

### 依存関係（既に導入済み）
- `JUnit 4` - ユニットテスト用
- `Robolectric` - Android リソース依存テスト用（MainActivity のシミュレーション）
- `Kotlin Coroutines Test` - 非同期テスト用
- `androidx-test` - Android テストフレームワーク

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ
- `app/build.gradle.kts` - 依存関係定義

---

## 2. 開発ルール

### TDD開発フロー（推奨）
1. `/tsumiki:tdd-requirements TASK-0062` - 詳細要件定義（REQ-002, REQ-406, REQ-101, REQ-102）
2. `/tsumiki:tdd-testcases` - テストケース作成（3個の単体テスト）
3. `/tsumiki:tdd-red` - テスト実装（失敗状態）
4. `/tsumiki:tdd-green` - 最小実装（テスト通過）
5. `/tsumiki:tdd-refactor` - リファクタリング（品質向上）
6. `/tsumiki:tdd-verify-complete` - 品質確認・完了

### コーディング規約

#### 実装パターン
- **本体**: `MainActivity.onCreate()` 内の特定箇所（約105行目）
- **引数追加**: `EditScreenViewModel.initialize()` のシグネチャ拡張
- **形式**: 既存実装の変更は最小限に（additive な引数追加のみ）

#### テスト命名規約（既存パターンから）
- テストファイル: `MainActivity.kt` → `MainActivityTest.kt` または統合テストファイル
- テストメソッド: ```fun `説明的なテスト名`() {}``` （バッククォートで日本語使用可）
- Arrange-Act-Assert（AAA）パターンで構成

#### 実装の詳細パターン
```kotlin
// ===== MainActivity.onCreate() 内（約103-107行目付近）=====

// BEFORE:
val processed = /* ContentProcessor.process() の結果 */
val defaultTemplate = templateRepository.getDefaultTemplate()
val noteSettings = noteSettingsRepository.getSettings().first()
val config = TemplateApplicator.buildConfig(noteSettings)
val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
val customFields = TemplateApplicator.buildCustomFields(defaultTemplate, processed)
viewModel.initialize(processed.copy(body = resolvedBody), config, customFields)

// AFTER:
val processed = /* ContentProcessor.process() の結果 */
val defaultTemplate = templateRepository.getDefaultTemplate()
val noteSettings = noteSettingsRepository.getSettings().first()
val config = TemplateApplicator.buildConfig(noteSettings)
val sourceContent = processed.body  // 🔵 REQ-406: テンプレート適用前の元コンテンツを退避
val bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty()  // 🔵 REQ-002, REQ-101
val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
val customFields = TemplateApplicator.buildCustomFields(defaultTemplate, processed)
viewModel.initialize(
    processed = processed.copy(body = resolvedBody),
    config = config,
    customFields = customFields,
    sourceContent = sourceContent,           // 🔵 新規引数
    bodyLlmPrompt = bodyLlmPrompt,           // 🔵 新規引数
)
```

#### EditScreenViewModel.initialize() シグネチャ変更
```kotlin
// BEFORE:
fun initialize(
    processed: ProcessedContent,
    config: NoteConfig,
    customFields: List<CustomFieldState> = emptyList(),
)

// AFTER:
fun initialize(
    processed: ProcessedContent,
    config: NoteConfig,
    customFields: List<CustomFieldState> = emptyList(),
    sourceContent: String = "",             // 🔵 新規引数
    bodyLlmPrompt: String = "",             // 🔵 新規引数
)
```

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0062.md` - タスク定義・要件詳細
- `docs/design/llm-memo-rewrite/architecture.md` - 「ProcessedContent保持設計」セクション
- `docs/spec/llm-memo-rewrite/requirements.md` - REQ-002, REQ-406, REQ-101, REQ-102

---

## 3. 関連実装

### 変更対象ファイル

#### 1. MainActivity.kt（ロジック変更）
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt`

**変更箇所**: `onCreate()` メソッド内（約100～107行目）
- テンプレート適用前の `processed.body` を `sourceContent` 変数に退避
- `defaultTemplate?.bodyLlmPrompt.orEmpty()` を算出して `bodyLlmPrompt` 変数に格納
- `viewModel.initialize()` の呼び出し時に新規引数 `sourceContent`, `bodyLlmPrompt` を追加

**変更内容の狙い**: 
- REQ-406（テンプレート適用前の元コンテンツが EditScreenViewModel に到達）を実現
- REQ-002（LLM入力用プロンプト）・REQ-101（テンプレートの bodyLlmPrompt）を EditScreenViewModel に提供

**信頼性**: 🔵 *architecture.md「ProcessedContent保持設計」より*

#### 2. EditScreenViewModel.kt（メソッドシグネチャ拡張）
**位置**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`

**変更箇所**: `initialize()` メソッドのシグネチャ（約58行目）
- 新規引数 `sourceContent: String = ""` を追加
- 新規引数 `bodyLlmPrompt: String = ""` を追加
- EditFormState の初期化処理では、これらの値を保持する（UI フォーム状態には含めない。後続タスク TASK-0063 で ViewModel内のプライベートプロパティとして活用）

**変更内容の狙い**:
- MainActivity から LLM入力用の元コンテンツ（sourceContent）を受け取る
- テンプレートのプロンプト（bodyLlmPrompt）を受け取る
- これらは ViewModel 内部で保持し、後続タスク TASK-0063 の `rewriteBody()` 実装時に使用される

**信頼性**: 🔵 *architecture.md「EditScreenViewModel設計変更」より*

### 既存実装（参考）

#### 既存パターン
- `app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt` - テンプレート解決パターン
- `app/src/main/java/com/den4dr/share2Obsidian/content/ContentProcessor.kt` - コンテンツ処理パターン

#### 既存テストパターン
- `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt` - Robolectric での MainActivity テスト例
- `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt` - Kotlin テストパターン（命名規約）

---

## 4. 設計文書

### 要件定義
- **位置**: `docs/spec/llm-memo-rewrite/requirements.md`
- **対応要件**: REQ-002（LLM リライト機能）, REQ-406（元コンテンツ保持）, REQ-101（テンプレート bodyLlmPrompt）, REQ-102（プロンプト未設定時のフォールバック）
- **内容**: 機能要件・エッジケース・制約要件

### アーキテクチャ設計
- **位置**: `docs/design/llm-memo-rewrite/architecture.md`
- **重要セクション**:
  - 「ProcessedContent保持設計」：課題・解決方針・ViewModel 内での保持方法
  - 「EditScreenViewModel設計変更」：新規引数の詳細
  - 「変更が必要な既存コンポーネント」：Template.bodyLlmPrompt の追加

### タスク定義
- **位置**: `docs/tasks/llm-memo-rewrite/TASK-0062.md`
- **重要項目**:
  - 実装詳細：sourceContent 退避・bodyLlmPrompt 算出
  - 単体テスト要件：3ケース（TC-001: sourceContent値検証、TC-002: bodyLlmPrompt値検証、TC-003: null時フォールバック）
  - 依存タスク：TASK-0057（DB Migration）完了、TASK-0063（ViewModel Hilt化・rewriteBody()実装）に続く

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### ユニットテスト（JUnit 4 + Robolectric）
- **設定ファイル**: `app/build.gradle.kts`
  - `testImplementation(libs.junit)`
  - `testImplementation(libs.robolectric)`
  - `testImplementation(libs.androidx.test.core)` （ShadowApplication等）
  - `testImplementation(libs.androidx.test.runner)` （テストランナー）
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/`
- **テストランナー**: JUnit 4（Robolectric組み込み対応）

### 既存テストのディレクトリ構成

```
app/src/test/java/com/den4dr/share2Obsidian/
├── MainActivityEditFlowTest.kt （Robolectric で MainActivity テスト）
├── content/
│   └── ContentProcessorTest.kt
├── data/
│   ├── llm/
│   │   ├── ChatCompletionDtoTest.kt
│   │   ├── LlmRewriteResultTest.kt
│   │   ├── LlmSettingsRepositoryImplTest.kt
│   │   └── LlmRewriteRepositoryImplTest.kt
├── domain/model/
│   ├── TemplateTest.kt
│   └── FieldValueSourceTest.kt
├── format/
│   └── NoteComposerTest.kt
└── ExampleUnitTest.kt
```

### テストユーティリティ・モック設定

#### Robolectric による Android リソーステスト
```kotlin
@RunWith(RobolectricTestRunner::class)
class MainActivityEditFlowTest {
    // Intent + ProcessedContent をモック
    // viewModel.initialize() への引数を検証
}
```

#### MockK によるモック・スタブ
```kotlin
val mockTemplateRepository = mockk<TemplateRepository>()
coEvery { mockTemplateRepository.getDefaultTemplate() } returns templateWithPrompt

val mockNoteSettingsRepository = mockk<NoteSettingsRepository>()
coEvery { mockNoteSettingsRepository.getSettings() } returns flowOf(noteSettings)
```

#### 既存テスト参考パターン
- `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt` - Robolectric + Hilt テストパターン
- `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt` - data class テストパターン（等価性検証）

### テスト実行コマンド

```bash
# ユニットテスト（ローカル JVM）
mise exec -- ./gradlew test

# TASK-0062 関連テストのみ実行
mise exec -- ./gradlew test --tests "MainActivity*Test"
```

### テストカバレッジ期待値
- **対象**: MainActivity.onCreate() の sourceContent・bodyLlmPrompt 設定箇所
- **期待カバレッジ**: 正常系（値の正確性）+ エッジケース（null フォールバック）

---

## 6. 注意事項

### 技術的制約

#### ProcessedContent の不変性
- TASK-0062 では `processed` オブジェクト本体の body フィールドを上書きしない（既存パターン踏襲）
- 代わりに `processed.copy(body = resolvedBody)` で新規オブジェクトを生成する（既に実装済み）
- `sourceContent` は別変数として保持

#### Template.bodyLlmPrompt の前提
- `Template` ドメインモデルに `bodyLlmPrompt: String = ""` フィールドが既に存在していることを前提とする（TASK-0055～0056で追加済み）
- TASK-0057（DB Migration version 2→3）で テーブルにカラムが追加されているはず

#### EditScreenViewModel の拡張
- TASK-0062 では `sourceContent` / `bodyLlmPrompt` を単にシグネチャに追加するだけ
- ViewModel 内での保持方法・使用方法（`rewriteBody()` メソッド等）は後続 TASK-0063 で実装

### セキュリティ・パフォーマンス要件

#### LLM プロンプトの取り扱い
- `defaultTemplate?.bodyLlmPrompt` の値は平文で ViewModel に渡される（ユーザー側で設定したテンプレート設定の参照）
- 以降 TASK-0063 で API 通信時に LLM Settings（apiKey等）と組み合わせて使用される

#### null フォールバック
- `defaultTemplate` が null の場合（デフォルトテンプレート未設定時）、`orEmpty()` で空文字に統一（REQ-102）
- ViewModel 側で `bodyLlmPrompt.isBlank()` チェックして UI 側でボタン制御（後続タスク）

### 参考ドキュメント関連図

**データフロー** (TASK-0062 処理箇所):
```
MainActivity.onCreate()
  ↓
1. ContentProcessor で共有コンテンツ処理 → ProcessedContent.body（元コンテンツ）
  ↓
2. sourceContent = processed.body （テンプレート適用前の値を退避）
  ↓
3. TemplateApplicator.buildBody() → resolvedBody（テンプレート適用後の値）
  ↓
4. bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty()
  ↓
5. viewModel.initialize(..., sourceContent, bodyLlmPrompt)
  ↓
EditScreenViewModel に渡される
  ├→ sourceContent（LLM入力用、TASK-0063 の rewriteBody() で使用）
  ├→ bodyLlmPrompt（テンプレート設定プロンプト）
  └→ processed.body（resolvedBody, UI フォーム初期値）
```

**参照元**: `docs/design/llm-memo-rewrite/dataflow.md`

---

## 実装チェックリスト（目安）

### 実装フェーズ
- [ ] MainActivity.onCreate() 内で sourceContent 退避ロジック追加
  - [ ] `val sourceContent = processed.body` を `TemplateApplicator.buildBody()` 呼び出し前に配置
  - [ ] `val bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty()` を追加
  - [ ] `viewModel.initialize()` に新規引数を追加
- [ ] EditScreenViewModel.initialize() シグネチャ拡張
  - [ ] 新規引数 `sourceContent: String = ""` を追加
  - [ ] 新規引数 `bodyLlmPrompt: String = ""` を追加
  - [ ] 後続 TASK-0063 用に ViewModel 内プライベートプロパティとして保持準備

### テストフェーズ
- [ ] 単体テスト3ケース（Robolectric）
  - [ ] TC-1: sourceContent がテンプレート解決前の値であること
  - [ ] TC-2: bodyLlmPrompt がテンプレートの値と一致すること
  - [ ] TC-3: defaultTemplate が null の場合 bodyLlmPrompt が空文字になること
- [ ] 既存テストスイートの確認（後方互換性）
  - [ ] MainActivity 関連テスト群
  - [ ] EditScreenViewModel 関連テスト群

---

## 関連タスク

### 前提タスク
- **TASK-0055**: Template/TemplateField ドメインモデル・DB マイグレーション準備（完了）
- **TASK-0056**: Template ドメインモデル LLM フィールド追加（bodyLlmPrompt）（完了）
- **TASK-0057**: DB マイグレーション version 2→3（完了）
- **TASK-0058～0061**: LLM リポジトリ・DTO・Settings 実装（完了）

### 後続タスク
- **TASK-0063**: EditScreenViewModel Hilt化・rewriteBody() 実装（TASK-0062 完了後）

---

**作成日**: 2026-07-06 by tsumiki:tdd-tasknote  
**最終確認**: TASK-0062 開発開始前
