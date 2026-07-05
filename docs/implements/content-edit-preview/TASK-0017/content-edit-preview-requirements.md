# TASK-0017: EditScreenViewModel 実装 - TDD要件定義書

**タスクID**: TASK-0017
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: Phase 2 - UI・統合実装

---

## 1. 機能の概要

### 何をする機能か 🔵

**信頼性**: 🔵 *REQ-003, REQ-101, REQ-103, EDGE-101・interfaces.kt の EditScreenViewModelSpec 定義より*

`EditScreenViewModel` を実装する。編集画面（EditScreen）のフォーム状態を `StateFlow<EditFormState>` で管理し、以下の責務を持つ。

1. **初期化**: `ProcessedContent` と `NoteConfig` からフォーム初期値をセットする
2. **状態更新**: タイトル・本文・タグ・フォルダの各フィールドの更新メソッドを提供する
3. **送信パラメータ生成**: `buildSendParams()` でタグパースと title の null 変換を行い、`SendParams` を返す
4. **画面回転対応**: `initialized` フラグにより、画面回転時の重複初期化を防止する

### どのような問題を解決するか 🔵

**信頼性**: 🔵 *REQ-003（編集フィールド定義）、REQ-101（送信パラメータ）、EDGE-101（画面回転）より*

- 編集画面のフォーム状態を ViewModel スコープで管理し、Activity 再作成（画面回転）時にも状態を保持する仕組みが必要（EDGE-101）
- `ProcessedContent`（コンテンツ処理結果）と `NoteConfig`（アプリ設定）から編集フォームの初期値を生成するロジックが必要
- 送信ボタンタップ時に、フォーム状態から `SendParams` を構築する（タグパース・title null 変換を含む）ロジックが必要

### 想定されるユーザー 🔵

**信頼性**: 🔵 *user-stories.md・ユーザヒアリングより*

- 後続タスク（TASK-0019: EditScreen Composable）が直接使用する ViewModel
- 最終的にはエンドユーザーのフォーム編集操作を支える状態管理層

### システム内での位置づけ 🔵

**信頼性**: 🔵 *architecture.md・dataflow.md・依存関係グラフより*

- **パッケージ**: `com.den4dr.share2Obsidian.ui`（UI 層の ViewModel）
- **継承**: `androidx.lifecycle.ViewModel` を継承
- **依存先**:
  - `EditFormState`（`ui` パッケージ、TASK-0016 で実装済み）
  - `SendParams`（`ui` パッケージ、TASK-0016 で実装済み）
  - `parseTagsText()`（`ui` パッケージ、TASK-0016 で実装済み）
  - `ProcessedContent`（`content` パッケージ、既存実装）
  - `NoteConfig`（`format` パッケージ、TASK-0015 で実装済み）
- **依存元**: `EditScreen`（TASK-0019）が ViewModel を使用
- **位置**: データフローの「ViewModel 層」。ProcessedContent + NoteConfig を受け取り、EditFormState で状態管理し、SendParams を EditScreen に返す

**参照したEARS要件**: REQ-003, REQ-101, REQ-103, EDGE-101
**参照した設計文書**: architecture.md（コンポーネント構成・ViewModel 設計）、dataflow.md（フロー1: テキスト共有時、EditFormState 状態遷移図）、interfaces.kt（EditScreenViewModelSpec）

---

## 2. 入力・出力の仕様

### 2.1 EditScreenViewModel クラス 🔵

**信頼性**: 🔵 *REQ-003, REQ-101, REQ-103, EDGE-101・interfaces.kt の EditScreenViewModelSpec 定義より*

