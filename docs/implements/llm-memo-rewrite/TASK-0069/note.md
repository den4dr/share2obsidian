# TASK-0069: EditScreen「タグを提案」ボタンUI追加 - TDD コンテキストノート

**タスクID**: TASK-0069
**機能名**: llm-memo-rewrite
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-08
**フェーズ**: Phase 5 - タグ提案（Should Have）
**推定工数**: 4時間
**前提タスク**: TASK-0068 (EditScreenViewModel suggestTags()実装完了), TASK-0064 (strings.xml新規文字列追加)
**後続タスク**: TASK-0073 (統合テスト・最終品質確認)

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **Android Gradle Plugin**: 9.1.0
- **minSdk**: 33 (Android 13)
- **targetSdk/compileSdk**: 36
- **Java互換性**: 11
- **UI フレームワーク**: Jetpack Compose BOM 2024.09.00
  - Material3 コンポーネント
  - State management: `StateFlow<T>` + `@HiltViewModel`
- **非同期処理**: Kotlin Coroutines + `viewModelScope`
- **DI**: Hilt 2.56+
- **ViewModel**: AndroidX ViewModel (`androidx.lifecycle.ViewModel`)
- **依存関係管理**: gradle/libs.versions.toml (Version Catalog)

### LLM統合技術スタック
- **HTTP通信**: Ktor Client (CIO エンジン) + タイムアウト設定（30秒）
- **シリアライズ**: kotlinx-serialization
- **暗号化ストレージ**: androidx.security-crypto (EncryptedSharedPreferences)
- **API形式**: OpenAI互換 Chat Completions

### テストフレームワーク
- **ユニットテスト**: JUnit 4
- **Compose UI テスト**: Jetpack Compose UI Test + Espresso
- **Androidテスト**: androidx.test 1.6.1
- **モック**: MockK (io.mockk)
- **テストランナー**: 
  - Unit: Robolectric 4.14.1 (@RunWith(RobolectricTestRunner::class))
  - Instrumented: AndroidJUnit4 (@RunWith(AndroidJUnit4::class))

### ビルドコマンド
```bash
mise exec -- ./gradlew test                      # ユニットテスト実行
mise exec -- ./gradlew connectedAndroidTest      # インストゥルメントテスト（デバイス/エミュレータ必要）
mise exec -- ./gradlew assembleDebug             # デバッグビルド
mise exec -- ./gradlew lint                      # Lint チェック
```

**参照元**:
- docs/tech-stack.md
- gradle/libs.versions.toml
- app/build.gradle.kts

---

## 2. 開発ルール

### プロジェクト固有ルール
- **シングルアクティビティ アーキテクチャ**: MainActivity のみが存在
  - UI変更は `setContent { }` 内で Compose を使用
  - 新規 Activity 作成は禁止
- **LLM設定管理**: APIキーは暗号化ストレージ(EncryptedSharedPreferences)に保存（平文DataStore禁止）
- **日本語ローカライズ**: エラーメッセージ・UI文字列は `res/values/strings.xml` で定義（NFR-201）
- **既存パターン踏襲**: 本文リライトボタン（TASK-0065）と同一のローディング表示パターンを使用

### Kotlin / Compose コーディング規約
- **StateFlow の使用**: ViewModel 内で `MutableStateFlow<State>` で状態管理
- **@HiltViewModel**: EditScreenViewModel は依存注入対象
- **データクラス**: EditFormState に `isSuggestingTags: Boolean` を追加
- **名前空間**:
  - `ui/` パッケージ: Composable 関数・ViewModel
  - `data/llm/` パッケージ: LLM関連Repository・DTO
  - `domain/model/` パッケージ: ドメインモデル
- **テストコメント**:
  - 【テスト目的】【テスト内容】【期待される動作】の3点セットで記述
  - 信頼性レベル（🔵/🟡/🔴）を明記

### ViewModel 実装パターン（TASK-0069用）
```kotlin
@HiltViewModel
class EditScreenViewModel @Inject constructor(
    private val llmRewriteRepository: LlmRewriteRepository,
    private val llmSettingsRepository: LlmSettingsRepository,
) : ViewModel() {
    private val _formState = MutableStateFlow<EditFormState>(...)
    val formState: StateFlow<EditFormState> = _formState.asStateFlow()
    
    private val _errorEvents = MutableSharedFlow<Int>()
    val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()
    
    // sourceContent, bodyLlmPrompt はプライベートプロパティ
    private var sourceContent: String = ""
    private var bodyLlmPrompt: String = ""

    fun suggestTags() {
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isSuggestingTags = true)
            // LLM呼び出し処理...
            _formState.value = _formState.value.copy(isSuggestingTags = false)
        }
    }
}
```

