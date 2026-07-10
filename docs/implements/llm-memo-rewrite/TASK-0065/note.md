# TASK-0065 開発コンテキストノート

**タスクID**: TASK-0065  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: EditScreen UI「メモを更改」ボタン・ローディング・エラーToast実装

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
- **非同期処理**: Kotlin Coroutines + viewModelScope + SharedFlow + LaunchedEffect

### テスト・フレームワーク
- **テストフレームワーク**: Compose UI Test (JUnit 4 + androidx.compose.ui.test)
- **テストランナー**: AndroidJUnit4 (Instrumented Tests / connectedAndroidTest)
- **テスト環境**: createAndroidComposeRule<ComponentActivity>() - BackHandler 検証対応
- **補助ツール**: MockK (Hilt ViewModel・Repository モック用)

### 依存関係（既に導入済み）
- `androidx-compose-ui-test-junit4` - Compose UI Test フレームワーク
- `androidx-junit` - AndroidJUnit4 ランナー
- `Hilt` - DI フレームワーク
- `MockK` - モック・スタブ作成用
- `Kotlin Coroutines Test` - 非同期テスト用

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ（composeBom, espressoCore, androidxJunit等）
- `app/build.gradle.kts` - 依存関係定義
- `app/src/main/res/values/strings.xml` - UI文字列リソース（button_rewrite_body, error_llm_*）

---

## 2. 開発ルール

### TDD開発フロー（推奨）
1. `/tsumiki:tdd-requirements TASK-0065` - 詳細要件定義
2. `/tsumiki:tdd-testcases` - テストケース作成
3. `/tsumiki:tdd-red` - テスト実装（失敗状態）
4. `/tsumiki:tdd-green` - 最小実装（テスト通過）
5. `/tsumiki:tdd-refactor` - リファクタリング
6. `/tsumiki:tdd-verify-complete` - 品質確認・完了

### コーディング規約

#### 実装パターン（TASK-0065の要点）

**EditScreen への「メモを更改」ボタン追加**:
```kotlin
Button(
    onClick = { viewModel.rewriteBody() },
    enabled = formState.rewriteBodyEnabled && !formState.isRewritingBody,
    modifier = Modifier.testTag("rewrite_body_button"),
) {
    if (formState.isRewritingBody) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
    } else {
        Text(stringResource(R.string.button_rewrite_body))
    }
}
```

**エラーToast表示パターン**:
```kotlin
val context = LocalContext.current
LaunchedEffect(Unit) {
    viewModel.errorEvents.collectLatest { resId ->
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_LONG).show()
    }
}
```

#### テスト命名規約（既存パターンから）
- テストクラス: `EditScreenTest.kt`（既存ファイルに追加）
- テストメソッド: ```fun `説明的なテスト名`() {}``` （バッククォートで日本語使用可）
- Arrange-Act-Assert（AAA）パターンで構成
- Compose UI Test: `composeTestRule.onNodeWithTag()`, `performClick()`, `assertIsDisplayed()` 等を使用

#### Compose UI Test パターン
```kotlin
@RunWith(AndroidJUnit4::class)
class EditScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()
    
    @Test
    fun `テスト説明`() {
        // Arrange
        val viewModel = /* モックまたは実装インスタンス */
        
        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
            )
        }
        
        // Act
        composeTestRule.onNodeWithTag("rewrite_body_button").performClick()
        
        // Assert
        composeTestRule.onNodeWithTag("rewrite_body_button").assertIsDisplayed()
    }
}
```

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0065.md` - タスク定義・テスト要件詳細
- `docs/design/llm-memo-rewrite/architecture.md` - 「変更が必要な既存コンポーネント」セクション（EditScreen.kt）
- `docs/spec/llm-memo-rewrite/requirements.md` - REQ-001, REQ-102, REQ-201, NFR-201, NFR-202

---

## 3. 関連実装

### 既に実装済みのコンポーネント

#### EditScreenViewModel（TASK-0063で完了）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`
- **実装内容**:
  - `@HiltViewModel` デコレータ
  - `@Inject constructor(llmRewriteRepository, llmSettingsRepository)` - 依存関係注入
  - `rewriteBody()` メソッド - ビューモデル側の非同期処理実装済み
  - `errorEvents: SharedFlow<Int>` - エラーイベント発行準備完了
  - `formState.isRewritingBody`, `formState.rewriteBodyEnabled` - 状態管理フィールド

