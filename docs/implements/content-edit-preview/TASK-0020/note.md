# TASK-0020: MainActivity フロー変更 コンテキストノート

**作成日**: 2026-05-30  
**タスクID**: TASK-0020  
**タスク区分**: TDD（Test-Driven Development）  
**フェーズ**: Phase 2 - UI・統合実装  
**推定工数**: 4時間  
**信頼性レベル**: 🔵 *TASK-0020.md, dataflow.md, architecture.md より*  

---

## タスク概要

`MainActivity` のフローを変更し、**処理完了後の即時 Obsidian 起動を撤廃**して、代わりに **`EditScreen` を表示**するように修正する。

ユーザーが編集画面で送信ボタンを押したときに、`NoteComposer` を経由して URI を構築し Obsidian を起動する。キャンセルボタンを押した場合は Obsidian を起動せずにアプリを終了する。

### 変更範囲

| 項目 | 詳細 |
|------|------|
| **変更対象ファイル** | `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` |
| **使用するコンポーネント** | EditScreen, EditScreenViewModel, NoteComposer, NoteConfig, SendParams |
| **削除するコード** | FrontmatterBuilder/ObsidianUriBuilder の直接呼び出し（ファイル自体は残す） |
| **新規依存関係** | `androidx.lifecycle.viewmodels` (viewModels delegate) |

---

## 現在の実装（変更前）

```kotlin
// MainActivity.kt（現在）
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareContent = ContentTypeDetector.detect(intent)
        if (shareContent == null) {
            finish()
            return
        }

        if (shareContent is ShareContent.Url) {
            setContent { LoadingScreen() }
        }

        lifecycleScope.launch {
            val processed = when (shareContent) {
                is ShareContent.Text -> TextContentProcessor().process(shareContent)
                is ShareContent.Url -> UrlContentProcessor(WebViewExtractor(this@MainActivity)).process(shareContent)
                is ShareContent.Html -> HtmlContentProcessor().process(shareContent)
                is ShareContent.File -> FileContentProcessor(this@MainActivity).process(shareContent)
            }

            // ❌ これを削除して EditScreen に置き換える
            val noteContent = FrontmatterBuilder.build(processed.title, processed.body)
            val uri = ObsidianUriBuilder.build(noteContent, processed.title)

            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_obsidian_not_installed),
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }
}
```

**問題点**:
- コンテンツ処理完了直後に即座に Obsidian を起動
- ユーザーが内容を確認・編集する機会がない
- FrontmatterBuilder と ObsidianUriBuilder が MainActivity で直接呼び出されている

---

## 変更後の実装フロー

```
Intent 受け取り
  ↓
ContentTypeDetector.detect()
  ↓
URL の場合？
  → Yes: setContent { LoadingScreen() } 表示
  → No: スキップ
  ↓
ContentProcessor.process() [suspend]
  ↓
viewModel.initialize(processed, NoteConfig.fromAppConfig())
  ↓
setContent { EditScreen(viewModel, onSend, onCancel) } → UI表示
  ↓
ユーザー操作
  ├─ 送信ボタン
  │   ├─ viewModel.buildSendParams(config) → SendParams
  │   ├─ NoteComposer.buildFrontmatter()
  │   ├─ NoteComposer.buildUri()
  │   ├─ startActivity(Intent(ACTION_VIEW, uri))
  │   └─ finish()
  │
  └─ キャンセルボタン / バックボタン
      └─ finish()
```

---

## 依存コンポーネント詳細

### 1. EditScreenViewModel

**責務**: フォーム状態（EditFormState）の管理と送信パラメータ（SendParams）の構築

**重要メソッド**:
- `initialize(processed: ProcessedContent, config: NoteConfig)` — 初期値をセット
- `buildSendParams(config: NoteConfig): SendParams` — タグパース・タイトル null 変換済みの SendParams を返す
- `updateTitle/updateBody/updateTagsText/updateFolder()` — フィールド更新

**ライフサイクル**:
- `viewModels()` デリゲートで Activity スコープに束縛（画面回転時も保持）
- `initialize()` は `initialized` フラグで重複実行を防止（EDGE-101）

