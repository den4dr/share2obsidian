# TASK-0019: EditScreen Composable 実装 - TDD コンテキストノート

**タスクID**: TASK-0019
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-05-30
**フェーズ**: Phase 2 - UI・統合実装
**前提タスク**: TASK-0017 (EditScreenViewModel 実装完了), TASK-0018 (strings.xml 追加完了)

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **Android Gradle Plugin**: 9.1.0
- **minSdk**: 33 (Android 13)
- **targetSdk/compileSdk**: 36
- **Java互換性**: 11
- **UI フレームワーク**: Jetpack Compose BOM 2024.09.00
  - Material3 コンポーネント（Scaffold, OutlinedTextField, Button, etc.）
  - Compose 標準レイアウト（Column, Row, Box）
  - BackHandler for back button support
- **非同期処理**: Kotlin Coroutines + Compose State
- **ViewModel**: AndroidX ViewModel (`androidx.lifecycle.ViewModel`)
- **依存関係管理**: gradle/libs.versions.toml (Version Catalog)

### テストフレームワーク
- **Compose UI テスト**: `androidx.compose.ui.test`
  - ComposeTestRule: `createComposeRule()`
  - Finders: `onNodeWithText()`, `onNodeWithTag()`, `performClick()`, etc.
- **ユニットテスト**: JUnit 4
- **テストランナー**: Robolectric 4.14.1
- **Androidテスト**: androidx.test 1.6.1
- **テストコンポーネント**:
  - `@RunWith(RobolectricTestRunner::class)` で Android API 利用
  - `@Config(sdk = [34])` で API レベル指定

### ビルドコマンド
```bash
mise exec -- ./gradlew test                      # 全ユニットテスト実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.*"  # UI テスト
mise exec -- ./gradlew connectedAndroidTest      # Compose UI テスト（デバイス/エミュレータ必須）
mise exec -- ./gradlew assembleDebug             # デバッグビルド
```

**参照元**:
- CLAUDE.md - Build Commands, SDK & Language Versions
- gradle/libs.versions.toml
- docs/spec/content-edit-preview/note.md

---

## 2. 開発ルール

### プロジェクト固有ルール
- **シングルアクティビティ アーキテクチャ**: MainActivity のみが存在（REQ-401）
  - EditScreen は Composable 関数として MainActivity.setContent() 内で使用
  - 新規 Activity 作成は禁止
- **既存コンポーネント保護**: FrontmatterBuilder / ObsidianUriBuilder は変更不可（REQ-402）
- **NoteComposer との連携**: TASK-0015 で実装済みの NoteComposer を使用（import: `com.den4dr.share2Obsidian.format.NoteComposer`）
- **EditScreenViewModel との連携**: TASK-0017 で実装済みの EditScreenViewModel を使用（import: `com.den4dr.share2Obsidian.ui.EditScreenViewModel`）
- **日本語ローカライズ**: UI文字列は `res/values/strings.xml` で定義（NFR-103）
- **UI 文字列リソース**: TASK-0018 で追加済みの strings を使用

### Kotlin / Compose コーディング規約
- **Composable 関数規約**: 
  ```kotlin
  @Composable
  fun EditScreen(
      viewModel: EditScreenViewModel,
      onSend: (SendParams) -> Unit,
      onCancel: () -> Unit
  ) { ... }
  ```
- **Material3 コンポーネント**: OutlinedTextField, Button, Scaffold など（既存 LoadingScreen.kt を参照）
- **StateFlow の収集**: Compose の `collectAsState()` で StateFlow を Compose State に変換
  ```kotlin
  val formState by viewModel.formState.collectAsState()
  ```
- **BackHandler の使用**: Android バックボタンをキャンセルと同等に対応（EDGE-102）
  ```kotlin
  BackHandler { onCancel() }
  ```
- **名前空間**:
  - `ui/` パッケージ: Composable 関数・ViewModel
  - `format/` パッケージ: NoteConfig・NoteComposer（TASK-0015で実装済）
  - `content/` パッケージ: ProcessedContent
- **テストコメント**:
  - 【テスト目的】【テスト内容】【期待される動作】の3点セットで記述
  - 信頼性レベル（🔵/🟡/🔴）を明記
  - Arrange-Act-Assert パターンを使用