#### strings.xml（TASK-0064で完了）
- **位置**: `app/src/main/res/values/strings.xml`
- **実装内容**:
  - `button_rewrite_body` = 「メモを更改」
  - `error_llm_network`, `error_llm_auth`, `error_llm_timeout`, `error_llm_empty_response`, `error_llm_unknown` - エラーメッセージ

### TASK-0065で実装する部分

#### 1. EditScreen への「メモを更改」ボタン追加
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`
- **作業内容**:
  - body_field（`OutlinedTextField(..., modifier = Modifier.testTag("body_field"))`）付近にボタンを追加
  - `rewriteBodyEnabled && !isRewritingBody` で活性/非活性制御
  - `isRewritingBody` 時は `CircularProgressIndicator` 表示、通常時は「メモを更改」テキスト表示
  - onClick: `viewModel.rewriteBody()` を呼び出し
  - testTag: `"rewrite_body_button"` を設定

#### 2. エラーToast表示の実装
- **変更箇所**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`
- **作業内容**:
  - `LaunchedEffect(Unit)` 内で `viewModel.errorEvents.collectLatest { resId -> ... }` を購読
  - `Toast.makeText(context, context.getString(resId), Toast.LENGTH_LONG).show()` でToast表示
  - 既存パターン（MainActivity の `ActivityNotFoundException` 処理）と同様のトーンを踏襲

### 既存実装（参考パターン）

#### EditScreen の構造（既存：body_field 付近）
- **位置**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt` (100-200行目付近)
- **内容**: Column スクロール可能、OutlinedTextField (body_field)、bottomBar に送信/キャンセルボタン

#### Compose UI Test パターン（既存）
- **位置**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt`
- **参照例**:
  - `createAndroidComposeRule<ComponentActivity>()` 設定
  - `composeTestRule.setContent { EditScreen(...) }` での画面描画
  - `composeTestRule.onNodeWithTag("body_field").performTextInput()` - テキスト入力
  - `composeTestRule.onNodeWithTag("button_send").performClick()` - ボタン押下
  - `composeTestRule.onNodeWithText("テキスト").assertIsDisplayed()` - 表示確認

#### LaunchedEffect による購読パターン
- **参照ドキュメント**: `docs/design/llm-memo-rewrite/design-interview.md` Q3
- **用途**: errorEvents（SharedFlow<Int>）のToast表示
- **既存パターン**: MainActivity での `ActivityNotFoundException` Toast（NFR-201 対応）

---

## 4. 設計文書

### 要件定義
- **位置**: `docs/spec/llm-memo-rewrite/requirements.md`
- **対応要件**:
  - REQ-001 - EditScreen に「メモを更改」ボタン表示
  - REQ-102 - プロンプト未設定時のボタン非活性化（UI反映）
  - REQ-201 - ローディング状態管理表示
  - NFR-201 - 日本語Toastエラーメッセージ表示
  - NFR-202 - ローディング表示によるUX向上

### アーキテクチャ設計
- **位置**: `docs/design/llm-memo-rewrite/architecture.md`
- **重要セクション**:
  - 「変更が必要な既存コンポーネント」- EditScreen.kt の変更内容詳細
  - 「LLM設定管理設計」- errorEvents 購読パターン
  - データフロー図（editscreen-uiflow.md）

### デザインヒアリング
- **位置**: `docs/design/llm-memo-rewrite/design-interview.md`
- **重要確認事項**:
  - Q2: EditScreenViewModel の Hilt化（TASK-0063で完了）
  - Q3: SharedFlow によるエラー通知パターン（ここで UI実装）