### 2. EditScreen

**責務**: 編集フォーム UI の表示と user interaction のキャプチャ

**コールバック**:
```kotlin
EditScreen(
    viewModel: EditScreenViewModel,
    config: NoteConfig,
    onSend: (SendParams) -> Unit,     // 送信ボタン → MainActivity で Obsidian 起動
    onCancel: () -> Unit,              // キャンセル・バックボタン → finish()
)
```

**フィールド**:
- タイトル（`label_title`）
- 本文（`label_body`）
- タグ（`label_tags`）
- フォルダ（`label_folder`）

**ボタン**:
- キャンセル（`button_cancel`）→ OutlinedButton
- 送信（`button_send`）→ Button（主要アクション）

### 3. NoteComposer

**責務**: 編集後の値から Frontmatter 文字列と Obsidian URI を生成

**静的メソッド**:
```kotlin
// Frontmatter 生成（タイトル nullable, タグリスト）
fun buildFrontmatter(title: String?, body: String, tags: List<String>): String

// Obsidian URI 生成（NoteConfig から vault/folder 取得）
fun buildUri(content: String, title: String?, config: NoteConfig): Uri
```

**特徴**:
- AppConfig 非依存（REQ-402）
- 既存の FrontmatterBuilder/ObsidianUriBuilder は変更しない
- すべてのパラメータを関数引数で明示的に受け取る

### 4. NoteConfig

**責務**: Obsidian ノート送信の設定（vault, folder, defaultTags）を保持

**ファクトリメソッド**:
```kotlin
fun fromAppConfig(): NoteConfig
  → vault: AppConfig.OBSIDIAN_VAULT = "testVault"
  → folder: AppConfig.OBSIDIAN_FOLDER = "70_clippings"
  → defaultTags: AppConfig.OBSIDIAN_TAGS = listOf("shared")
```

**役割**:
- EditScreenViewModel の初期値ソース
- NoteComposer への依存渡し（URI 構築時）

### 5. SendParams

**責務**: 送信ボタンタップ時のパラメータ転送

**定義**:
```kotlin
data class SendParams(
    val title: String?,          // null: タイトルなし（EDGE-001）
    val body: String,            // 空文字列可（EDGE-002）
    val tags: List<String>,      // 空リスト可（EDGE-003）
    val config: NoteConfig       // vault/folder を含む設定
)
```

---

## 実装詳細

### MainActivity の変更コード例

```kotlin
class MainActivity : ComponentActivity() {
    // ✅ viewModels() デリゲートで ViewModel を取得
    private val viewModel: EditScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareContent = ContentTypeDetector.detect(intent)
        if (shareContent == null) {
            finish()
            return
        }

        // URL の場合は処理中にローディング画面を表示（変更なし）
        if (shareContent is ShareContent.Url) {
            setContent { LoadingScreen() }
        }

        // ✅ NoteConfig.fromAppConfig() で初期設定を取得
        val config = NoteConfig.fromAppConfig()

        lifecycleScope.launch {
            // コンテンツ処理（変更なし）
            val processed = when (shareContent) {
                is ShareContent.Text -> TextContentProcessor().process(shareContent)
                is ShareContent.Url -> UrlContentProcessor(WebViewExtractor(this@MainActivity)).process(shareContent)
                is ShareContent.Html -> HtmlContentProcessor().process(shareContent)
                is ShareContent.File -> FileContentProcessor(this@MainActivity).process(shareContent)
            }

            // ✅ ViewModel を初期化
            viewModel.initialize(processed, config)

            // ✅ EditScreen を表示し、コールバックを接続
            setContent {
                EditScreen(
                    viewModel = viewModel,
                    config = config,
                    onSend = { sendParams ->
                        // 送信ボタンの処理
                        val content = NoteComposer.buildFrontmatter(
                            sendParams.title, 
                            sendParams.body, 
                            sendParams.tags
                        )
                        val uri = NoteComposer.buildUri(content, sendParams.title, sendParams.config)
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.error_obsidian_not_installed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        finish()
                    },
                    onCancel = {
                        // キャンセルボタン・バックボタンの処理
                        finish()
                    }
                )
            }
        }
    }
}
```

