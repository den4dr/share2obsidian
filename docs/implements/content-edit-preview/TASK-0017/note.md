# TASK-0017: EditScreenViewModel 実装 - TDD コンテキストノート

**タスクID**: TASK-0017
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-04-01
**フェーズ**: Phase 2 - UI・統合実装
**前提タスク**: TASK-0016 (EditFormState + parseTagsText + SendParams 実装完了)

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **Android Gradle Plugin**: 9.1.0
- **minSdk**: 33 (Android 13)
- **targetSdk/compileSdk**: 36
- **Java互換性**: 11
- **ViewModel フレームワーク**: AndroidX Lifecycle ViewModel
  - `StateFlow<T>` で状態管理
  - `asStateFlow()` で MutableStateFlow をイミュータブルに公開
- **非同期処理**: Kotlin Coroutines
  - `kotlinx.coroutines.flow.StateFlow`
  - `kotlinx.coroutines.flow.MutableStateFlow`
- **依存関係管理**: gradle/libs.versions.toml (Version Catalog)

### テストフレームワーク
- **ユニットテスト**: JUnit 4
- **テストランナー**: Robolectric 4.14.1
- **Androidテスト**: androidx.test 1.6.1
- **テストコンポーネント**:
  - `@RunWith(RobolectricTestRunner::class)` で Android API 利用
  - `@Config(sdk = [34])` で API レベル指定
  - Kotlin Coroutines test utilities（runBlockingTest, runTest等）