**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`

#### プロパティ

| プロパティ | 型 | 可視性 | 説明 | 信頼性 |
|-----------|-----|--------|------|--------|
| `_formState` | `MutableStateFlow<EditFormState>` | private | フォーム状態の内部 StateFlow | 🔵 EDGE-101 |
| `formState` | `StateFlow<EditFormState>` | public | フォーム状態の公開 StateFlow（イミュータブル） | 🔵 REQ-003 |
| `initialized` | `Boolean` | private | 重複初期化防止フラグ | 🔵 EDGE-101 |

### 2.2 initialize() メソッド 🔵

**信頼性**: 🔵 *REQ-001, REQ-003・acceptance-criteria.md TC-003-01〜04 より*

#### 入力

| パラメータ | 型 | 説明 | 制約 | 信頼性 |
|-----------|-----|------|------|--------|
| `processed` | `ProcessedContent` | コンテンツ処理結果 | `title` は nullable | 🔵 REQ-003 |
| `config` | `NoteConfig` | アプリ設定（vault, folder, defaultTags） | TASK-0015 で実装済み | 🔵 REQ-405 |

#### 出力（副作用）

| 変更対象 | 変更内容 | 信頼性 |
|----------|----------|--------|
| `_formState.value` | `EditFormState(title, body, tagsText, folder)` に更新 | 🔵 REQ-003 |
| `initialized` | `true` に変更 | 🔵 EDGE-101 |

#### 初期値マッピング

| EditFormState フィールド | 初期値の由来 | 信頼性 |
|-------------------------|------------|--------|
| `title` | `processed.title ?: ""`（null の場合は空文字） | 🔵 TC-003-01, TC-003-02 |
| `body` | `processed.body`（そのまま使用） | 🔵 REQ-003 |
| `tagsText` | `config.defaultTags.joinToString(", ")`（List -> カンマ区切り文字列） | 🔵 REQ-103, TC-003-03 |
| `folder` | `config.folder`（そのまま使用） | 🔵 REQ-405, TC-003-04 |

#### 重複初期化防止ロジック

```
1. initialized が true の場合、何もせずに return（画面回転時の保護）
2. initialized を true にセット
3. _formState.value を更新
```

### 2.3 update メソッド群 🔵

**信頼性**: 🔵 *REQ-003・ユーザストーリー2.1（ユーザーが編集）より*

| メソッド | 入力パラメータ | 型 | 動作 | 信頼性 |
|---------|--------------|-----|------|--------|
| `updateTitle(title)` | `title` | `String` | `_formState.value = _formState.value.copy(title = title)` | 🔵 REQ-003 |
| `updateBody(body)` | `body` | `String` | `_formState.value = _formState.value.copy(body = body)` | 🔵 REQ-003 |
| `updateTagsText(tagsText)` | `tagsText` | `String` | `_formState.value = _formState.value.copy(tagsText = tagsText)` | 🔵 REQ-103 |
| `updateFolder(folder)` | `folder` | `String` | `_formState.value = _formState.value.copy(folder = folder)` | 🔵 REQ-405 |

### 2.4 buildSendParams() メソッド 🔵

**信頼性**: 🔵 *REQ-101, REQ-103・dataflow.md フロー3（送信ボタンタップ）より*

#### 入力

| パラメータ | 型 | 説明 | 信頼性 |
|-----------|-----|------|--------|
| `config` | `NoteConfig` | 送信設定（vault, folder, defaultTags） | 🔵 REQ-405 |

#### 出力

| 戻り値 | 型 | 説明 | 信頼性 |
|--------|-----|------|--------|
| 送信パラメータ | `SendParams` | タグパース・title null 変換済みのパラメータ | 🔵 REQ-101 |

#### 変換ロジック

| SendParams フィールド | 変換元 | 変換ロジック | 信頼性 |
|---------------------|--------|------------|--------|
| `title` | `formState.title` | `ifBlank { null }`（空文字・スペースのみ -> null） | 🔵 EDGE-001 |
| `body` | `formState.body` | そのまま（空文字も許容） | 🔵 EDGE-002 |
| `tags` | `formState.tagsText` | `parseTagsText(tagsText)` でカンマ区切りパース | 🔵 REQ-103 |
| `config` | メソッド引数 | そのまま渡す | 🔵 REQ-405 |

#### データフロー

```
EditFormState（ViewModel 内 StateFlow）
    |
    ├── title: "タイトル" → ifBlank { null } → String? ("タイトル" or null)
    ├── body: "本文" → そのまま → String ("本文")
    ├── tagsText: "shared, web" → parseTagsText() → List<String> ["shared", "web"]
    └── config: メソッド引数 → そのまま → NoteConfig
    |
    v