---

## 削除するコード

以下のコードを **削除**（ファイル自体は残す）:

```kotlin
// ❌ これを削除
val noteContent = FrontmatterBuilder.build(processed.title, processed.body)
val uri = ObsidianUriBuilder.build(noteContent, processed.title)
```

理由:
- FrontmatterBuilder/ObsidianUriBuilder は AppConfig に依存する
- EditScreen コールバックで NoteComposer 経由の処理に置き換えられる
- REQ-402: 既存クラスは変更・削除しない

---

## テスト要件

### テストケース1: キャンセル後に Activity が終了する

**TC-201-01** (EDGE-102: バックボタン)

| 項目 | 内容 |
|------|------|
| **前提条件** | テキスト共有インテントで MainActivity が起動・EditScreen 表示中 |
| **操作** | キャンセルボタンをタップ |
| **期待結果** | Activity が finish() して終了。Obsidian への startActivity が呼ばれない |
| **検証方法** | Activity が alive/destroyed に遷移することを確認 |

### テストケース2: 送信後に Obsidian URI が正しく構築される

**TC-101-01** (REQ-101)

| 項目 | 内容 |
|------|------|
| **前提条件** | タイトル "テスト"、本文 "本文"、タグ "shared, web"、フォルダ "70_clippings" で編集済み |
| **操作** | 送信ボタンをタップ |
| **期待結果** | `obsidian://new?content=...&title=テスト&vault=testVault&folder=70_clippings` で startActivity が呼ばれる |
| **検証方法** | Intent のデータ URI が期待値と一致することを確認 |

### テストケース3: Obsidian 未インストール時にトーストが表示される

**TC-101-E01** (REQ-401 エラー処理)

| 項目 | 内容 |
|------|------|
| **前提条件** | Obsidian がインストールされていない環境で EditScreen 表示中 |
| **操作** | 送信ボタンをタップ |
| **期待結果** | ActivityNotFoundException がキャッチされ、トーストが表示（R.string.error_obsidian_not_installed）、Activity が finish() |
| **検証方法** | Toast の表示と finish() の呼び出しを確認 |

### テストケース4: URL フロー LoadingScreen → EditScreen 遷移

**TC-301-01** (REQ-301 URL処理)

| 項目 | 内容 |
|------|------|
| **前提条件** | URL 共有インテントで MainActivity が起動 |
| **操作** | （自動：WebView 本文抽出） |
| **期待結果** | LoadingScreen 表示 → EditScreen に切り替わる。本文フィールドに抽出テキストが入力済み |
| **検証方法** | screen state の遷移と EditScreen の body フィールド内容を確認 |

---

## 関連ファイル一覧

### 変更対象

| ファイル | 変更内容 |
|---------|--------|
| `MainActivity.kt` | フロー全体を変更：EditScreen 表示・コールバック処理 |

### 使用するコンポーネント（変更なし）

| ファイル | 役割 |
|---------|------|
| `EditScreen.kt` | UI 表示・user interaction キャプチャ |
| `EditScreenViewModel.kt` | フォーム状態管理・SendParams 構築 |
| `NoteComposer.kt` | Frontmatter + URI 生成 |
| `NoteConfig.kt` | 設定保持・fromAppConfig() ファクトリ |
| `SendParams.kt` | 送信パラメータデータクラス |
| `EditFormState.kt` | フォーム状態データクラス・parseTagsText() |

### 参照ドキュメント

| ファイル | 内容 |
|---------|------|
| `TASK-0020.md` | タスク定義 |
| `overview.md` | 全体タスク概要 |
| `architecture.md` | システムアーキテクチャ |
| `dataflow.md` | データフロー図・フロー1〜4 |
| `note.md` | スペック・技術スタック |

---

## 実装チェックリスト

- [ ] viewModels() デリゲートの import が依存関係に含まれているか確認
  - `implementation(libs.androidx.activity.compose)` 確認
  
- [ ] MainActivity に `viewModel: EditScreenViewModel` プロパティを追加
  