### ビルドコマンド
```bash
mise exec -- ./gradlew test                      # 全ユニットテスト実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.*"  # UI テスト
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"  # TASK-0017テスト
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
  - EditScreenViewModel は UI 層の状態管理
  - Activity 再作成時に状態が保持される（EDGE-101: 画面回転対応）
- **既存コンポーネント保護**: FrontmatterBuilder / ObsidianUriBuilder は変更不可（REQ-402）
- **NoteConfig 依存**: TASK-0015 で実装済みの NoteConfig を使用（import: `com.den4dr.share2Obsidian.format.NoteConfig`）
- **日本語ローカライズ**: エラーメッセージ・UI文字列は `res/values/strings.xml` で定義（NFR-103）

### Kotlin / ViewModel コーディング規約
- **StateFlow の使用**: ViewModel 内で `MutableStateFlow<State>` で状態管理、公開時は `asStateFlow()` でイミュータブルに変換
  ```kotlin
  private val _formState = MutableStateFlow(初期値)
  val formState: StateFlow<EditFormState> = _formState.asStateFlow()
  ```
- **重複実行防止フラグ**: `initialize()` メソッドに `initialized` フラグを実装（EDGE-101: 画面回転対応）
  ```kotlin
  private var initialized = false
  fun initialize(processed: ProcessedContent, config: NoteConfig) {
      if (initialized) return
      initialized = true
      _formState.value = ...
  }
  ```
- **状態更新**: `copy()` を使用したイミュータブル更新
  ```kotlin
  _formState.value = _formState.value.copy(title = newTitle)
  ```
- **名前空間**:
  - `ui/` パッケージ: ViewModel・Composable 関数
  - `format/` パッケージ: NoteConfig・NoteComposer（TASK-0015で実装済）
  - `content/` パッケージ: ProcessedContent
- **テストコメント**:
  - 【テスト目的】【テスト内容】【期待される動作】の3点セットで記述
  - 信頼性レベル（🔵/🟡/🔴）を明記
  - Arrange-Act-Assert パターンを使用

### 依存関係
- `EditFormState`: TASK-0016 で実装済み（`com.den4dr.share2Obsidian.ui.EditFormState`）
- `SendParams`: TASK-0016 で実装済み（`com.den4dr.share2Obsidian.ui.SendParams`）
- `parseTagsText()`: TASK-0016 で実装済み（`com.den4dr.share2Obsidian.ui.parseTagsText`）
- `ProcessedContent`: 既存実装（`com.den4dr.share2Obsidian.content.ProcessedContent`）
- `NoteConfig`: TASK-0015 で実装済み（`com.den4dr.share2Obsidian.format.NoteConfig`）

**参照元**:
- docs/spec/content-edit-preview/requirements.md - REQ-401, REQ-402, REQ-003, REQ-101, REQ-103
- docs/design/content-edit-preview/architecture.md - ViewModel設計方針
- docs/design/content-edit-preview/dataflow.md - EditScreenViewModel フロー

---

## 3. 関連実装

### 既存コンポーネント（参照用）
| クラス | パッケージ | 役割 | ファイルパス |
|--------|----------|------|----------|
| `ProcessedContent` | content | コンテンツ処理結果 | app/src/main/java/.../content/ProcessedContent.kt |
| `EditFormState` | ui | 編集フォーム状態（TASK-0016完了） | app/src/main/java/.../ui/EditFormState.kt |
| `SendParams` | ui | 送信パラメータ（TASK-0016完了） | app/src/main/java/.../ui/SendParams.kt |
| `parseTagsText()` | ui | タグパース関数（TASK-0016完了） | app/src/main/java/.../ui/EditFormState.kt |
| `NoteConfig` | format | 設定データ（TASK-0015で実装済） | app/src/main/java/.../format/NoteConfig.kt |
| `NoteComposer` | format | Frontmatter+URI生成（TASK-0015で実装済） | app/src/main/java/.../format/NoteComposer.kt |
| `MainActivity` | root | Activity エントリポイント（統合は TASK-0018） | app/src/main/java/.../MainActivity.kt |

### テスト参考実装
| テストファイル | テスト対象 | ファイルパス |
|----------|----------|----------|
| `ParseTagsTextTest` | parseTagsText() | app/src/test/java/.../ui/ParseTagsTextTest.kt |
| `EditFormStateTest` | EditFormState | app/src/test/java/.../ui/EditFormStateTest.kt |
| `SendParamsTest` | SendParams | app/src/test/java/.../ui/SendParamsTest.kt |
| `NoteComposerTest` | NoteComposer | app/src/test/java/.../format/NoteComposerTest.kt |
| `MainActivityTest` | MainActivity 統合 | app/src/test/java/.../MainActivityTest.kt |

### 参考パターン
- **ViewModel 設計**: NoteComposerTest.kt 参照（単体テスト構造）
- **テストコメント**: NoteComposerTest.kt 参照（テスト3点セット【目的】【内容】【期待結果】）
- **StateFlow テスト**: Kotlin Coroutines test utilities（runTest + collect）

**参照元**:
- app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt
- app/src/test/java/com/den4dr/share2Obsidian/ui/ParseTagsTextTest.kt
- docs/design/content-edit-preview/architecture.md - コンポーネント構成

---

## 4. 設計文書

### 要件定義
- **統合要件**: docs/spec/content-edit-preview/requirements.md
  - REQ-001: 全コンテンツで編集画面表示
  - REQ-003: 編集フィールド定義（title, body, tagsText, folder）
  - REQ-101: 送信時に編集後の値から URI 構築
  - REQ-103: タグフィールドのカンマ区切りパース
  - REQ-201: キャンセル動作
  - REQ-301/302: URL処理フロー
  - REQ-401/402: アーキテクチャ制約
  - REQ-405: フォルダ初期値は AppConfig から
  - NFR-001: 初期表示 100ms 以内
  - EDGE-001: タイトル空で送信
  - EDGE-002: 本文空で送信
  - EDGE-003: タグ空で送信
  - EDGE-101: **画面回転後も状態が保持される**

### アーキテクチャ・データフロー
- **アーキテクチャ**: docs/design/content-edit-preview/architecture.md
  - EditScreenViewModel コンポーネント
  - ProcessedContent + NoteConfig → EditFormState 初期化
  - StateFlow による状態管理

- **データフロー**: docs/design/content-edit-preview/dataflow.md
  - フロー1: テキスト共有時 - ProcessedContent から EditFormState へ
  - フロー3: 送信ボタンタップ時 - parseTagsText() + NoteComposer呼び出し
  - EditFormState 状態遷移図
  - タグパース仕様（REQ-103）

### 型定義・インターフェース
- **EditScreenViewModel** (実装対象):
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
      val title: String,           // 初期値: ProcessedContent.title ?: ""
      val body: String,            // 初期値: ProcessedContent.body
      val tagsText: String,        // 初期値: config.defaultTags.joinToString(", ")
      val folder: String           // 初期値: config.folder
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
- **テストランナー**: Robolectric 4.14.1
  - Android API を JVM 上でシミュレート
  - Intent / Uri / Context のテストに必須
- **テスト設定**:
  ```kotlin
  @RunWith(RobolectricTestRunner::class)
  @Config(sdk = [34])  // API 34 でシミュレート
  class EditScreenViewModelTest { ... }
  ```

### テストディレクトリ構成
```
app/src/test/java/com/den4dr/share2Obsidian/
├── format/
│   ├── NoteComposerTest.kt        ← 参照（テスト構造）
│   └── ...
├── content/
│   ├── TextContentProcessorTest.kt
│   └── ...
├── ui/
│   ├── ParseTagsTextTest.kt       ← TASK-0016で実装済み
│   ├── EditFormStateTest.kt       ← TASK-0016で実装済み
│   ├── SendParamsTest.kt          ← TASK-0016で実装済み
│   └── EditScreenViewModelTest.kt ← TASK-0017の新規テスト
└── MainActivityTest.kt
```

### テストケース要件（TASK-0017.md より）
- **TC-001**: initialize() で初期値がセットされる（REQ-003）
- **TC-002**: initialize() は2回目以降無視される（EDGE-101）
- **TC-003**: title が null の場合は空文字で初期化（TC-003-02）
- **TC-004**: updateTitle() でタイトルが変更される（REQ-003）
- **TC-005**: buildSendParams() でタグがパースされる（REQ-103）
- **TC-006**: buildSendParams() で空タイトルが null になる（EDGE-001）
- **TC-007**: buildSendParams() でスペースのみタイトルが null になる（EDGE-001）
- **StateFlow テスト**: StateFlow の変更が正しく伝播する（EDGE-101）

### 既存テストパターン（参考用）
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditScreenViewModelTest {

    @Test
    fun `TC-001 説明`() {
        // 【テスト目的】: xxx が正しく動作すること
        // 【テスト内容】: 典型的な使用例で検証
        // 【期待される動作】: yyy が zz になること
        // 🔵 信頼性レベル: REQ-xxx より

        // Arrange
        val viewModel = EditScreenViewModel()
        val processed = ProcessedContent(body="本文", title="タイトル")
        val config = NoteConfig.fromAppConfig()

        // Act
        viewModel.initialize(processed, config)

        // Assert
        assertEquals("タイトル", viewModel.formState.value.title)
    }
}
```