SendParams(title, body, tags, config)
```

**参照したEARS要件**: REQ-001, REQ-003, REQ-101, REQ-103, REQ-405, EDGE-001, EDGE-002, EDGE-101
**参照した設計文書**: interfaces.kt（EditScreenViewModelSpec）、dataflow.md（フロー1, フロー3, EditFormState 状態変化）、acceptance-criteria.md（TC-003-01〜04, TC-101-02/03）

---

## 3. 制約条件

### パフォーマンス要件 🟡

**信頼性**: 🟡 *NFR-001 から妥当な推測（ViewModel 初期化は即座に完了）*

- `initialize()` の実行は O(1) で行われること（`joinToString` はタグ数に比例だが通常数個）
- `updateXxx()` メソッドの実行は O(1) で行われること（`copy()` によるイミュータブル更新）
- `buildSendParams()` の実行は O(n)（n = タグ文字列長）で、通常のユーザー入力サイズでは問題なし

### セキュリティ要件 🟡

**信頼性**: 🟡 *既存実装のセキュリティ方針から妥当な推測*

- 入力検証: 本文・タイトル空を許容（EDGE-001, EDGE-002）
- URI エンコーディング: `NoteComposer.buildUri()` が自動処理（ViewModel の責務外）
- ViewModel はデータ変換のみ行い、Intent 起動は行わない

### アーキテクチャ制約 🔵

**信頼性**: 🔵 *REQ-401, REQ-402・architecture.md より*

- `EditScreenViewModel` は `ui` パッケージに配置する（`com.den4dr.share2Obsidian.ui`）
- `androidx.lifecycle.ViewModel` を継承する
- `MutableStateFlow` を private に保持し、`asStateFlow()` で公開する
- `FrontmatterBuilder` / `ObsidianUriBuilder` は変更しない（REQ-402）。ViewModel は `SendParams` を返すのみ
- `parseTagsText()` は TASK-0016 で実装済みの関数を再利用する

### 互換性制約 🔵

**信頼性**: 🔵 *CLAUDE.md, NFR-201 より*

- minSdk 33（Android 13）以上で動作すること
- Kotlin 2.2.10 / Java 11 互換
- AndroidX Lifecycle ViewModel を使用（gradle/libs.versions.toml で管理）
- Kotlin Coroutines Flow を使用（StateFlow）

### テスト制約 🔵

**信頼性**: 🔵 *既存テストパターン（NoteComposerTest.kt, ParseTagsTextTest.kt 等）より*

- JUnit 4 + Robolectric 4.14.1 でユニットテストを記述する
- `@RunWith(RobolectricTestRunner::class)` / `@Config(sdk = [34])` を使用
- テストコメントに【テスト目的】【テスト内容】【期待される動作】の3点セットと信頼性レベルを記載する
- Arrange-Act-Assert パターンを使用する
- StateFlow テストには Kotlin Coroutines test utilities（`runTest`）を使用する

### 命名規約 🔵

**信頼性**: 🔵 *既存実装パターン・note.md の名前空間ルールより*

- クラス名: パスカルケース（`EditScreenViewModel`）
- テストクラス名: `EditScreenViewModelTest`
- テストメソッド: バッククォート記法 `` `TC-XXX 説明` ``
- プライベートフィールド: アンダースコアプレフィックス（`_formState`）

**参照したEARS要件**: REQ-401, REQ-402, NFR-001, NFR-201, EDGE-001, EDGE-002
**参照した設計文書**: architecture.md（パッケージ構成・ViewModel 設計）、CLAUDE.md（SDK・言語バージョン）

---

## 4. 想定される使用例

### 4.1 基本的な使用パターン 🔵

**信頼性**: 🔵 *REQ-003, REQ-101, REQ-103・dataflow.md フロー1 より*

#### パターン1: ViewModel の初期化（MainActivity から使用）

```kotlin
// ProcessedContent と NoteConfig から ViewModel を初期化
val viewModel: EditScreenViewModel = viewModel()
val processed = ProcessedContent(body = "共有テキスト", title = "ページタイトル")
val config = NoteConfig.fromAppConfig()
viewModel.initialize(processed, config)
// → formState.value == EditFormState(
//        title = "ページタイトル",
//        body = "共有テキスト",
//        tagsText = "shared",
//        folder = "70_clippings"
//    )
```

#### パターン2: フォーム状態の更新（EditScreen から使用）

```kotlin
// ユーザーがタイトルを編集
viewModel.updateTitle("新しいタイトル")
// → formState.value.title == "新しいタイトル"

