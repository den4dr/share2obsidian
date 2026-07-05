# TASK-0016: EditScreen + EditScreenViewModel 実装 - TDD コンテキストノート

**タスクID**: TASK-0016
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: Phase 2 - UI実装
**前提タスク**: TASK-0015 (NoteConfig + NoteComposer 実装完了)

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
  - State management: `StateFlow<T>` + `viewModels()` delegate
- **非同期処理**: Kotlin Coroutines + `lifecycleScope`
- **ViewModel**: AndroidX ViewModel (`androidx.lifecycle.ViewModel`)
- **依存関係管理**: gradle/libs.versions.toml (Version Catalog)

### テストフレームワーク
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
mise exec -- ./gradlew assembleDebug             # デバッグビルド
```

**参照元**:
- CLAUDE.md - Build Commands
- gradle/libs.versions.toml
- docs/spec/content-edit-preview/note.md

---

## 2. 開発ルール

### プロジェクト固有ルール
- **シングルアクティビティ アーキテクチャ**: MainActivity のみが存在（REQ-401）
  - UI変更は `setContent { }` 内で Compose を使用
  - 新規 Activity 作成は禁止
- **既存コンポーネント保護**: FrontmatterBuilder / ObsidianUriBuilder は変更不可（REQ-402）
- **日本語ローカライズ**: エラーメッセージ・UI文字列は `res/values/strings.xml` で定義（NFR-103）
- **URI エンコーディング**: `Uri.Builder.appendQueryParameter()` で自動処理

### Kotlin / Compose コーディング規約
- **StateFlow の使用**: ViewModel 内で `MutableStateFlow<State>` で状態管理
- **データクラス**: `@Composable` 外で状態を持つクラスはデータクラスとして定義
- **名前空間**:
  - `ui/` パッケージ: Composable 関数・ViewModel
  - `format/` パッケージ: NoteConfig・NoteComposer
  - `content/` パッケージ: コンテンツ処理
- **テストコメント**:
  - 【テスト目的】【テスト内容】【期待される動作】の3点セットで記述
  - 信頼性レベル（🔵/🟡/🔴）を明記

### ViewModel 実装パターン
```kotlin
class EditScreenViewModel : ViewModel() {
    private val _formState = MutableStateFlow<EditFormState>(初期値)
    val formState: StateFlow<EditFormState> = _formState.asStateFlow()

    private var initialized = false