- [ ] `NoteConfig.fromAppConfig()` で初期設定を取得
  
- [ ] `viewModel.initialize(processed, config)` を coroutine 内で呼び出し
  
- [ ] `setContent { EditScreen(...) }` で EditScreen を表示
  
- [ ] `onSend` コールバックで：
  - `viewModel.buildSendParams(config)` → SendParams 取得
  - `NoteComposer.buildFrontmatter()` → Frontmatter 生成
  - `NoteComposer.buildUri()` → URI 生成
  - `startActivity()` → Obsidian 起動
  - `ActivityNotFoundException` キャッチ → Toast + finish()
  
- [ ] `onCancel` コールバックで `finish()` を呼び出し
  
- [ ] FrontmatterBuilder/ObsidianUriBuilder の直接呼び出しを削除
  
- [ ] `./gradlew assembleDebug` でコンパイルエラーなし
  
- [ ] 全テストケース通過

---

## エッジケース（EDGE）対応

### EDGE-001: タイトル空文字

- **入力**: `title = ""`（空文字列）
- **処理**: `ifBlank { null }` で null に変換
- **Frontmatter**: title フィールド省略
- **実装**: `NoteComposer.buildFrontmatter()` で対応

### EDGE-002: 本文空文字

- **入力**: `body = ""`（空文字列）
- **処理**: そのまま渡す（削除せず）
- **Frontmatter**: `---\n...\n---\n\n` で空ノート
- **実装**: EditFormState でも許容

### EDGE-003: タグ空リスト

- **入力**: `tagsText = ""` または `tagsText = ",,,"`
- **処理**: parseTagsText() で `[]` に変換
- **Frontmatter**: `tags: []` として出力
- **実装**: `joinToString()` で空リストは空文字列に

### EDGE-101: 画面回転

- **問題**: Activity 再作成時に EditScreenViewModel が alive なら状態を保持
- **解決**: `viewModels()` デリゲートで ViewModel スコープを Activity に束縛
- **initialize() の重複防止**: `initialized` フラグで 2 回目以降を無視

### EDGE-102: バックボタン

- **問題**: バックボタンとキャンセルボタンを同等に扱う
- **解決**: EditScreen で `BackHandler { onCancel() }` を実装
- **MainActivity**: onCancel コールバックで `finish()` を呼ぶだけ

---

## 注意事項

1. **viewModels() デリゲート**
   - `androidx.lifecycle.viewmodel` が build.gradle.kts に含まれることを確認
   - `androidx.activity.compose` に依存（既に存在）

2. **lifecycleScope.launch**
   - Dispatchers.Main で動作
   - setContent() は main thread で呼べるので追加でディスパッチャ指定は不要

3. **EditScreen 引数**
   - `config: NoteConfig` 引数が必須（EditScreen の定義から）
   - onSend/onCancel コールバック必須

4. **FrontmatterBuilder/ObsidianUriBuilder の扱い**
   - ファイル自体は削除しない（REQ-402）
   - MainActivity からの直接呼び出しのみ削除

5. **既存コンポーネントの変更**
   - ContentProcessor, LoadingScreen, WebViewExtractor などは変更なし
   - EditScreen, EditScreenViewModel, NoteComposer, NoteConfig は既に完成

---

## 信頼性レベル

| 項目 | レベル | 根拠 |
|------|--------|------|
| タスク全体 | 🔵 青 | TASK-0020.md, dataflow.md, architecture.md より確実な設計 |
| 実装詳細 | 🔵 青 | TASK-0020.md 「実装詳細」セクション |
| テストケース | 🔵 青 | TASK-0020.md 「単体テスト要件」「統合テスト要件」より |
| エッジケース | 🔵 青 | dataflow.md フロー図に記載されている処理フロー |

---

## 関連 Skill コマンド

TDD 開発フロー:

```bash
/tsumiki:tdd-requirements TASK-0020
/tsumiki:tdd-testcases
/tsumiki:tdd-red
/tsumiki:tdd-green
/tsumiki:tdd-refactor
/tsumiki:tdd-verify-complete
```

---

**作成者**: Claude Code  
**更新日**: 2026-05-30