### ボタン固定表示（NFR-102）の実装パターン
```kotlin
Scaffold(
    bottomBar = {
        // ボタンをここに配置 → 常に画面下部に固定表示
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedButton(...) { }
            Button(...) { }
        }
    }
) { paddingValues ->
    // フィールドはここに配置 → verticalScroll で スクロール可能
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        // フィールド定義
    }
}
```

### 依存関係
- `EditFormState`: TASK-0016 で実装済み（`com.den4dr.share2Obsidian.ui.EditFormState`）
- `SendParams`: TASK-0016 で実装済み（`com.den4dr.share2Obsidian.ui.SendParams`）
- `parseTagsText()`: TASK-0016 で実装済み（`com.den4dr.share2Obsidian.ui.EditFormState.kt`）
- `EditScreenViewModel`: TASK-0017 で実装済み（`com.den4dr.share2Obsidian.ui.EditScreenViewModel`）
- `ProcessedContent`: 既存実装（`com.den4dr.share2Obsidian.content.ProcessedContent`）
- `NoteConfig`: TASK-0015 で実装済み（`com.den4dr.share2Obsidian.format.NoteConfig`）
- `SendParams`: TASK-0016 で実装済み（`com.den4dr.share2Obsidian.ui.SendParams`）

**参照元**:
- docs/spec/content-edit-preview/requirements.md - REQ-401, REQ-402, REQ-003, REQ-004, NFR-101, NFR-102, EDGE-102
- docs/design/content-edit-preview/architecture.md - EditScreen 設計方針
- docs/design/content-edit-preview/dataflow.md - EditScreen フロー

---

## 3. 関連実装

### 既存コンポーネント（参照用）
| クラス | パッケージ | 役割 | ファイルパス |
|--------|----------|------|----------|
| `EditScreenViewModel` | ui | フォーム状態管理（TASK-0017完了） | app/src/main/java/.../ui/EditScreenViewModel.kt |
| `EditFormState` | ui | 編集フォーム状態（TASK-0016完了） | app/src/main/java/.../ui/EditFormState.kt |
| `SendParams` | ui | 送信パラメータ（TASK-0016完了） | app/src/main/java/.../ui/SendParams.kt |
| `parseTagsText()` | ui | タグパース関数（TASK-0016完了） | app/src/main/java/.../ui/EditFormState.kt |
| `ProcessedContent` | content | コンテンツ処理結果 | app/src/main/java/.../content/ProcessedContent.kt |
| `NoteComposer` | format | Frontmatter+URI生成（TASK-0015で実装済） | app/src/main/java/.../format/NoteComposer.kt |
| `NoteConfig` | format | 設定データ（TASK-0015で実装済） | app/src/main/java/.../format/NoteConfig.kt |
| `LoadingScreen` | ui | URL処理中ローディング（参照用） | app/src/main/java/.../ui/LoadingScreen.kt |
| `MainActivity` | root | Activity エントリポイント（統合は TASK-0020） | app/src/main/java/.../MainActivity.kt |

### UI テスト参考実装
| テストファイル | テスト対象 | ファイルパス |
|----------|----------|----------|
| `LoadingScreen` | Composable UI デザイン | app/src/main/java/.../ui/LoadingScreen.kt |
| `EditScreenViewModelTest` | ViewModel ロジック（TASK-0017） | app/src/test/java/.../ui/EditScreenViewModelTest.kt |
| `NoteComposerTest` | NoteComposer ロジック | app/src/test/java/.../format/NoteComposerTest.kt |

### 参考パターン
- **Composable UI**: LoadingScreen.kt 参照（Scaffold・Material3コンポーネント実装例）
- **BackHandler 実装**: Android Compose 公式ドキュメント参照
- **StateFlow + Compose**: collectAsState() を使用した状態管理パターン

**参照元**:
- app/src/main/java/com/den4dr/share2Obsidian/ui/LoadingScreen.kt
- app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt
- docs/design/content-edit-preview/architecture.md - EditScreen 設計

---

## 4. 設計文書

### 要件定義
- **統合要件**: docs/spec/content-edit-preview/requirements.md
  - REQ-003: 4フィールド（タイトル・本文・タグ・フォルダ）定義
  - REQ-004: 送信・キャンセルボタン定義
  - REQ-101: 送信時に EditScreen から NoteComposer 呼び出し
  - REQ-103: タグフィールドのカンマ区切りパース
  - REQ-201: キャンセル動作（finish() のみ）
  - REQ-401/402: アーキテクチャ制約
  - NFR-101: フィールドレイアウト（タイトル単一行、本文複数行）
  - NFR-102: ボタン固定表示（スクロール非対応）
  - EDGE-102: **バックボタン対応（キャンセルと同等）**

