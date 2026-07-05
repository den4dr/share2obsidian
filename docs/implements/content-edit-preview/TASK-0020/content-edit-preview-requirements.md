# TASK-0020 MainActivity フロー変更 TDD要件定義書

**機能名**: content-edit-preview（展開内容の編集・プレビュー機能）
**タスクID**: TASK-0020
**要件名**: content-edit-preview
**フェーズ**: Phase 2 - UI・統合実装
**作成日**: 2026-05-30
**出力ファイル**: `docs/implements/content-edit-preview/TASK-0020/content-edit-preview-requirements.md`

---

## 信頼性レベル凡例

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: `MainActivity` のフローを変更し、コンテンツ処理完了後に即座に Obsidian を起動していた従来の動作を撤廃する。代わりに `EditScreen`（編集フォーム）を表示し、ユーザーがタイトル・本文・タグ・保存先フォルダを確認・編集してから送信できるようにする。送信ボタンで `NoteComposer` 経由で Obsidian URI を構築して `startActivity` を呼び、キャンセル／バックボタンでは Obsidian を起動せずに `finish()` する。

- 🔵 **どのような問題を解決するか**: 従来は処理完了直後に即座に Obsidian を起動していたため、ユーザーが共有内容を確認・編集する機会がなかった。本変更により「送信前に内容を確認・修正したい」というユーザー要求を満たす（ユーザーストーリー1.1〜1.4）。

- 🔵 **想定されるユーザー**: 他アプリからテキスト・URL・HTML・ファイルを Share2Obsidian に共有し、Obsidian にクリップする利用者。

- 🔵 **システム内での位置づけ**: 既存のレイヤード + ストラテジーパターン（`ContentTypeDetector` → `ContentProcessor` → フォーマット層）を維持したまま、処理層とフォーマット層の間に「編集画面層（EditScreen / EditScreenViewModel）」を挿入する統合ポイント。`MainActivity` は本機能群の唯一のエントリポイント兼コールバックの接続点であり、本タスクは Phase 2 の最終統合タスク（後続タスクなし）にあたる。

- **参照したEARS要件**: REQ-001, REQ-101, REQ-201, REQ-301, REQ-401, REQ-402
- **参照した設計文書**:
  - `docs/design/content-edit-preview/architecture.md` 「MainActivity フロー変更」「システム構成図」セクション
  - `docs/design/content-edit-preview/dataflow.md` 「システム全体のデータフロー」

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 入力

- 🔵 **Intent（onCreate の入力）**:
  - `ACTION_SEND` インテント（`text/plain` ほか各コンテンツタイプ）。`ContentTypeDetector.detect(intent)` で `ShareContent?`（`Text` / `Url` / `Html` / `File` の sealed class、または `null`）に判定される。
  - `null` の場合は即 `finish()` して終了（共有対象外のインテント）。

- 🔵 **ProcessedContent（処理結果・ViewModel 初期化の入力）**:
  - `ContentProcessor.process()` の戻り値。`body: String`、`title: String?`、`contentType` を保持。
  - 各タイプに対応するプロセッサ:
    - `ShareContent.Text` → `TextContentProcessor().process()`
    - `ShareContent.Url` → `UrlContentProcessor(WebViewExtractor(this)).process()`
    - `ShareContent.Html` → `HtmlContentProcessor().process()`
    - `ShareContent.File` → `FileContentProcessor(this).process()`

- 🔵 **NoteConfig（設定の入力）**:
  - `NoteConfig.fromAppConfig()` で生成。`vault = AppConfig.OBSIDIAN_VAULT`（"testVault"）、`folder = AppConfig.OBSIDIAN_FOLDER`（"70_clippings"）、`defaultTags = AppConfig.OBSIDIAN_TAGS`（`listOf("shared")`）。