### テスト実行コマンド
```bash
# 全テスト実行
mise exec -- ./gradlew test

# UI テストのみ実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.*"

# EditScreenViewModelTest のみ
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"

# テスト出力確認
mise exec -- ./gradlew test --info
```

### StateFlow テストパターン
```kotlin
// Kotlin Coroutines test utilities を使用
@Test
fun testStateFlowChanges() = runTest {
    val viewModel = EditScreenViewModel()
    val states = mutableListOf<EditFormState>()

    // StateFlow の値を collect
    val job = launch {
        viewModel.formState.collect { states.add(it) }
    }

    // 状態を変更
    viewModel.updateTitle("新タイトル")
    advanceUntilIdle()

    // 初期値 + 変更後の値が captured される
    assertEquals(2, states.size)
    assertEquals("新タイトル", states[1].title)

    job.cancel()
}
```

**参照元**:
- gradle/libs.versions.toml - robolectric, junit, androidx-test-core
- app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt
- docs/spec/content-edit-preview/acceptance-criteria.md
- docs/tasks/content-edit-preview/TASK-0017.md

---

## 6. 注意事項

### 技術的制約

#### EditScreenViewModel の状態管理
- **重複実行防止**: `initialize()` は `initialized` フラグで1回のみ実行（EDGE-101: 画面回転対応）
  ```kotlin
  private var initialized = false
  fun initialize(processed: ProcessedContent, config: NoteConfig) {
      if (initialized) return  // 画面回転時に重複初期化を防止
      initialized = true
      _formState.value = EditFormState(
          title = processed.title ?: "",
          body = processed.body,
          tagsText = config.defaultTags.joinToString(", "),
          folder = config.folder
      )
  }
  ```
- **StateFlow のメモリ管理**: ViewModel スコープ（Activity 再作成時に保持される）
- **状態更新は `copy()` で**: `_formState.value = _formState.value.copy(...)`

#### buildSendParams() の実装
```kotlin
fun buildSendParams(config: NoteConfig): SendParams {
    val state = _formState.value
    return SendParams(
        title = state.title.ifBlank { null },  // 空文字 → null（EDGE-001）
        body = state.body,
        tags = parseTagsText(state.tagsText),  // タグパース（REQ-103）
        config = config
    )
}
```
- **ifBlank { null }**: スペースのみのタイトルも null に変換（EDGE-001）
- **parseTagsText()**: TASK-0016で実装済み関数を再利用
- **config 引数**: ViewModel 内に保持してもよいが、メソッド引数として受け取る設計

#### ProcessedContent から EditFormState への変換
```kotlin
title = processed.title ?: ""          // null → 空文字
body = processed.body                  // そのまま使用
tagsText = config.defaultTags.joinToString(", ")  // List → カンマ区切り文字列
folder = config.folder                // 初期値として使用
```

### セキュリティ・パフォーマンス

#### セキュリティ
- **入力検証**: 本文・タイトル空許容（EDGE-002/001）、タグパース時の空文字列フィルタリング
- **URI エンコーディング**: `NoteComposer.buildUri()` で自動処理（既存実装継承）

#### パフォーマンス
- **初期表示時間**: テキスト・HTML・ファイルは < 100ms（NFR-001）
- **State 変更**: `MutableStateFlow` での状態変更は O(1)
- **initialize() 実行**: 初回のみ（`initialized` フラグで防止）