**参照元**:
- docs/design/llm-memo-rewrite/architecture.md - ViewModel設計方針
- docs/spec/llm-memo-rewrite/requirements.md - REQ-103, REQ-301, REQ-302, REQ-406

---

## 3. 関連実装

### 既存コンポーネント（参照用）
| クラス | パッケージ | 役割 | ファイルパス |
|--------|----------|------|----------|
| `EditScreen` | ui | Compose UI | app/src/main/java/.../ui/EditScreen.kt |
| `EditScreenViewModel` | ui | ViewModel（TASK-0068で実装済）| app/src/main/java/.../ui/EditScreenViewModel.kt |
| `EditFormState` | ui | 状態データクラス | app/src/main/java/.../ui/EditFormState.kt |
| `LlmRewriteRepository` | data/llm | LLM呼び出しインターフェース | app/src/main/java/.../data/llm/LlmRewriteRepository.kt |
| `LlmSettingsRepository` | data/llm | LLM設定読み書き | app/src/main/java/.../data/llm/LlmSettingsRepository.kt |
| `ProcessedContent` | content | 元コンテンツ（LLM入力ソース） | app/src/main/java/.../content/ProcessedContent.kt |

### テスト参考実装
| テストファイル | テスト対象 | ファイルパス |
|----------|----------|----------|
| `EditScreenTest.kt` | EditScreen Composable | app/src/androidTest/java/.../ui/EditScreenTest.kt |
| `EditScreenViewModelSuggestTagsTest.kt` | suggestTags()メソッド | app/src/test/java/.../ui/EditScreenViewModelSuggestTagsTest.kt |
| `EditScreenViewModelTest.kt` | ViewModel基本動作 | app/src/test/java/.../ui/EditScreenViewModelTest.kt |

### 参考パターン
- **UI ローディング表示**: TASK-0065「メモを更改」ボタン（isRewritingBody）と同一パターン
  ```kotlin
  Button(
      onClick = { viewModel.suggestTags() },
      enabled = !formState.isSuggestingTags,
  ) {
      if (formState.isSuggestingTags) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      } else {
          Text(stringResource(R.string.button_suggest_tags))
      }
  }
  ```
- **Compose UI テスト**: EditScreenTest.kt 参照（onNodeWithText()・performClick()パターン）
- **ViewModel テスト**: EditScreenViewModelSuggestTagsTest.kt 参照（MockK + runTest()パターン）
- **エラー処理**: errorEvents SharedFlow を LaunchedEffect で購読

**参照元**:
- docs/design/llm-memo-rewrite/architecture.md - コンポーネント構成表
- docs/tasks/llm-memo-rewrite/TASK-0065.md - 「メモを更改」ボタン実装
- docs/tasks/llm-memo-rewrite/TASK-0068.md - suggestTags()実装

---

## 4. 設計文書

### 要件定義
- **統合要件**: docs/spec/llm-memo-rewrite/requirements.md
  - REQ-103: タグ提案UI表示要件
  - REQ-201: LLM呼び出し中のローディング表示
  - REQ-202: 30秒タイムアウト
  - REQ-301/302: タグ提案機能詳細
  - REQ-406: ProcessedContent保持（元コンテンツをLLM入力として使用）
  - NFR-201: 日本語Toastエラー表示
  - NFR-202: ローディング表示
  - EDGE-101: 元コンテンツが空文字でもボタン非活性化しない

### アーキテクチャ・データフロー
- **アーキテクチャ**: docs/design/llm-memo-rewrite/architecture.md
  - 「変更が必要な既存コンポーネント」表参照：EditFormState に isSuggestingTags 追加
  - EditScreenViewModel が suggestTags() メソッドを保持
  - errorEvents SharedFlow でエラー通知
  
- **データフロー**: docs/design/llm-memo-rewrite/dataflow.md
  - タグ提案ボタンタップ → suggestTags() → LLM API呼び出し → タグ追加

### 型定義・インターフェース