    fun initialize(processed: ProcessedContent, config: NoteConfig) {
        if (initialized) return  // 重複実行防止（EDGE-101: 画面回転対応）
        initialized = true
        _formState.value = EditFormState(
            title = processed.title ?: "",
            body = processed.body,
            tagsText = config.defaultTags.joinToString(", "),
            folder = config.folder
        )
    }
}
```

**参照元**:
- docs/spec/content-edit-preview/requirements.md - REQ-401, REQ-402, NFR-103
- docs/design/content-edit-preview/architecture.md - ViewModel設計方針
- docs/design/content-edit-preview/dataflow.md - EditScreenViewModel フロー

---

## 3. 関連実装

### 既存コンポーネント（参照用）
| クラス | パッケージ | 役割 | ファイルパス |
|--------|----------|------|----------|
| `MainActivity` | root | Activity エントリポイント（変更対象） | app/src/main/java/.../MainActivity.kt |
| `NoteComposer` | format | Frontmatter+URI生成（TASK-0015で実装済） | app/src/main/java/.../format/NoteComposer.kt |
| `NoteConfig` | format | 設定データ（TASK-0015で実装済） | app/src/main/java/.../format/NoteConfig.kt |
| `ProcessedContent` | content | 処理結果 | app/src/main/java/.../content/ProcessedContent.kt |
| `LoadingScreen` | ui | URL処理中ローディング | app/src/main/java/.../ui/LoadingScreen.kt |
| `FrontmatterBuilder` | format | Frontmatter生成（変更不可） | app/src/main/java/.../format/FrontmatterBuilder.kt |
| `ObsidianUriBuilder` | format | URI構築（変更不可） | app/src/main/java/.../format/ObsidianUriBuilder.kt |

### テスト参考実装
| テストファイル | テスト対象 | ファイルパス |
|----------|----------|----------|
| `NoteComposerTest` | NoteComposer | app/src/test/java/.../format/NoteComposerTest.kt |
| `FrontmatterBuilderTest` | FrontmatterBuilder | app/src/test/java/.../format/FrontmatterBuilderTest.kt |
| `TextContentProcessorTest` | TextContentProcessor | app/src/test/java/.../content/TextContentProcessorTest.kt |

### 参考パターン
- **Composable UI**: LoadingScreen.kt 参照（ローディング画面実装例）
- **ViewModel 状態管理**: NoteComposerTest.kt 参照（単体テスト構造）
- **MainActivity 統合**: MainActivityTest.kt 参照（Activity テスト）

**参照元**:
- app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt
- app/src/main/java/com/den4dr/share2Obsidian/ui/LoadingScreen.kt
- docs/design/content-edit-preview/architecture.md - コンポーネント構成

---

## 4. 設計文書

### 要件定義
- **統合要件**: docs/spec/content-edit-preview/requirements.md
  - REQ-001: 全コンテンツで編集画面表示
  - REQ-003/004: 編集フィールド・ボタン定義
  - REQ-101: 送信時 Obsidian 起動
  - REQ-201: キャンセル動作
  - REQ-301/302: URL処理フロー
  - NFR-101/102: Compose UI 実装パターン
  - EDGE-101/102: 画面回転・バックボタン対応

### アーキテクチャ・データフロー
- **アーキテクチャ**: docs/design/content-edit-preview/architecture.md
  - EditScreen + EditScreenViewModel コンポーネント
  - MainActivity フロー変更: 処理完了 → EditScreen表示
  - URL処理: LoadingScreen → EditScreen切り替え

- **データフロー**: docs/design/content-edit-preview/dataflow.md
  - フロー3: 送信ボタンタップ時の NoteComposer 呼び出し
  - フロー4: キャンセルボタンタップ
  - EditFormState 状態遷移図
  - タグパース仕様（REQ-103）

### 型定義・インターフェース
- **EditFormState データクラス** (必須):
  ```kotlin
  data class EditFormState(
      val title: String,           // 初期値: ProcessedContent.title?? ""
      val body: String,            // 初期値: ProcessedContent.body
      val tagsText: String,        // 初期値: tags.joinToString(", ")
      val folder: String           // 初期値: config.folder
  )
  ```

- **EditScreen Composable** (必須):
  ```kotlin
  @Composable
  fun EditScreen(
      viewModel: EditScreenViewModel,
      onSend: (title: String?, body: String, tags: List<String>, config: NoteConfig) -> Unit,
      onCancel: () -> Unit
  )
  ```

- **EditScreenViewModel** (必須):
  ```kotlin
  class EditScreenViewModel : ViewModel() {
      val formState: StateFlow<EditFormState>
      fun initialize(processed: ProcessedContent, config: NoteConfig)
      fun onSend(): SendParams  // 内部的にタグパース
      fun onCancel()
  }
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
  - Uri / Context / Intent のテストに必須
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
│   ├── FrontmatterBuilderTest.kt  ← 参照（Frontmatter検証方法）
│   └── ObsidianUriBuilderTest.kt  ← 参照（URI検証方法）
├── content/
│   ├── TextContentProcessorTest.kt
│   ├── UrlContentProcessorTest.kt
│   └── ...
├── ui/
│   └── EditScreenViewModelTest.kt  ← TASK-0016 の新規テスト
└── MainActivityTest.kt             ← 統合テスト（参照）
```

### テストケース参考（acceptance-criteria.md より）
- **TC-003-01~04**: フィールド初期値表示テスト
- **TC-101-01~03**: 送信時の Frontmatter・URI生成テスト
- **TC-201-01**: キャンセル動作テスト
- **TC-EDGE-101-01**: 画面回転時の状態保持テスト
- **TC-EDGE-102-01**: バックボタン対応テスト

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

**参照元**:
- gradle/libs.versions.toml - robolectric, junit, androidx-test-core
- app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt
- docs/spec/content-edit-preview/acceptance-criteria.md

---

## 6. 注意事項

### 技術的制約

#### EditScreenViewModel の状態管理
- **重複実行防止**: `initialize()` は `initialized` フラグで1回のみ実行（EDGE-101: 画面回転対応）
  ```kotlin
  private var initialized = false
  fun initialize(processed: ProcessedContent, config: NoteConfig) {
      if (initialized) return
      initialized = true
      _formState.value = ...
  }
  ```
- **StateFlow のメモリ管理**: ViewModel スコープ（Activity 再作成時に保持）

#### EditScreen の UI 実装
- **複数行テキスト入力**: 本文は `TextField(modifier = Modifier.height(200.dp))` など（NFR-101）
- **ボタン固定表示**: Column + Spacer で本文がスクロール可能、ボタンは常に下部固定（NFR-102）
- **Material3 コンポーネント**: OutlinedTextField, Button, Scaffold など

#### タグパース仕様（REQ-103）
```kotlin
fun parseTagsText(tagsText: String): List<String> {
    return tagsText
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
```
- 入力: `"shared, web, clipping "` → 出力: `["shared", "web", "clipping"]`
- 空文字列: `""` → `[]` (EDGE-003)

#### MainActivity との連携
- **setContent 切り替え**: 処理完了後に `setContent { EditScreen(...) }` に切り替え
- **onSend コールバック**: EditScreen から MainActivity の `startActivity()` を呼び出し
  ```kotlin
  fun onSend() {
      val content = NoteComposer.buildFrontmatter(...)
      val uri = NoteComposer.buildUri(...)
      startActivity(Intent(ACTION_VIEW, uri))
      finish()
  }
  ```
- **onCancel コールバック**: `finish()` のみ（Obsidian 起動なし）

### セキュリティ・パフォーマンス

#### セキュリティ
- **URI エンコーディング**: `Uri.Builder.appendQueryParameter()` で自動処理（既存実装継承）
- **入力検証**: 本文・タイトル空許容（EDGE-002/001）、タグパース時の空文字列フィルタリング

#### パフォーマンス
- **初期表示時間**: テキスト・HTML・ファイルは < 100ms（NFR-001）
- **State 変更**: `MutableStateFlow` での状態変更は O(1)
- **Recomposition 最適化**: `remember { }` や `derivedStateOf { }` で不要な再描画を防止

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
- docs/implements/content-edit-preview/TASK-0016/ (本タスク: EditScreen + ViewModel)

**参照元**:
- docs/spec/content-edit-preview/requirements.md - REQ-401, REQ-402, REQ-101, REQ-103, NFR-101/102/103, EDGE-001/002/003/101/102
- docs/design/content-edit-preview/architecture.md - MainActivity フロー変更、EditScreen設計
- docs/design/content-edit-preview/dataflow.md - フロー1/2/3/4、EditFormState状態遷移
- CLAUDE.md - Build Commands, Localization Note

---

## 7. 依存関係・ブロッキング

### 前提条件（ブロック解除待ち）
- ✅ TASK-0015: NoteConfig + NoteComposer 実装完了

### ブロック対象（本タスク完了後に実行可能）
- TASK-0017: MainActivity フロー統合（EditScreen表示への切り替え）
- TASK-0018: E2E テスト（UI テスト）

### 実装順序
1. **データクラス定義** (EditFormState)
2. **ViewModel 実装** (EditScreenViewModel)
3. **Composable UI 実装** (EditScreen)
4. **ユニットテスト** (EditScreenViewModelTest)
5. **統合テスト準備** (MainActivity 統合は TASK-0017)

---

## 8. ファイルパス一覧

### 実装対象
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt` (新規 Composable)
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` (新規 ViewModel)

### テスト対象
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt` (新規テスト)

### 参照ファイル
- `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` (フロー変更対象は TASK-0017)
- `app/src/main/java/com/den4dr/share2Obsidian/format/NoteComposer.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/format/NoteConfig.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/content/ProcessedContent.kt`
- `app/src/main/res/values/strings.xml` (UI文字列定義)

### 設計・要件ドキュメント
- `docs/spec/content-edit-preview/requirements.md`
- `docs/spec/content-edit-preview/acceptance-criteria.md`
- `docs/design/content-edit-preview/architecture.md`
- `docs/design/content-edit-preview/dataflow.md`
- `docs/implements/content-edit-preview/TASK-0015/content-edit-preview-requirements.md`

---

## 9. テスト実行確認リスト

### 実装時チェックリスト
- [ ] EditFormState データクラスが定義されている
- [ ] EditScreenViewModel が StateFlow<EditFormState> を持つ
- [ ] initialize() が重複実行を防ぐ
- [ ] parseTagsText() が正しくカンマ区切りをパースする
- [ ] EditScreen Composable がすべてのフィールド + ボタンを表示
- [ ] 送信・キャンセルコールバックが正しく機能

### テスト実行確認
```bash
# ユニットテスト実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"

# 全テスト実行（回帰テスト）
mise exec -- ./gradlew test

# ビルド確認
mise exec -- ./gradlew assembleDebug
```

### 完了条件（TASK-0016）
- [ ] EditScreenViewModelTest が全件パス
- [ ] NoteComposerTest が全件パス（回帰テスト）
- [ ] assembleDebug が成功
- [ ] コンパイルエラーなし

---

**作成者**: Claude Code
**最終更新**: 2026-03-31