### アーキテクチャ・データフロー
- **アーキテクチャ**: docs/design/content-edit-preview/architecture.md
  - EditScreen コンポーネント
  - EditScreenViewModel との連携
  - MainActivity フロー変更（TASK-0020）

- **データフロー**: docs/design/content-edit-preview/dataflow.md
  - フロー1: テキスト共有時 - EditScreen表示
  - フロー3: 送信ボタンタップ時 - NoteComposer呼び出し
  - フロー4: キャンセルボタンタップ時
  - EditFormState の状態遷移図

### 型定義・インターフェース
- **EditScreen Composable** (実装対象):
  ```kotlin
  @Composable
  fun EditScreen(
      viewModel: EditScreenViewModel,
      onSend: (SendParams) -> Unit,
      onCancel: () -> Unit
  )
  ```

- **EditScreenViewModel** (TASK-0017で実装):
  ```kotlin
  class EditScreenViewModel : ViewModel() {
      val formState: StateFlow<EditFormState>
      fun initialize(processed: ProcessedContent, config: NoteConfig)
      fun updateTitle(title: String)
      fun updateBody(body: String)
      fun updateTagsText(tagsText: String)
      fun updateFolder(folder: String)
      fun buildSendParams(config: NoteConfig): SendParams
  }
  ```

- **EditFormState** (TASK-0016で実装):
  ```kotlin
  data class EditFormState(
      val title: String,           // タイトルフィールド
      val body: String,            // 本文フィールド
      val tagsText: String,        // タグフィールド（カンマ区切り）
      val folder: String           // フォルダフィールド
  )
  ```

- **SendParams** (TASK-0016で実装):
  ```kotlin
  data class SendParams(
      val title: String?,          // null = タイトルなし（EDGE-001）
      val body: String,            // 空文字許容（EDGE-002）
      val tags: List<String>,      // 空リスト許容（EDGE-003）
      val config: NoteConfig       // vault・folder を含む
  )
  ```

**参照元**:
- docs/spec/content-edit-preview/requirements.md
- docs/design/content-edit-preview/architecture.md
- docs/design/content-edit-preview/dataflow.md
- docs/design/content-edit-preview/interfaces.kt (型定義)

---

## 5. テスト関連情報

### テストフレームワーク・設定
- **Compose UI テスト**: `androidx.compose.ui.test.junit4.createComposeRule()`
  - `ComposeTestRule` を使用
  - メイン スレッドで Composable を描画してテスト
- **テスト実行環境**: 
  - デバイス/エミュレータが必須（`connectedAndroidTest` で実行）
  - または Robolectric で Android API をシミュレート

### テストディレクトリ構成
```
app/src/androidTest/java/com/den4dr/share2Obsidian/
└── ui/
    └── EditScreenTest.kt          ← TASK-0019 の新規テスト

app/src/test/java/com/den4dr/share2Obsidian/
├── ui/
│   ├── EditScreenViewModelTest.kt ← TASK-0017
│   ├── EditFormStateTest.kt       ← TASK-0016
│   ├── SendParamsTest.kt          ← TASK-0016
│   └── ParseTagsTextTest.kt       ← TASK-0016
├── format/
│   ├── NoteComposerTest.kt        ← 参照（テスト構造）
│   └── ...
└── MainActivityTest.kt
```

### テストケース要件（TASK-0019.md より）
- **TC-003-01**: フィールド初期値表示テスト（title）
- **TC-003-02**: フィールド初期値表示テスト（body）
- **TC-003-03**: フィールド初期値表示テスト（tagsText）
- **TC-003-04**: フィールド初期値表示テスト（folder）
- **TC-101-01**: 送信ボタンで onSend が呼ばれる
- **TC-201-01**: キャンセルボタンで onCancel が呼ばれる
- **TC-EDGE-102-01**: バックボタン（BackHandler）で onCancel が呼ばれる
- **TC-NFR-102-01**: ボタンが画面下部に固定表示される