- **EditFormState への追加フィールド**:
  ```kotlin
  data class EditFormState(
      val title: String = "",
      val body: String = "",
      val tagsText: String = "",
      val folder: String = "",
      val isRewritingBody: Boolean = false,
      val isSuggestingTags: Boolean = false,      // ← TASK-0069で追加
      val rewriteBodyEnabled: Boolean = false,
      val generatingFieldIndex: Int? = null,
  )
  ```

- **EditScreenViewModel に追加メソッド** (TASK-0068で実装済):
  ```kotlin
  fun suggestTags() {
      // ProcessedContent を入力としてLLMがタグ候補を生成
      // 生成結果を既存タグに追加（既存タグは保持）
  }
  ```

**参照元**:
- docs/spec/llm-memo-rewrite/requirements.md - REQ-103, REQ-301, REQ-302
- docs/spec/llm-memo-rewrite/acceptance-criteria.md - テストケース TC-301
- docs/design/llm-memo-rewrite/architecture.md - 「変更が必要な既存コンポーネント」表
- docs/design/llm-memo-rewrite/interfaces.kt

---

## 5. テスト関連情報

### テストフレームワーク・設定

#### ユニットテスト（JUnit4 + MockK）
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditScreenViewModelSuggestTagsTest {
    // suggestTags() の単体テスト
}
```

#### Compose UI テスト（Jetpack Compose UI Test）
```kotlin
@RunWith(AndroidJUnit4::class)
class EditScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()
    // EditScreen の UI テスト
}
```

### テストディレクトリ構成
```
app/src/test/java/com/den4dr/share2Obsidian/
├── ui/
│   ├── EditScreenViewModelSuggestTagsTest.kt       ← TASK-0069 ユニットテスト
│   ├── EditScreenViewModelTest.kt                  ← 既存（回帰テスト）
│   └── EditScreenViewModelRewriteBodyTest.kt       ← TASK-0065参照パターン
└── ...

app/src/androidTest/java/com/den4dr/share2Obsidian/
├── ui/
│   └── EditScreenTest.kt                           ← TASK-0069 Compose UI テスト
└── ...
```

### TASK-0069 テストケース（docs/spec/llm-memo-rewrite/acceptance-criteria.md より）

#### ユニットテスト (TC-301-01〜)
- **TC-301-01**: suggestTags() 呼び出しで LLM APIが呼ばれ、タグが追加されること
  - 入力: ProcessedContent="旅行の計画について", 既存タグ="private"
  - 期待: タグが "private, 旅行, 計画" に更新される
  - 🔵 ヒアリングQ17より
  
- **TC-301-02**: 元コンテンツ（ProcessedContent）が空文字でもボタン押下可能
  - 入力: ProcessedContent=""
  - 期待: ボタンは活性状態のまま押下可能
  - 🔵 EDGE-101より

#### Compose UI テスト (編集部分のみ抽出)
- **「タグを提案」ボタンが表示されること**
  - composeTestRule.onNodeWithText("タグを提案").assertIsDisplayed()
  - 信頼性: 🔵 REQ-103より

- **isSuggestingTags=true中はローディング表示＆ボタン非活性**
  - formState.isSuggestingTags = true にセット
  - CircularProgressIndicator が表示されることを確認
  - ボタンが非活性（assertIsNotEnabled）であることを確認
  - 信頼性: 🔵 REQ-201, NFR-202より

### テスト実行コマンド
```bash
# ユニットテスト実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"

# Compose UI テスト実行（デバイス/エミュレータ必要）
mise exec -- ./gradlew connectedAndroidTest --tests "com.den4dr.share2Obsidian.ui.EditScreenTest"

# 全テスト実行（回帰テスト）
mise exec -- ./gradlew test