- 🔵 **SendParams（送信コールバックの入力）**:
  - `EditScreen` 内で `viewModel.buildSendParams(config)` により生成され、`onSend` コールバックに渡される。
  - 定義: `data class SendParams(val title: String?, val body: String, val tags: List<String>, val config: NoteConfig)`。
  - `title` は空文字・空白のみの場合 `null` 変換済み（EDGE-001）、`body` は空文字許容（EDGE-002）、`tags` は `parseTagsText()` 適用済みで空リスト許容（EDGE-003）。

### 2.2 出力

- 🔵 **画面表示（setContent）**:
  - URL タイプの場合: 処理開始前に `setContent { LoadingScreen() }` を表示（REQ-301）。
  - 全タイプ共通: 処理完了後に `setContent { EditScreen(viewModel, config, onSend, onCancel) }` を表示。URL の場合は LoadingScreen → EditScreen に置き換わる。

- 🔵 **Obsidian 起動（送信時の出力）**:
  - `onSend` 内で `NoteComposer.buildFrontmatter(title, body, tags)` → `NoteComposer.buildUri(content, title, config)` で `Uri`（`obsidian://new?content=...&title=...&vault=...&folder=...`）を生成し、`startActivity(Intent(Intent.ACTION_VIEW, uri))` を呼ぶ。
  - 例（タイトル "テスト"、本文 "本文"、タグ "shared, web"、フォルダ "70_clippings"）:
    `obsidian://new?content=<Frontmatter付き本文>&title=テスト&vault=testVault&folder=70_clippings`

- 🔵 **トースト表示（エラー時の出力）**:
  - `startActivity` が `ActivityNotFoundException` を投げた場合、`R.string.error_obsidian_not_installed` のトーストを `Toast.LENGTH_LONG` で表示（REQ-401 / 既存パターン踏襲）。

- 🔵 **Activity 終了（最終出力）**:
  - 送信完了後（成功・例外いずれも）`finish()`。
  - キャンセル・バックボタン押下時は `startActivity` を呼ばず `finish()` のみ（REQ-201、EDGE-102）。

### 2.3 入出力の関係性・データフロー

- 🔵 入力 Intent → `ContentTypeDetector.detect` → `ContentProcessor.process`（suspend、`lifecycleScope.launch` 内）→ `viewModel.initialize(processed, config)` → `setContent { EditScreen(...) }` → ユーザー操作 → `onSend` で `NoteComposer` 経由 URI 生成 → `startActivity` → `finish()`。キャンセル経路は `onCancel` → `finish()`。

- 🟡 **コールバック内の URI 構築主体について**: `dataflow.md` のシーケンス図では EditScreen が NoteComposer を呼ぶように描かれているが、TASK-0020.md・note.md の実装詳細では `EditScreen` は `SendParams` を渡すのみで、`NoteComposer.buildFrontmatter` / `buildUri` の呼び出しは `MainActivity` の `onSend` コールバック内で行う。本タスクでは後者（MainActivity 内で構築）を採用する。

- **参照したEARS要件**: REQ-001, REQ-101, REQ-103, REQ-201, REQ-301, REQ-405
- **参照した設計文書**:
  - `dataflow.md` フロー1（テキスト共有）・フロー2（URL共有）・フロー3（送信ボタン）・フロー4（キャンセル）
  - 型定義: `app/src/main/java/com/den4dr/share2Obsidian/ui/SendParams.kt`, `format/NoteConfig.kt`, `format/NoteComposer.kt`, `ui/EditScreenViewModel.kt`

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **アーキテクチャ制約（REQ-401）**: シングルアクティビティ（`MainActivity` のみ）を維持する。新規 Activity を追加しない。画面遷移は `setContent()` の差し替えで実現する。

- 🔵 **既存クラス不変更制約（REQ-402）**: `FrontmatterBuilder` / `ObsidianUriBuilder` のファイル自体は削除・変更しない。`MainActivity` からの**直接呼び出しのみ削除**し、`NoteComposer` 経由に置き換える。