### タスク定義
- **位置**: `docs/tasks/llm-memo-rewrite/TASK-0065.md`
- **重要項目**:
  - 実装詳細: 2個の実装パターン（ボタン追加、エラーToast）
  - UI/UX要件: 3項目（ローディング状態、エラー表示、モバイル対応）
  - テスト要件: 3ケース（TC-1, TC-2, TC-3）

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### Compose UI Test（AndroidJUnit4）
- **設定ファイル**: `app/build.gradle.kts`
  - `androidTestImplementation(libs.androidx.compose.ui.test.junit4)`
  - `androidTestImplementation(libs.androidx.junit)`
- **テストディレクトリ**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/`
- **テストランナー**: AndroidJUnit4（デバイス/エミュレータ必須）

### 既存テストのディレクトリ構成

```
app/src/androidTest/java/com/den4dr/share2Obsidian/ui/
├── EditScreenTest.kt （既存: 初期値表示等のテスト）
├── SettingsScreenTest.kt
├── template/
│   ├── TemplateListScreenTest.kt
│   └── TemplateEditScreenTest.kt
└── LoadingScreenTest.kt
```

### テストユーティリティ・モック設定

#### BackHandler検証対応
```kotlin
@RunWith(AndroidJUnit4::class)
class EditScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()
}
```

#### ViewModel モック（MockK）
```kotlin
val mockViewModel = mockk<EditScreenViewModel>()
coEvery { mockViewModel.rewriteBody() } just Runs
every { mockViewModel.formState } returns MutableStateFlow(
    EditFormState(
        rewriteBodyEnabled = true,
        isRewritingBody = false,
        body = "テスト本文",
        // ... その他フィールド
    )
)
every { mockViewModel.errorEvents } returns MutableSharedFlow()
```

#### Compose UI Test 基本操作
```kotlin
// ノード取得
composeTestRule.onNodeWithTag("rewrite_body_button")

// 操作
.performClick()                    // クリック
.performTextInput("入力テキスト")  // テキスト入力

// 検証
.assertIsDisplayed()               // 表示確認
.assertIsNotEnabled()              // 非活性確認
.assertIsEnabled()                 // 活性確認
.assertCountEquals(1)              // 数量確認（onAllNodes対象）
```

### テスト実行コマンド

```bash
# 全Instrumented Tests（デバイス/エミュレータ必須）
mise exec -- ./gradlew connectedAndroidTest

# EditScreen関連テストのみ実行
mise exec -- ./gradlew connectedAndroidTest --tests "*EditScreenTest*"

# 特定テストクラス実行
mise exec -- ./gradlew connectedAndroidTest --tests "com.den4dr.share2Obsidian.ui.EditScreenTest"
```

### テストカバレッジ期待値

- **対象**: EditScreen への「メモを更改」ボタン UI実装・errorEvents 購読
- **テストケース**:
  - TC-1: `rewriteBodyEnabled=false` 時ボタン非活性表示
  - TC-2: `rewriteBodyEnabled=true` 時ボタン押下でrewriteBody() 呼び出し
  - TC-3: `isRewritingBody=true` 時ローディングインジケータ表示・ボタン非活性
  - （追加）errorEvents 発行時Toast表示確認（ただし Toast表示検証の難易度から、emit 検証のみでOK）

---

## 6. 注意事項

### 技術的制約

#### EditScreen のレイアウト構造
- TASK-0065 ではbottomBar（送信/キャンセルボタン）の変更は不要
- body_field 直近（その直下または同じ Row内）に「メモを更改」ボタンを追加する（既存 EditScreen パターン踏襲）
- body_field 自体のテストTag は変更しない（既存テストとの互換性）

#### LaunchedEffect による購読の安全性
- `LaunchedEffect(Unit)` で単一回限りの購読セットアップ（画面描画時のみ1回実行）
- `collectLatest` は前の購読を自動キャンセルするため、重複購読の心配なし（リソースリーク対策）
- Toast.makeText() は UI スレッドで安全に実行される（Compose UI スレッドコンテキスト内）

#### ViewModel のライフサイクル
- TASK-0063 での `@HiltViewModel` 化により、EditScreen と EditScreenViewModel のライフサイクルが同期される
- errorEvents の SharedFlow は ViewModel 内で保持されるため、EditScreen 再構成時も同一の Flow インスタンスを購読する

#### CircularProgressIndicator サイズ
- 標準ボタン内に表示するため、size=16.dp, strokeWidth=2.dp（テンプレートサイズ）
- ボタンの高さとのバランス（既存ボタンが約48.dp）で自動で収まる

### セキュリティ・パフォーマンス要件

#### エラーメッセージ管理
- すべてのエラー文字列は `app/src/main/res/values/strings.xml` から stringResource() で取得（NFR-201, NFR-102 対応）
- エラーメッセージに機微情報（APIキー、エンドポイント等）を含まない（既に実装済み）

#### UI描画パフォーマンス
- CircularProgressIndicator は Compose の標準コンポーネント（最適化済み）
- LaunchedEffect での Flow 購読は lightweight（重いポーリング処理でない）

#### メモリリーク対策
- `collectLatest` は購読終了時に自動でキャンセル（LaunchedEffect スコープ終了時）
- Toast は Context.getString() で生成され、自動ガベージコレクション対象

### 参考ドキュメント関連図

**データフロー** (TASK-0065 UI実装部分):
```
EditScreen「メモを更改」ボタン表示
  ↓ (formState.rewriteBodyEnabled && !isRewritingBody により活性/非活性制御)