# 特定テスト実行
mise exec -- ./gradlew test --tests "*SuggestTags*"
```

### 既存テストパターン（参考用）
```kotlin
@Test
fun `TC-001 説明`() {
    // 【テスト目的】: xxx が正しく動作すること
    // 【テスト内容】: 典型的な使用例で検証
    // 【期待される動作】: yyy が zz になること
    // 🔵 信頼性レベル: REQ-xxx より

    // Arrange
    val input = ...

    // Act
    val result = ...

    // Assert
    assertEquals(expected, result)
}
```

**参照元**:
- gradle/libs.versions.toml - junit, mockk, androidx-compose-ui-test
- app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt
- docs/spec/llm-memo-rewrite/acceptance-criteria.md - TC-301系テストケース
- docs/tasks/llm-memo-rewrite/TASK-0069.md - テスト要件表

---

## 6. 注意事項

### 技術的制約

#### EditFormState への isSuggestingTags 追加
- **責務分離**: isRewritingBody と同様、ローディング状態を表すBoolean フラグ
- **初期値**: false（ボタンはデフォルト活性）
- **更新タイミング**: suggestTags() 実行開始時に true、終了時に false

#### suggestTags() 実装における入力ソース
- **入力**: 元コンテンツ (`sourceContent`/`ProcessedContent`)
  - EditScreen 上の編集済み tagsText ではない
  - テンプレート適用後の body ではない
  - **重要**: 共有/取得直後の元コンテンツ（REQ-302, REQ-406）
- **入力が空文字でもガードしない** (EDGE-101)
  - ボタンは非活性化されず、空文字のまま LLM API へ送信

#### エラー処理
- **既存パターン踏襲**: 本文リライト（TASK-0065）と同じ errorEvents 購読の仕組みを再利用
- **Toast表示**: EditScreen 側で `LaunchedEffect(viewModel.errorEvents) { ... }` が処理
- **TASK-0069 での追加実装は不要**: TASK-0065 で実装済みの errorEvents 機構が suggestTags() の失敗も処理

### セキュリティ・パフォーマンス

#### セキュリティ
- **APIキー**: EncryptedSharedPreferences に暗号化保存（REQ-401, NFR-101）
- **ログ出力禁止**: APIキー等の機微情報をログ・クラッシュレポートに出力しない（NFR-102）
  - LlmRewriteRepositoryImpl で例外を捕捉し、種別のみ LlmRewriteResult に変換

#### パフォーマンス
- **タイムアウト**: 30秒（REQ-202, NFR-001）
  - Ktor Client HttpTimeout プラグイン で設定済み
- **UI レスポンス**: ローディング表示開始は即座（CircularProgressIndicator）
- **State 更新**: isSuggestingTags の MutableStateFlow 変更は O(1)

#### 入力検証
- 元コンテンツが空文字でもボタン非活性化しない（EDGE-101）
- ボタン非活性化条件: なし（タグ提案はボタンラベルクリックのみ）
- タグパース: 既存の parseTagsText() パターン踏襲（カンマ区切り）

### 注意すべき実装点

1. **strings.xml に新規文字列追加** (TASK-0064で実装済み想定):
   ```xml
   <string name="button_suggest_tags">タグを提案</string>
   ```

2. **EditScreen.kt のボタン配置**:
   - タグ入力欄（OutlinedTextField）の直下に配置
   - ボタン内: isSuggestingTags 中は CircularProgressIndicator、通常はテキスト

3. **EditFormState の isSuggestingTags フィールド**:
   - 既存の isRewritingBody と同じ構造
   - 初期値は false

4. **既存 errorEvents の再利用**:
   - suggestTags() 失敗時に _errorEvents.emit(messageResId) を呼び出し
   - EditScreen側は既に LaunchedEffect で購読済み

### 関連ドキュメント

#### 要件定義
- docs/spec/llm-memo-rewrite/requirements.md (REQ-103, REQ-201, REQ-202, REQ-301, REQ-302, REQ-406, NFR-201, NFR-202, EDGE-101)
- docs/spec/llm-memo-rewrite/user-stories.md
- docs/spec/llm-memo-rewrite/acceptance-criteria.md

#### 設計文書
- docs/design/llm-memo-rewrite/architecture.md
- docs/design/llm-memo-rewrite/dataflow.md
- docs/design/llm-memo-rewrite/api-endpoints.md
- docs/design/llm-memo-rewrite/interfaces.kt

#### 実装タスク
- docs/tasks/llm-memo-rewrite/TASK-0068.md (EditScreenViewModel suggestTags()実装)
- docs/tasks/llm-memo-rewrite/TASK-0064.md (strings.xml新規文字列追加)
- docs/tasks/llm-memo-rewrite/TASK-0073.md (統合テスト)

#### 参考実装タスク
- docs/tasks/llm-memo-rewrite/TASK-0065.md (「メモを更改」ボタン実装 - 参考パターン)

**参照元**:
- docs/spec/llm-memo-rewrite/requirements.md - REQ-103, REQ-201, REQ-202, REQ-301, REQ-302, REQ-406, NFR-201, NFR-202, EDGE-101
- docs/design/llm-memo-rewrite/architecture.md - 「変更が必要な既存コンポーネント」表の EditFormState/EditScreen 変更内容
- docs/tasks/llm-memo-rewrite/TASK-0069.md - タスク詳細
- CLAUDE.md - Build Commands

---

## 7. 依存関係・ブロッキング

### 前提条件（ブロック解除待ち）
- ✅ TASK-0068: EditScreenViewModel suggestTags() 実装完了
- ✅ TASK-0064: strings.xml に `button_suggest_tags` 文字列追加完了
- ✅ TASK-0065: 「メモを更改」ボタン実装完了（参考パターン）

### ブロック対象（本タスク完了後に実行可能）
- TASK-0073: 統合テスト・最終品質確認（TASK-0069 完了後）

### 実装順序
1. **EditFormState に isSuggestingTags フィールド追加**
2. **EditScreen.kt に「タグを提案」ボタンUI実装**
   - タグ入力欄の直下に配置
   - isRewritingBody と同一パターンのローディング表示
3. **ユニットテスト実装** (EditScreenViewModelSuggestTagsTest.kt)
4. **Compose UI テスト実装** (EditScreenTest.kt で「タグを提案」ボタン関連テスト追加)
5. **全テスト実行・回帰テスト確認**

---

## 8. ファイルパス一覧

### 実装対象ファイル
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` (既存ファイルに isSuggestingTags 追加)
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt` (既存ファイルに「タグを提案」ボタン追加)

### テスト対象ファイル
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt` (新規テスト)
- `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt` (既存ファイルに テスト追加)