- 🔵 **依存関係制約（viewModels デリゲート）**: `EditScreenViewModel` を `private val viewModel: EditScreenViewModel by viewModels()` で取得するため、`androidx.activity:activity-ktx`（または `activity-compose`）への依存が必要。`app/build.gradle.kts` に `implementation(libs.androidx.activity.compose)` が既に存在しており、`viewModels()` デリゲートは利用可能（追加依存不要）。

- 🔵 **EditScreen シグネチャ制約**: `EditScreen(viewModel, config, onSend, onCancel)` で `config: NoteConfig` が**第2引数**。`onSend: (SendParams) -> Unit`、`onCancel: () -> Unit`。呼び出し時は引数順・名前付き引数を一致させること。

- 🔵 **非同期制約**: コンテンツ処理は suspend 関数のため `lifecycleScope.launch` 内で実行する。`lifecycleScope.launch` は `Dispatchers.Main` で動作するため、内部から `setContent()` を直接呼べる（追加のディスパッチャ指定不要）。

- 🔵 **パフォーマンス要件（NFR-001, NFR-002）**: テキスト・HTML・ファイルは処理完了まで空白画面（< 100ms 想定）→ EditScreen 表示。URL は LoadingScreen → 抽出完了後 EditScreen 表示。

- 🔵 **セキュリティ要件（NFR-101）**: URI エンコードは `NoteComposer.buildUri()` 内の `Uri.Builder.appendQueryParameter()` に委譲され、既存 `ObsidianUriBuilder` と同等のエンコード処理を維持する（MainActivity 側で生 URI を組み立てない）。

- 🔵 **ローカライズ要件（NFR-103）**: ユーザー向け文字列（エラーメッセージ・ラベル・ボタン）は `res/values/strings.xml` を `getString` / `stringResource` で参照する。エラーメッセージは既存の日本語（`error_obsidian_not_installed`）を維持。

- 🔵 **テスト制約**: `androidTest`（インストゥルメントテスト）は実機／エミュレータが必要なため、CI 等での実行は限定的。本タスクの単体検証の中心は `./gradlew assembleDebug` のコンパイル通過確認とする。

- **参照したEARS要件**: REQ-401, REQ-402, REQ-405, NFR-001, NFR-002, NFR-101, NFR-103
- **参照した設計文書**: `architecture.md` 「アーキテクチャパターン」「非機能要件の実現方法」、`app/build.gradle.kts`、`docs/spec/content-edit-preview/note.md` 「開発ルール」

---

## 4. 想定される使用例（Edgeケース・データフローベース）

### 4.1 基本的な使用パターン

- 🔵 **テキスト共有（フロー1 / REQ-001, REQ-101）**: テキストを共有 → 処理 → `viewModel.initialize` → EditScreen 表示（初期値入力済み）→ 編集 → 送信 → `obsidian://new?...` で起動 → `finish()`。

- 🔵 **URL 共有（フロー2 / REQ-301, REQ-302）**: URL を共有 → `LoadingScreen` 表示 → `UrlContentProcessor` が WebView で本文抽出（最大10秒、タイムアウト時は URL 文字列を body にフォールバック）→ `viewModel.initialize` → EditScreen に切り替え（本文フィールドに抽出テキスト入力済み）。

- 🔵 **送信ボタン（フロー3 / REQ-101, REQ-103）**: `viewModel.buildSendParams(config)` で `SendParams` を取得 → `NoteComposer.buildFrontmatter` → `NoteComposer.buildUri` → `startActivity` → `finish()`。

- 🔵 **キャンセルボタン（フロー4 / REQ-201）**: `onCancel` → `startActivity` を呼ばずに `finish()`。

### 4.2 エッジケース

- 🔵 **EDGE-001 タイトル空文字**: タイトルが空文字・空白のみ → `buildSendParams` の `ifBlank { null }` で `null` 変換 → `buildFrontmatter` で title 行省略、`buildUri` で `title=`（空文字）。MainActivity 側は `SendParams.title` をそのまま渡すのみ。