### Compose UI テストパターン（参考用）
```kotlin
@RunWith(AndroidJUnit4::class)
class EditScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `TC-003-01 フィールド初期値表示テスト`() {
        // 【テスト目的】: EditScreen がフォーム初期値を正しく表示すること
        // 【テスト内容】: viewModel を初期化して EditScreen を表示
        // 【期待される動作】: title フィールドにタイトルが表示される
        // 🔵 信頼性レベル: TC-003-01より

        // Arrange
        val viewModel = EditScreenViewModel()
        val processed = ProcessedContent(body = "本文", title = "テスト", contentType = ContentKind.TEXT)
        val config = NoteConfig.fromAppConfig()
        viewModel.initialize(processed, config)

        composeTestRule.setContent {
            EditScreen(viewModel, onSend = {}, onCancel = {})
        }

        // Act
        composeTestRule.onNodeWithText("テスト").assertIsDisplayed()

        // Assert
        // （onNodeWithText の assertIsDisplayed() で assertion 完了）
    }

    @Test
    fun `TC-101-01 送信ボタンで onSend が呼ばれる`() {
        // 【テスト目的】: 送信ボタンタップで onSend コールバックが呼ばれること
        // 【テスト内容】: EditScreen を表示して送信ボタンをタップ
        // 【期待される動作】: onSend が呼ばれる
        // 🔵 信頼性レベル: TC-101-01より

        val onSendCalled = mutableListOf<SendParams>()
        val viewModel = EditScreenViewModel()
        viewModel.initialize(ProcessedContent(body = "本文", title = "テスト", contentType = ContentKind.TEXT), NoteConfig.fromAppConfig())

        composeTestRule.setContent {
            EditScreen(
                viewModel,
                onSend = { params -> onSendCalled.add(params) },
                onCancel = {}
            )
        }

        composeTestRule.onNodeWithText(stringResource(R.string.button_send)).performClick()
        
        assertEquals(1, onSendCalled.size)
    }
}
```

### テスト実行コマンド
```bash
# Compose UI テスト実行（デバイス/エミュレータ必須）
mise exec -- ./gradlew connectedAndroidTest --tests "com.den4dr.share2Obsidian.ui.EditScreenTest"

# ユニットテスト実行（ViewModel テスト）
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"

# UI テストすべて実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.*"

# 全テスト実行（回帰テスト）
mise exec -- ./gradlew test

# ビルド確認
mise exec -- ./gradlew assembleDebug
```

**参照元**:
- gradle/libs.versions.toml - androidx-compose-ui-test
- docs/spec/content-edit-preview/acceptance-criteria.md - TC-003-01〜04, TC-101-01, TC-201-01, TC-EDGE-102-01, TC-NFR-102-01
- docs/tasks/content-edit-preview/TASK-0019.md

---

## 6. 注意事項

### 技術的制約

#### EditScreen Composable の実装パターン
```kotlin
@Composable
fun EditScreen(
    viewModel: EditScreenViewModel,
    onSend: (SendParams) -> Unit,
    onCancel: () -> Unit
) {
    // formState を StateFlow から Compose State に変換
    val formState by viewModel.formState.collectAsState()

    // バックボタン対応（EDGE-102）
    BackHandler { onCancel() }

    Scaffold(
        bottomBar = {
            // ボタンを画面下部に固定表示（NFR-102）
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.button_cancel))
                }
                Button(onClick = {
                    onSend(viewModel.buildSendParams(config))
                }) {
                    Text(stringResource(R.string.button_send))
                }
            }
        }
    ) { paddingValues ->
        // フィールドがスクロール可能（NFR-102）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // フィールド定義（タイトル・本文・タグ・フォルダ）
        }
    }
}
```

#### フィールドレイアウト（NFR-101）
- **タイトル**: `OutlinedTextField` + `singleLine = true`（単一行入力）
- **本文**: `OutlinedTextField` + `minLines = 5`（複数行入力）
- **タグ**: `OutlinedTextField` + `singleLine = true`（カンマ区切り）
- **フォルダ**: `OutlinedTextField` + `singleLine = true`

#### ボタン配置（NFR-102）
```kotlin
// Scaffold.bottomBar に配置 → 画面下部に固定表示
Scaffold(
    bottomBar = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.button_cancel))
            }
            Button(
                onClick = { onSend(viewModel.buildSendParams(config)) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.button_send))
            }
        }
    }
) { ... }
```

#### BackHandler の使用（EDGE-102）
```kotlin
BackHandler { onCancel() }
// Android のバックボタンをキャンセルと同等に対応
```