// ユーザーがタグを編集
viewModel.updateTagsText("shared, web, clipping")
// → formState.value.tagsText == "shared, web, clipping"
```

#### パターン3: 送信パラメータの生成（送信ボタンタップ時）

```kotlin
val sendParams = viewModel.buildSendParams(config)
// → SendParams(
//        title = "新しいタイトル",  // or null if blank
//        body = "共有テキスト",
//        tags = ["shared", "web", "clipping"],
//        config = NoteConfig(...)
//    )
```

### 4.2 エッジケース 🔵/🟡

#### エッジケース1: 画面回転時の重複初期化防止 🔵

**信頼性**: 🔵 *EDGE-101・acceptance-criteria.md TC-EDGE-101-01 より*

```kotlin
// 初回初期化
viewModel.initialize(processed, config)
// ユーザーがタイトルを編集
viewModel.updateTitle("変更後タイトル")

// 画面回転後、Activity 再作成で initialize() が再度呼ばれる
viewModel.initialize(processed, config)
// → formState.value.title == "変更後タイトル"（初期値に戻らない）
```

#### エッジケース2: タイトルが null の ProcessedContent 🔵

**信頼性**: 🔵 *TC-003-02（タイトル null 時は空文字）より*

```kotlin
val processed = ProcessedContent(body = "本文", title = null)
viewModel.initialize(processed, config)
// → formState.value.title == ""
```

#### エッジケース3: 空タイトルでの送信 🔵

**信頼性**: 🔵 *EDGE-001・interfaces.kt SendParams 定義より*

```kotlin
viewModel.updateTitle("")
val params = viewModel.buildSendParams(config)
// → params.title == null（空文字は null に変換）
```

#### エッジケース4: スペースのみのタイトルでの送信 🟡

**信頼性**: 🟡 *EDGE-001 から妥当な推測（ifBlank の動作）*

```kotlin
viewModel.updateTitle("   ")
val params = viewModel.buildSendParams(config)
// → params.title == null（スペースのみも null に変換）
```

### 4.3 エラーケース 🟡

**信頼性**: 🟡 *EDGE-001, EDGE-002, EDGE-003 から妥当な推測*

- **initialize() に null ProcessedContent は渡されない**: Kotlin の型システムで非 nullable を保証
- **buildSendParams() で body が空文字**: エラーではなく正常な状態（EDGE-002）
- **buildSendParams() で tagsText が空文字**: `parseTagsText("")` → `[]` で正常動作（EDGE-003）

**参照したEARS要件**: REQ-003, REQ-101, REQ-103, EDGE-001, EDGE-002, EDGE-003, EDGE-101
**参照した設計文書**: dataflow.md（フロー1, フロー3, EditFormState 状態変化図）、interfaces.kt（EditScreenViewModelSpec）、acceptance-criteria.md（TC-003-01〜04, TC-EDGE-101-01）

---

## 5. EARS要件・設計文書との対応関係

### 参照したユーザストーリー

- ユーザストーリー1.1: テキスト共有時の編集画面表示
- ユーザストーリー2.1: 編集フォームでの値変更
- ユーザストーリー2.2: 送信ボタンタップ時のパラメータ構築

### 参照した機能要件

| 要件ID | 内容 | 対応する実装 | 信頼性 |
|--------|------|------------|--------|
| REQ-001 | 全コンテンツタイプで編集画面表示 | `initialize()` で全タイプの ProcessedContent を受け入れ | 🔵 |
| REQ-003 | 編集フィールド（title, body, tags, folder）の表示 | `StateFlow<EditFormState>` で4フィールドを管理 | 🔵 |
| REQ-101 | 送信時に編集後の値から URI 構築 | `buildSendParams()` で SendParams を生成 | 🔵 |
| REQ-103 | タグフィールドのカンマ区切りパース | `buildSendParams()` 内で `parseTagsText()` を呼び出し | 🔵 |
| REQ-405 | フォルダ初期値は AppConfig から | `initialize()` で `config.folder` を使用 | 🔵 |

### 参照した非機能要件

| 要件ID | 内容 | 対応 | 信頼性 |
|--------|------|------|--------|
| NFR-001 | 編集画面の初期表示 100ms 以内 | ViewModel 初期化は O(1) | 🟡 |
| NFR-201 | minSdk 33 対応 | AndroidX ViewModel + Kotlin Coroutines Flow 使用 | 🔵 |

### 参照したEdgeケース

| ケースID | 内容 | 対応 | 信頼性 |
|----------|------|------|--------|
| EDGE-001 | タイトル空で送信 | `buildSendParams()` で `ifBlank { null }` | 🔵 |
| EDGE-002 | 本文空で送信 | `SendParams.body` は空文字許容 | 🟡 |
| EDGE-003 | タグ空で送信 | `parseTagsText("")` → `[]` | 🟡 |
| EDGE-101 | 画面回転後も状態保持 | `initialized` フラグで重複初期化防止 + ViewModel スコープ | 🔵 |

### 参照した受け入れ基準

- TC-003-01: タイトル初期値が ProcessedContent.title で設定される
- TC-003-02: タイトルが null の場合、タイトルフィールドは空
- TC-003-03: タグフィールドの初期値が AppConfig.OBSIDIAN_TAGS から生成される
- TC-003-04: フォルダフィールドの初期値が AppConfig.OBSIDIAN_FOLDER
- TC-101-02: タグのカンマ区切り入力が List に変換される
- TC-101-03: タグのカンマ区切りでスペースがトリムされる
- TC-EDGE-001-01: タイトルが空でも送信できる
- TC-EDGE-101-01: 画面回転後も入力内容が保持される

### 参照した設計文書

| 文書 | 該当セクション |
|------|------------|
| **アーキテクチャ**: architecture.md | コンポーネント構成、ViewModel 設計方針、パッケージ配置 |
| **データフロー**: dataflow.md | フロー1（テキスト共有時）、フロー3（送信ボタンタップ）、EditFormState 状態変化図 |
| **型定義**: interfaces.kt | EditScreenViewModelSpec、EditFormState、SendParams、parseTagsText |
| **受け入れ基準**: acceptance-criteria.md | TC-003-01〜04, TC-101-02/03, TC-EDGE-001-01, TC-EDGE-101-01 |

---

## 6. 実装ファイル一覧

| ファイル | 種別 | 内容 |
|---------|------|------|
| `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` | 新規作成 | EditScreenViewModel クラス |
| `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt` | 新規作成 | EditScreenViewModel のユニットテスト |

### 依存ファイル（参照のみ、変更なし）

| ファイル | パッケージ | TASK |
|---------|----------|------|
| `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` | ui | TASK-0016 |
| `app/src/main/java/com/den4dr/share2Obsidian/ui/SendParams.kt` | ui | TASK-0016 |
| `app/src/main/java/com/den4dr/share2Obsidian/content/ProcessedContent.kt` | content | 既存 |
| `app/src/main/java/com/den4dr/share2Obsidian/format/NoteConfig.kt` | format | TASK-0015 |

---

## 7. テストケース一覧（概要）

| TC | テスト内容 | 対応要件 | 信頼性 |
|----|----------|----------|--------|
| TC-001 | initialize() で初期値がセットされる | REQ-003, TC-003-01〜04 | 🔵 |
| TC-002 | initialize() は2回目以降無視される | EDGE-101 | 🔵 |
| TC-003 | title が null の場合は空文字で初期化 | TC-003-02 | 🔵 |
| TC-004 | updateTitle() でタイトルが変更される | REQ-003 | 🔵 |
| TC-005 | buildSendParams() でタグがパースされる | REQ-103 | 🔵 |
| TC-006 | buildSendParams() で空タイトルが null になる | EDGE-001 | 🔵 |
| TC-007 | buildSendParams() でスペースのみタイトルが null になる | EDGE-001 | 🟡 |
| TC-008 | StateFlow の変更が正しく伝播する | EDGE-101 | 🟡 |

---

## 信頼性レベルサマリー

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 機能の概要 | 4 | 0 | 0 | 4 |
| 入出力の仕様 | 20 | 0 | 0 | 20 |
| 制約条件 | 4 | 2 | 0 | 6 |
| 使用例 | 4 | 2 | 0 | 6 |
| テストケース | 6 | 2 | 0 | 8 |
| **合計** | **38** | **6** | **0** | **44** |

- 🔵 **青信号**: 38項目 (86%)
- 🟡 **黄信号**: 6項目 (14%)
- 🔴 **赤信号**: 0項目 (0%)

**品質評価**: 高品質