- 🔵 **EDGE-002 本文空文字**: 本文が空文字 → そのまま渡し、空ノート（`---\n...\n---\n\n`）として送信。

- 🔵 **EDGE-003 タグ空リスト**: `tagsText = ""` または `",,,"` → `parseTagsText()` で `[]` に変換 → `tags: []` として出力。

- 🔵 **EDGE-101 画面回転**: Activity 再作成時、`viewModels()` デリゲートにより `EditScreenViewModel` が保持され編集内容が復元される。`viewModel.initialize()` の `initialized` フラグにより 2 回目以降の初期化は無視され、編集内容が上書きされない。MainActivity は再 `onCreate` 時も同様の処理を行うが ViewModel 側でガードされる。

- 🔵 **EDGE-102 バックボタン**: `EditScreen` 内 `BackHandler { onCancel() }` によりバックボタンをキャンセルと同等に扱う。MainActivity は `onCancel` コールバックで `finish()` を呼ぶのみ。

### 4.3 エラーケース

- 🔵 **Obsidian 未インストール（REQ-401 エラー処理 / TC-101-E01）**: `startActivity` が `ActivityNotFoundException` を投げた場合、`R.string.error_obsidian_not_installed` のトーストを表示し、`catch` ブロック後に `finish()`。

- 🔵 **共有対象外 Intent**: `ContentTypeDetector.detect(intent)` が `null` を返した場合、`setContent` を行わず即 `finish()` して `return`。

- **参照したEARS要件**: REQ-001, REQ-101, REQ-201, REQ-301, REQ-302, EDGE-001, EDGE-002, EDGE-003, EDGE-101, EDGE-102
- **参照した設計文書**: `dataflow.md` フロー1〜4・「タグパース仕様」・「EditFormState 状態変化」、`note.md`「エッジケース（EDGE）対応」

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**:
  - ストーリー1.1（テキスト共有して編集）
  - ストーリー1.2（URL 共有して本文抽出後に編集）
  - ストーリー1.4 / 2.1 / 2.2（編集後の送信・キャンセル）

- **参照した機能要件**:
  - REQ-001（共有コンテンツ処理）
  - REQ-101（送信ボタンで編集後の値から URI 構築・Obsidian 起動）
  - REQ-102（送信フロー）
  - REQ-103（タグのカンマ区切りパース）
  - REQ-201（キャンセルで Obsidian を起動せず終了）
  - REQ-301（URL 処理中の LoadingScreen 表示）
  - REQ-302（URL 抽出タイムアウト時のフォールバック）
  - REQ-401（シングルアクティビティ・既存エラー処理パターン）
  - REQ-402（FrontmatterBuilder / ObsidianUriBuilder を変更しない）
  - REQ-405（NoteConfig による vault/folder/defaultTags 設定）

- **参照した非機能要件**: NFR-001, NFR-002（パフォーマンス）, NFR-101（URI エンコード）, NFR-103（文字列リソース化）

- **参照したEdgeケース**: EDGE-001, EDGE-002, EDGE-003, EDGE-101, EDGE-102

- **参照した受け入れ基準（テスト項目）**:
  - TC-201-01（キャンセル後に Activity 終了・startActivity 未呼び出し）
  - TC-101-01（送信後に正しい Obsidian URI で startActivity）
  - TC-101-E01（Obsidian 未インストール時のトースト表示＋終了）
  - TC-301-01（URL フロー LoadingScreen → EditScreen 遷移）