### 画面回転対応（EDGE-101）
- **ViewModel の自動保持**: ViewModel は Activity 再作成時に保存される（AndroidX ViewModel 機能）
- **initialize() の重複防止**: `initialized` フラグで2回目以降の呼び出しを無視
- **FormState の保持**: StateFlow は ViewModel 内なため、自動的に保持される
- **テスト時の確認**: Activity 再作成シミュレーション + 状態確認（MainActivityTest.kt 参照）

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
- docs/tasks/content-edit-preview/TASK-0017.md (本タスク定義)

**参照元**:
- docs/spec/content-edit-preview/requirements.md - REQ-401, REQ-402, REQ-001, REQ-003, REQ-101, REQ-103, NFR-001, EDGE-001/002/003/101
- docs/design/content-edit-preview/architecture.md - ViewModel設計
- docs/design/content-edit-preview/dataflow.md - フロー1/3、状態遷移
- CLAUDE.md - Build Commands, Localization Note

---

## 7. 依存関係・ブロッキング

### 前提条件（ブロック解除待ち）
- ✅ TASK-0016: EditFormState + parseTagsText + SendParams 実装完了

### ブロック対象（本タスク完了後に実行可能）
- TASK-0018: EditScreen Composable 実装（EditScreenViewModel 使用）
- TASK-0019: MainActivity フロー統合（EditScreen表示への切り替え）
- TASK-0020: E2E テスト（UI テスト）

### 実装順序
1. **EditScreenViewModel 定義** (ファイル作成、プロパティ/メソッド宣言)
2. **初期化ロジック実装** (initialize() メソッド)
3. **更新メソッド実装** (updateTitle, updateBody, updateTagsText, updateFolder)
4. **送信パラメータ生成** (buildSendParams() メソッド)
5. **ユニットテスト実装** (EditScreenViewModelTest.kt)

---

## 8. ファイルパス一覧

### 実装対象
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` (新規 ViewModel)

### テスト対象
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt` (新規テスト)

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
- `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` (フロー変更は TASK-0018)

### 参照テスト
- `app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/MainActivityTest.kt`

### 設計・要件ドキュメント
- `docs/spec/content-edit-preview/requirements.md`
- `docs/spec/content-edit-preview/acceptance-criteria.md`
- `docs/design/content-edit-preview/architecture.md`
- `docs/design/content-edit-preview/dataflow.md`
- `docs/design/content-edit-preview/interfaces.kt`
- `docs/tasks/content-edit-preview/TASK-0017.md`

---

## 9. テスト実行確認リスト

### 実装時チェックリスト
- [ ] EditScreenViewModel クラスが定義されている
- [ ] StateFlow<EditFormState> が private val _formState で実装されている
- [ ] formState: StateFlow<EditFormState> が public で公開されている
- [ ] initialize() が重複実行を防ぐ initialized フラグを持つ
- [ ] updateTitle(), updateBody(), updateTagsText(), updateFolder() が実装されている
- [ ] buildSendParams(config) が title の null 変換と parseTagsText() を行う
- [ ] すべてのインポートが正しい（EditFormState, SendParams, ProcessedContent, NoteConfig等）

### テスト実行確認
```bash
# ユニットテスト実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"

# UI テスト実行（EditFormState, SendParams, EditScreenViewModel）
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.*"

# 全テスト実行（回帰テスト）
mise exec -- ./gradlew test

# ビルド確認
mise exec -- ./gradlew assembleDebug
```

### 完了条件（TASK-0017）
- [ ] EditScreenViewModelTest が全件パス（テストケース7件 + StateFlow テスト）
- [ ] ParseTagsTextTest が全件パス（回帰テスト）
- [ ] EditFormStateTest が全件パス（回帰テスト）
- [ ] SendParamsTest が全件パス（回帰テスト）
- [ ] NoteComposerTest が全件パス（回帰テスト）
- [ ] assembleDebug が成功
- [ ] コンパイルエラーなし

---

## 10. 実装手順（TDDフロー）

### Phase 1: 要件整理
1. `docs/tasks/content-edit-preview/TASK-0017.md` を確認
2. 関連するEARS要件を確認（REQ-003, REQ-101, REQ-103, EDGE-101）
3. テストケースを整理（TC-001〜007 + StateFlow テスト）

### Phase 2: テストケース実装（Red フェーズ）
1. EditScreenViewModelTest.kt を作成
2. 7つのテストケースを定義（失敗を期待）
3. StateFlow テストケースを追加
4. コンパイルエラーを確認

### Phase 3: 最小実装（Green フェーズ）
1. EditScreenViewModel.kt を作成
2. StateFlow + initialize() メソッド実装
3. update メソッド実装（4つ）
4. buildSendParams() メソッド実装
5. すべてのテストがパスすることを確認

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
**最終更新**: 2026-04-01