### 参照ファイル
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` (suggestTags()実装済み)
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt`
- `app/src/main/res/values/strings.xml` (button_suggest_tags 使用)

### 設計・要件ドキュメント
- `docs/spec/llm-memo-rewrite/requirements.md`
- `docs/spec/llm-memo-rewrite/acceptance-criteria.md`
- `docs/design/llm-memo-rewrite/architecture.md`
- `docs/design/llm-memo-rewrite/dataflow.md`
- `docs/tasks/llm-memo-rewrite/TASK-0068.md` (前提タスク)
- `docs/tasks/llm-memo-rewrite/TASK-0064.md` (前提タスク)
- `docs/tasks/llm-memo-rewrite/TASK-0065.md` (参考パターン)

---

## 9. テスト実行確認リスト

### 実装時チェックリスト
- [ ] EditFormState に `isSuggestingTags: Boolean = false` フィールドが追加されている
- [ ] EditScreen.kt にタグ入力欄直下に「タグを提案」ボタンが追加されている
- [ ] ボタンは `viewModel.suggestTags()` を呼び出す onClick ハンドラを持つ
- [ ] `isSuggestingTags` が true 中はボタン内に CircularProgressIndicator が表示される
- [ ] `isSuggestingTags` が true 中はボタンが非活性（enabled=false）になる
- [ ] `stringResource(R.string.button_suggest_tags)` で「タグを提案」ラベルが表示される

### テスト実行確認
```bash
# ユニットテスト実行（EditScreenViewModel.suggestTags() 単体テスト）
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"

# Compose UI テスト実行（「タグを提案」ボタンUI テスト）
mise exec -- ./gradlew connectedAndroidTest --tests "com.den4dr.share2Obsidian.ui.EditScreenTest"

# 全テスト実行（回帰テスト）
mise exec -- ./gradlew test

# ビルド確認
mise exec -- ./gradlew assembleDebug

# Lint チェック
mise exec -- ./gradlew lint
```

### 完了条件（TASK-0069）
- [ ] EditScreenViewModelSuggestTagsTest が全件パス
- [ ] EditScreenTest の「タグを提案」ボタンテストが全件パス
- [ ] 既存テスト（EditScreenViewModelTest 等）が全件パス（回帰テスト）
- [ ] assembleDebug が成功
- [ ] コンパイルエラーなし
- [ ] lint チェック 警告レベル以上なし
- [ ] 完了条件（docs/tasks/llm-memo-rewrite/TASK-0069.md）すべて満たしている

---

**作成者**: Claude Code (tsumiki:tdd-tasknote)
**最終更新**: 2026-07-08