- **参照した設計文書**:
  - **アーキテクチャ**: `architecture.md`「MainActivity フロー変更」「コンポーネント構成」「システム構成図」「非機能要件の実現方法」
  - **データフロー**: `dataflow.md`「システム全体のデータフロー」フロー1〜4・「NoteConfig データフロー」・「EditFormState 状態変化」
  - **型定義 / 既存実装**:
    - `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt`（変更前実装）
    - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（シグネチャ `EditScreen(viewModel, config, onSend, onCancel)`）
    - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（`initialize` / `buildSendParams`）
    - `app/src/main/java/com/den4dr/share2Obsidian/ui/SendParams.kt`
    - `app/src/main/java/com/den4dr/share2Obsidian/format/NoteComposer.kt`（`buildFrontmatter` / `buildUri`）
    - `app/src/main/java/com/den4dr/share2Obsidian/format/NoteConfig.kt`（`fromAppConfig`）
  - **ビルド設定**: `app/build.gradle.kts`（`androidx.activity.compose` 依存）
  - **タスク／ノート**: `docs/tasks/content-edit-preview/TASK-0020.md`, `docs/implements/content-edit-preview/TASK-0020/note.md`

---

## 6. 実装対象ファイル

| ファイル | 変更内容 |
|---------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` | フロー全体を変更：viewModels() デリゲート追加、EditScreen 表示、onSend/onCancel コールバック実装、FrontmatterBuilder/ObsidianUriBuilder 直接呼び出し削除、未使用 import 整理 |

### 変更後 MainActivity の構造（実装イメージ）

- `private val viewModel: EditScreenViewModel by viewModels()` を追加
- `onCreate`:
  1. `ContentTypeDetector.detect(intent)` → `null` なら `finish(); return`
  2. URL の場合 `setContent { LoadingScreen() }`
  3. `val config = NoteConfig.fromAppConfig()`
  4. `lifecycleScope.launch { ... }`:
     - `when` でコンテンツ処理 → `processed`
     - `viewModel.initialize(processed, config)`
     - `setContent { EditScreen(viewModel = viewModel, config = config, onSend = { ... }, onCancel = { finish() }) }`
       - `onSend`: `NoteComposer.buildFrontmatter(it.title, it.body, it.tags)` → `NoteComposer.buildUri(content, it.title, it.config)` → `try { startActivity(Intent(ACTION_VIEW, uri)) } catch (ActivityNotFoundException) { Toast(...) } ` → `finish()`
- 削除する import: `FrontmatterBuilder`, `ObsidianUriBuilder`
- 追加する import: `androidx.activity.viewModels`, `EditScreen`, `EditScreenViewModel`, `NoteComposer`, `NoteConfig`（およびコールバックで使用する型）

---

## 7. 品質判定結果

### 評価項目

| 項目 | 評価 | 根拠 |
|------|------|------|
| 要件の曖昧さ | なし（軽微な1点を明記） | 実装詳細が TASK-0020.md・note.md に具体的に記載済み。`dataflow.md` のシーケンス図と実装詳細で URI 構築主体に差異があるが、§2.3 で「MainActivity 内で構築」と明示し解消済み |
| 入出力定義 | 完全 | 入力（Intent / ProcessedContent / NoteConfig / SendParams）・出力（画面表示 / URI / トースト / finish）を型レベルで定義済み |
| 制約条件 | 明確 | REQ-401/402/405・viewModels 依存・EditScreen シグネチャ・非同期制約を明記 |
| 実装可能性 | 確実 | 依存コンポーネント（EditScreen, ViewModel, NoteComposer, NoteConfig, SendParams）はすべて実装済み。`androidx.activity.compose` 依存も既存 |
| 信頼性レベル分布 | 🔵 多数 | 大半が 🔵、1点のみ 🟡（URI 構築主体の解釈） |

### 信頼性レベル分布

- 🔵 青信号: 約95%（機能概要・入出力・制約・使用例の大部分）
- 🟡 黄信号: 約5%（dataflow.md と実装詳細の差異に関する解釈 §2.3）
- 🔴 赤信号: 0%

### 総合判定

**✅ 高品質**

- 要件の曖昧さ: ほぼなし（差異点は明示的に解消）
- 入出力定義: 完全
- 制約条件: 明確
- 実装可能性: 確実（前提タスク TASK-0015 / TASK-0019 完了済み）

---

## 次のステップ

次のお勧めステップ: `/tsumiki:tdd-testcases content-edit-preview 0020` でテストケースの洗い出しを行います。