#### 注意: NoteConfig の受け渡し
- `NoteConfig` は `EditScreen` の引数で受け取るか、ViewModel 内に保持する
- 現在の実装では `buildSendParams(config)` で config をメソッド引数として受け取っているため、
  EditScreen から onSend コールバック時に config を渡す必要がある
- または MainActivity 内で config を保持して EditScreen/ViewModel に渡す設計にする

### セキュリティ・パフォーマンス

#### セキュリティ
- **URI エンコーディング**: `NoteComposer.buildUri()` で自動処理（既存実装継承）
- **入力検証**: 本文・タイトル空許容（EDGE-002/001）、タグパース時の空文字列フィルタリング（TASK-0016 実装済）

#### パフォーマンス
- **初期表示時間**: テキスト・HTML・ファイルは < 100ms（NFR-001）
- **Recomposition 最適化**: `remember { }` や `derivedStateOf { }` で不要な再描画を防止
- **StateFlow のメモリ管理**: ViewModel スコープ（Activity 再作成時に保持される）

### 画面回転対応（EDGE-101）
- **ViewModel の自動保持**: ViewModel は Activity 再作成時に保存される（AndroidX ViewModel 機能）
- **EditScreenViewModel.initialize() の重複防止**: TASK-0017 で実装済みの `initialized` フラグで2回目以降の呼び出しを無視
- **FormState の保持**: StateFlow は ViewModel 内なため、自動的に保持される
- **EditScreen は状態を持たない**: UI 層は ViewModel から状態を取得するのみ

### 関連ドキュメント

#### 要件定義
- docs/spec/content-edit-preview/requirements.md (REQ-001〜REQ-405, NFR-001〜NFR-201, EDGE-001〜EDGE-102)
- docs/spec/content-edit-preview/user-stories.md
- docs/spec/content-edit-preview/acceptance-criteria.md

#### 設計文書
- docs/design/content-edit-preview/architecture.md
- docs/design/content-edit-preview/dataflow.md
- docs/design/content-edit-preview/design-interview.md
- docs/design/content-edit-preview/interfaces.kt

#### 実装タスク
- docs/implements/content-edit-preview/TASK-0015/ (NoteConfig + NoteComposer 実装済)
- docs/implements/content-edit-preview/TASK-0016/ (EditFormState + SendParams 実装済)
- docs/implements/content-edit-preview/TASK-0017/ (EditScreenViewModel 実装済)
- docs/tasks/content-edit-preview/TASK-0019.md (本タスク定義)

**参照元**:
- docs/spec/content-edit-preview/requirements.md - REQ-401, REQ-402, REQ-003, REQ-004, REQ-101, REQ-103, REQ-201, NFR-101/102/103, EDGE-001/002/003/101/102
- docs/design/content-edit-preview/architecture.md - EditScreen設計
- docs/design/content-edit-preview/dataflow.md - フロー1/3/4、EditFormState状態遷移
- CLAUDE.md - Build Commands, Localization Note

---

## 7. 依存関係・ブロッキング

### 前提条件（ブロック解除待ち）
- ✅ TASK-0016: EditFormState + parseTagsText + SendParams 実装完了
- ✅ TASK-0017: EditScreenViewModel 実装完了
- ✅ TASK-0018: strings.xml UI文字列リソース追加完了

### ブロック対象（本タスク完了後に実行可能）
- TASK-0020: MainActivity フロー統合（EditScreen表示への切り替え）

### 実装順序
1. **EditScreen Composable 定義** (ファイル作成、@Composable 関数宣言)
2. **BackHandler 実装** (バックボタン対応)
3. **Scaffold + レイアウト実装** (ボタン固定表示 + フィールドスクロール)
4. **フィールド実装** (タイトル・本文・タグ・フォルダ)
5. **コールバック実装** (onSend・onCancel)
6. **UI テスト実装** (EditScreenTest.kt - androidTest)
7. **ビルド確認** (assembleDebug)

---

## 8. ファイルパス一覧

### 実装対象
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt` (新規 Composable)

### テスト対象
- `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt` (新規 UI テスト)

### 参照ファイル（TASK-0017で実装済）
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt`

### 参照ファイル（TASK-0016で実装済）
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` (EditFormState, parseTagsText)
- `app/src/main/java/com/den4dr/share2Obsidian/ui/SendParams.kt` (SendParams)
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditFormStateTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/ui/ParseTagsTextTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/ui/SendParamsTest.kt`