ユーザーがボタン押下
  ↓
onClick handler → viewModel.rewriteBody() を呼び出し
  ↓
（TASK-0063で実装済みの rewriteBody() が非同期処理を実行）
  ↓
  ├→ 成功時: formState.body を上書き、EditScreen自動再構成で本文表示更新
  ├→ 失敗時: errorEvents に messageResId を emit
  │    ↓
  │ LaunchedEffect で errorEvents を collectLatest 購読
  │    ↓
  │ Toast.makeText(context, context.getString(resId), Toast.LENGTH_LONG).show()
  │
  ├→ 常に: formState.isRewritingBody を false へ状態更新
       ↓
    ボタン内の CircularProgressIndicator が非表示に戻る
```

**参照元**: `docs/design/llm-memo-rewrite/dataflow.md`

---

## 実装チェックリスト（目安）

### 実装フェーズ
- [ ] EditScreen の body_field 付近
  - [ ] Button 要素の追加
  - [ ] `onClick = { viewModel.rewriteBody() }` ハンドラ設定
  - [ ] `enabled = formState.rewriteBodyEnabled && !formState.isRewritingBody` で活性制御
- [ ] ボタン内容の条件分岐
  - [ ] `if (formState.isRewritingBody)` 時に CircularProgressIndicator 表示
  - [ ] 通常時は `Text(stringResource(R.string.button_rewrite_body))` 表示
- [ ] testTag 設定
  - [ ] `modifier = Modifier.testTag("rewrite_body_button")` を追加
- [ ] エラーToast表示
  - [ ] `val context = LocalContext.current` を追加
  - [ ] `LaunchedEffect(Unit) { ... }` ブロック追加
  - [ ] `viewModel.errorEvents.collectLatest { resId -> ... }` で購読
  - [ ] `Toast.makeText(context, context.getString(resId), Toast.LENGTH_LONG).show()` で表示

### テストフェーズ
- [ ] Compose UI Test 3ケース（TASK-0065.md 定義分）
  - [ ] TC-1: rewriteBodyEnabled=false の場合ボタンが非活性
  - [ ] TC-2: rewriteBodyEnabled=true かつボタン押下で viewModel.rewriteBody() が呼ばれる
  - [ ] TC-3: isRewritingBody=true 中はローディング表示・ボタン非活性
- [ ] 既存テストスイートの確認（後方互換性）
  - [ ] EditScreenTest（既存テスト全項目）の確認・実行

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
- **TASK-0063**: EditScreenViewModel Hilt化・rewriteBody()実装
- **TASK-0064**: strings.xml 「メモを更改」ボタン・エラーメッセージ追加

### 後続タスク
- **TASK-0073**: タグ提案機能（Should Have）

---

**作成日**: 2026-07-06 by tsumiki:tdd-tasknote  
**最終確認**: TASK-0065 TDD開発開始前