### 参照ファイル（既存実装）
- `app/src/main/java/com/den4dr/share2Obsidian/content/ProcessedContent.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/format/NoteConfig.kt` (TASK-0015)
- `app/src/main/java/com/den4dr/share2Obsidian/format/NoteComposer.kt` (TASK-0015)
- `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` (フロー変更は TASK-0020)
- `app/src/main/res/values/strings.xml` (TASK-0018で更新済)

### 参照テスト
- `app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/ui/LoadingScreen.kt` (Composable UI 参考)

### 設計・要件ドキュメント
- `docs/spec/content-edit-preview/requirements.md`
- `docs/spec/content-edit-preview/acceptance-criteria.md`
- `docs/design/content-edit-preview/architecture.md`
- `docs/design/content-edit-preview/dataflow.md`
- `docs/design/content-edit-preview/interfaces.kt`
- `docs/tasks/content-edit-preview/TASK-0019.md`

---

## 9. テスト実行確認リスト

### 実装時チェックリスト
- [ ] EditScreen.kt が ui/ パッケージに作成されている
- [ ] @Composable fun EditScreen が定義されている
- [ ] EditScreenViewModel, onSend, onCancel をパラメータで受け取っている
- [ ] BackHandler { onCancel() } が実装されている
- [ ] Scaffold.bottomBar に送信・キャンセルボタンがある
- [ ] ボタンが Modifier.weight(1f) で均等配置されている
- [ ] Column が verticalScroll(rememberScrollState()) でスクロール可能
- [ ] OutlinedTextField で 4フィールドが表示されている
- [ ] stringResource(R.string.label_*) で UI 文字列を取得している
- [ ] stringResource(R.string.button_*) でボタンテキストを取得している
- [ ] formState by viewModel.formState.collectAsState() で状態取得している
- [ ] onSend で viewModel.buildSendParams() を呼び出している
- [ ] すべてのインポートが正しい（androidx.compose.*, BackHandler等）

### テスト実行確認
```bash
# Compose UI テスト実行（エミュレータ/デバイス必須）
mise exec -- ./gradlew connectedAndroidTest --tests "com.den4dr.share2Obsidian.ui.EditScreenTest"

# ViewModel ユニットテスト実行（回帰テスト）
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"

# UI テスト実行（すべて）
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.*"

# 全テスト実行（回帰テスト）
mise exec -- ./gradlew test

# ビルド確認
mise exec -- ./gradlew assembleDebug
```

### 完了条件（TASK-0019）
- [ ] EditScreenTest が全件パス（テストケース8件）
- [ ] EditScreenViewModelTest が全件パス（回帰テスト）
- [ ] ParseTagsTextTest が全件パス（回帰テスト）
- [ ] EditFormStateTest が全件パス（回帰テスト）
- [ ] SendParamsTest が全件パス（回帰テスト）
- [ ] NoteComposerTest が全件パス（回帰テスト）
- [ ] assembleDebug が成功
- [ ] コンパイルエラーなし

---

## 10. 実装手順（TDDフロー）

### Phase 1: 要件整理
1. `docs/tasks/content-edit-preview/TASK-0019.md` を確認
2. 関連するEARS要件を確認（REQ-003, REQ-004, REQ-101, NFR-101, NFR-102, EDGE-102）
3. テストケースを整理（TC-003-01〜04, TC-101-01, TC-201-01, TC-EDGE-102-01, TC-NFR-102-01）

### Phase 2: テストケース実装（Red フェーズ）
1. EditScreenTest.kt を作成（androidTest）
2. 8つのテストケースを定義（失敗を期待）
3. コンパイルエラーを確認

### Phase 3: 最小実装（Green フェーズ）
1. EditScreen.kt を作成
2. @Composable fun EditScreen(viewModel, onSend, onCancel) 実装
3. BackHandler 実装
4. Scaffold + bottomBar 実装
5. フィールド表示実装
6. コールバック実装
7. すべてのテストがパスすることを確認

### Phase 4: リファクタリング（Refactor フェーズ）
1. KDoc コメント追加
2. コード形式の統一（trailing comma など）
3. テストコメントの充実
4. すべてのテストが継続パスすることを確認

### Phase 5: 品質検証（Verify フェーズ）
1. 全テストの実行確認（100% パス）
2. コンパイルエラーなし
3. assembleDebug 成功確認
4. コード品質のレビュー

---

**作成者**: Claude Code (tsumiki:tdd-tasknote)
**最終更新**: 2026-05-30
