# TASK-0019: EditScreen Composable - TDD要件定義書

**機能名**: content-edit-preview（展開内容の編集・プレビュー機能）
**タスクID**: TASK-0019
**要件名**: content-edit-preview
**フェーズ**: Phase 2 - UI・統合実装
**作成日**: 2026-05-30
**実装対象**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`
**テスト対象**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt`

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

### 何をする機能か 🔵

- **🔵 青信号**: Obsidian へ送信する前のコンテンツ（タイトル・本文・タグ・保存先フォルダ）を、ユーザーが確認・編集できるフォーム画面（Compose Composable）を提供する。
- **🔵 青信号**: 4つの編集フィールド（タイトル・本文・タグ・フォルダ）と、「送信」「キャンセル」の2ボタンを持つ。
- **🔵 青信号**: Android のバックボタンを「キャンセル」と同等の動作にする（`BackHandler`）。
- **🔵 青信号**: 送信・キャンセルボタンは、フィールドのスクロールに関わらず常に画面下部に固定表示する。

### どのような問題を解決するか 🔵

- **🔵 青信号**: 従来の Share2Obsidian は共有テキストを「即時」Obsidian に転送していたため、ユーザーは送信前に内容を確認・修正できなかった。本機能（EditScreen）により、送信前にタイトル・本文・タグ・フォルダを編集できるようにする。
  - As a 共有ユーザー / So that 送信前に内容を確認・修正してから Obsidian に保存できる

### 想定されるユーザー 🔵

- **🔵 青信号**: Android の共有メニュー（Share Sheet）から Share2Obsidian を選択し、テキスト・URL・HTML・ファイルを Obsidian に保存しようとするユーザー。

### システム内での位置づけ 🔵

- **🔵 青信号**: 編集画面層（新規追加）に属する Compose UI コンポーネント。`ContentProcessor`（処理層）が生成した `ProcessedContent` を `EditScreenViewModel` 経由で受け取り、ユーザー編集後に `onSend` コールバックで `SendParams` を上位（MainActivity）へ返す。
- **🔵 青信号**: `EditScreen(viewModel, onSend, onCancel)` という純粋な Composable 関数で、状態は `EditScreenViewModel.formState`（StateFlow）に委譲する（UI 層は状態を持たない）。

**参照したEARS要件**: REQ-002, REQ-003, REQ-004, REQ-201, NFR-101, NFR-102, NFR-103, EDGE-102
**参照した設計文書**: architecture.md「コンポーネント構成 - 編集画面層」「システム構成図」、TASK-0019.md「タスク概要」「実装詳細」

---

## 2. 入力・出力の仕様（EARS機能要件・Kotlin型定義ベース）

### 入力パラメータ（Composable 引数）🔵

`EditScreen` Composable は以下の3引数を受け取る（architecture.md / TASK-0019.md より）:

| パラメータ | 型 | 説明 | 信頼性 |
|-----------|-----|------|--------|
| `viewModel` | `EditScreenViewModel` | フォーム状態を保持する ViewModel（TASK-0017実装済）。`formState: StateFlow<EditFormState>` を購読する | 🔵 |
| `onSend` | `(SendParams) -> Unit` | 送信ボタンタップ時に呼ばれるコールバック。`viewModel.buildSendParams(config)` の結果を渡す | 🔵 |
| `onCancel` | `() -> Unit` | キャンセルボタン・バックボタン時に呼ばれるコールバック | 🔵 |

### ViewModel から取得する状態（EditFormState）🔵

`viewModel.formState.collectAsState()` で取得する `EditFormState`（TASK-0016実装済）:

| フィールド | 型 | フォーム要素 | UI仕様 | 信頼性 |
|-----------|-----|------------|--------|--------|
| `title` | `String` | タイトル | `OutlinedTextField` + `singleLine = true` | 🔵 |
| `body` | `String` | 本文 | `OutlinedTextField` + `minLines = 5`（複数行） | 🔵 |
| `tagsText` | `String` | タグ（カンマ区切り） | `OutlinedTextField` + `singleLine = true` | 🔵 |
| `folder` | `String` | フォルダ | `OutlinedTextField` + `singleLine = true` | 🔵 |

### 出力（コールバック呼び出し）🔵

| イベント | 出力動作 | 信頼性 |
|---------|---------|--------|
| 送信ボタンタップ | `onSend(viewModel.buildSendParams(config))` を呼ぶ。`SendParams(title: String?, body: String, tags: List<String>, config: NoteConfig)` を渡す | 🔵 |
| キャンセルボタンタップ | `onCancel()` を呼ぶ（Obsidian は起動しない） | 🔵 |
| バックボタン押下 | `onCancel()` を呼ぶ（キャンセルと同等、EDGE-102） | 🔵 |
| フィールド入力 | `viewModel.updateTitle/Body/TagsText/Folder(it)` を呼び StateFlow を更新する | 🔵 |

### UI 文字列リソース（strings.xml、TASK-0018実装済）🔵

| リソースID | 値 | 用途 | 信頼性 |
|-----------|-----|------|--------|
| `R.string.label_title` | タイトル | タイトルラベル | 🔵 |
| `R.string.label_body` | 本文 | 本文ラベル | 🔵 |
| `R.string.label_tags` | タグ（カンマ区切り） | タグラベル | 🔵 |
| `R.string.label_folder` | フォルダ | フォルダラベル | 🔵 |
| `R.string.button_send` | 送信 | 送信ボタン | 🔵 |
| `R.string.button_cancel` | キャンセル | キャンセルボタン | 🔵 |

### 入出力の関係性・データフロー 🔵

- **🔵 青信号**:
  1. 入力: `viewModel.formState`（StateFlow）→ `collectAsState()` → 各 `OutlinedTextField.value` に表示
  2. ユーザー入力: `OutlinedTextField.onValueChange` → `viewModel.updateXxx(it)` → StateFlow 更新 → Recomposition
  3. 送信: 送信ボタン → `onSend(viewModel.buildSendParams(config))`
  4. キャンセル/バック: `onCancel()`

### NoteConfig の受け渡し（実装時判断事項）🟡

- **🟡 黄信号**: `buildSendParams(config: NoteConfig)` は config を引数に取る。EditScreen から呼ぶ際の config 取得方法（EditScreen 引数で渡す or ViewModel に保持する or MainActivity 側で保持）は実装時に判断する（TASK-0019.md 注意事項）。妥当な選択肢として、EditScreen に `config` 引数を追加する、または ViewModel に config を保持させる方式が考えられる。

**参照したEARS要件**: REQ-003, REQ-004, REQ-101, REQ-103, REQ-201, NFR-101, NFR-103, EDGE-102
**参照した設計文書**: interfaces.kt（EditFormState, SendParams, NoteConfig）、EditScreenViewModel.kt（buildSendParams シグネチャ）、strings.xml、TASK-0019.md「実装詳細1」

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

### パフォーマンス要件 🔵

- **🔵 青信号 (NFR-001/NFR-002)**: テキスト・HTML・ファイルは処理完了（< 100ms）後に EditScreen を表示する。EditScreen 自体は軽量な Compose 描画であり、Recomposition の最適化はフィールド単位の状態更新で実現する。

### UI/レイアウト制約 🔵 / 🟡

- **🔵 青信号 (NFR-101)**: タイトルフィールドは単一行（`singleLine = true`）、本文フィールドは複数行（`minLines = 5`）で実装する。
- **🟡 黄信号 (NFR-102)**: 送信・キャンセルボタンはスクロールに関わらず常に画面下部に固定表示する。`Scaffold.bottomBar` を使用し、フィールド部分は `verticalScroll(rememberScrollState())` でスクロール可能にする。
- **🔵 青信号 (NFR-103)**: すべての UI 文字列（ラベル・ボタン）は `strings.xml` にリソース定義し、`stringResource(R.string.xxx)` で参照する（ハードコード禁止）。

### 互換性・アーキテクチャ制約 🔵

- **🔵 青信号 (REQ-002)**: 編集画面は Jetpack Compose（Material3, `androidx.compose.material3.*`）で実装する。
- **🔵 青信号 (REQ-401)**: シングルアクティビティ構成を維持する。EditScreen は新規 Activity ではなく Composable 関数として実装し、MainActivity の `setContent` 内で使用される（統合は TASK-0020）。
- **🔵 青信号 (REQ-402)**: `FrontmatterBuilder` / `ObsidianUriBuilder` は変更しない（EditScreen は送信時に `viewModel.buildSendParams()` を返すのみで、URI 構築は NoteComposer / MainActivity 側が担う）。
- **🔵 青信号 (REQ-201)**: キャンセル時は Obsidian を起動せず、`onCancel()` のみを呼ぶ（`finish()` は MainActivity 側で処理）。

### 状態保持制約 🟡

- **🟡 黄信号 (EDGE-101)**: 画面回転（Activity 再作成）時もフォーム状態を保持する。これは `EditScreenViewModel`（StateFlow + initialized フラグ、TASK-0017実装済）が担保する。EditScreen は状態を持たず ViewModel から取得するため、EditScreen 単体での追加対応は不要。

### バックボタン制約 🔵 / 🟡

- **🔵 青信号 (EDGE-102 タスク定義)**: `BackHandler { onCancel() }` を EditScreen 内に配置し、Android バックボタンをキャンセルと同等にする。
- **🟡 黄信号 (EDGE-102 信頼性)**: requirements.md 上 EDGE-102 自体の信頼性は 🟡（Android UX 標準からの推測）だが、TASK-0019.md では実装方針が明示されているため実装は確実。

**参照したEARS要件**: REQ-002, REQ-201, REQ-401, REQ-402, NFR-001, NFR-002, NFR-101, NFR-102, NFR-103, EDGE-101, EDGE-102
**参照した設計文書**: architecture.md「アーキテクチャパターン」「非機能要件の実現方法」、TASK-0019.md「UI/UX要件」「注意事項」

---

## 4. 想定される使用例（EARS Edgeケース・データフローベース）

### 基本的な使用パターン 🔵

- **🔵 青信号 (REQ-003, REQ-004)**:
  1. MainActivity がコンテンツ処理後に `viewModel.initialize(processed, config)` を呼び、`EditScreen` を表示する
  2. ユーザーが4フィールド（タイトル・本文・タグ・フォルダ）を確認・編集する
  3. 「送信」をタップ → `onSend(SendParams)` → MainActivity が NoteComposer で URI 構築 → Obsidian 起動
  4. または「キャンセル」/バックボタン → `onCancel()` → MainActivity が `finish()`

### データフロー 🔵

- **🔵 青信号 (dataflow.md フロー3)**: 送信ボタンタップ時、`buildSendParams()` で `tagsText` を `parseTagsText()` でパースし、空タイトルを null 変換して `SendParams` を生成（変換ロジックは ViewModel 側、TASK-0017実装済）。EditScreen はこの結果を `onSend` に渡すのみ。

### エッジケース 🔵 / 🟡

| ケース | 入力 | 期待動作 | 信頼性 |
|--------|------|---------|--------|
| EDGE-001（空タイトル送信） | タイトル空欄で送信 | `buildSendParams()` が title=null を返す（ViewModel 担当）。EditScreen は空入力を許容 | 🔵 |
| EDGE-002（空本文送信） | 本文空欄で送信 | 空本文を許容して送信できる | 🔵 |
| EDGE-003（空タグ送信） | タグ空欄/カンマのみで送信 | `parseTagsText()` が空リストを返す（ViewModel 担当） | 🔵 |
| EDGE-101（画面回転） | 編集中に画面回転 | ViewModel が状態保持、EditScreen は再描画後も同じ内容を表示 | 🟡 |
| EDGE-102（バックボタン） | 編集画面でバックボタン押下 | `onCancel()` が呼ばれ Obsidian 未起動で終了 | 🟡 |
| 長文本文スクロール | 本文が画面を超える長さ | フィールド部分はスクロール、ボタンは下部固定（NFR-102） | 🟡 |

### エラーケース 🔵

- **🔵 青信号 (TC-101-E01)**: Obsidian 未インストール時のトースト表示は MainActivity 側（onSend 後）の責務であり、EditScreen の責務外。EditScreen は `onSend` コールバックを呼ぶところまでが責任範囲。

**参照したEARS要件**: REQ-003, REQ-004, EDGE-001, EDGE-002, EDGE-003, EDGE-101, EDGE-102
**参照した設計文書**: dataflow.md「フロー3 送信ボタンタップ時」、acceptance-criteria.md（TC-003, TC-101, TC-201, TC-EDGE-102）

---

## 5. EARS要件・設計文書との対応関係

### 参照したユーザストーリー
- 共有ユーザーが「送信前に内容を確認・修正してから Obsidian に保存したい」（user-stories.md / requirements.md 概要）

### 参照した機能要件
- **REQ-002**: 編集画面は Jetpack Compose で実装
- **REQ-003**: 4編集フィールド（title, body, tagsText, folder）を表示
- **REQ-004**: 「送信」「キャンセル」ボタンを表示
- **REQ-101**: 送信時に編集後の値から URI 構築（EditScreen は buildSendParams を onSend に渡す）
- **REQ-103**: タグフィールドのカンマ区切りパース（ViewModel 担当）
- **REQ-201**: キャンセルで Obsidian 起動せず finish()
- **REQ-401**: シングルアクティビティ維持
- **REQ-402**: FrontmatterBuilder / ObsidianUriBuilder を変更しない

### 参照した非機能要件
- **NFR-001 / NFR-002**: 初期表示パフォーマンス（< 100ms）
- **NFR-101**: タイトル単一行 / 本文複数行
- **NFR-102**: ボタン画面下部固定表示
- **NFR-103**: UI 文字列を strings.xml に定義

### 参照したEdgeケース
- **EDGE-001**: タイトル空で送信（ViewModel 担当）
- **EDGE-002**: 本文空で送信
- **EDGE-003**: タグ空で送信（ViewModel 担当）
- **EDGE-101**: 画面回転後の状態保持（ViewModel 担当）
- **EDGE-102**: バックボタンでキャンセル

### 参照した受け入れ基準（acceptance-criteria.md）
- **TC-003-01**: タイトル初期値が ProcessedContent.title で表示される
- **TC-003-03**: タグ初期値が AppConfig.OBSIDIAN_TAGS から生成される
- **TC-003-04**: フォルダ初期値が AppConfig.OBSIDIAN_FOLDER
- **TC-101-01**: 編集後の内容で送信（onSend 呼び出し）
- **TC-201-01**: キャンセル後に Obsidian が起動しない（onCancel 呼び出し）
- **TC-EDGE-102-01**: Android バックボタンがキャンセルと同じ動作をする

### 参照した設計文書
- **アーキテクチャ**: architecture.md「コンポーネント構成 - 編集画面層」「システム構成図」「ディレクトリ構造」「非機能要件の実現方法」
- **データフロー**: dataflow.md「フロー3 送信ボタンタップ時」「EditFormState 状態遷移」
- **型定義**: interfaces.kt（EditFormState, NoteConfig, SendParams）、EditScreenViewModel.kt（buildSendParams シグネチャ）
- **UI文字列**: strings.xml（label_*, button_*）
- **タスク定義**: TASK-0019.md（実装詳細・UI/UX要件・単体テスト要件）

---

## 6. テストケース概要（次フェーズへの橋渡し）

Compose UI Test（`app/src/androidTest/.../ui/EditScreenTest.kt`）として実装予定。

| # | テストケース | 検証内容 | 対応 | 信頼性 |
|---|------------|---------|------|--------|
| 1 | フィールド初期値表示 | 各フィールドに初期値が表示される | TC-003-01/03/04 | 🔵 |
| 2 | 送信ボタンで onSend が呼ばれる | 送信ボタンタップで onSend コールバックが起動 | TC-101-01 | 🔵 |
| 3 | キャンセルボタンで onCancel が呼ばれる | キャンセルボタンタップで onCancel コールバックが起動 | TC-201-01 | 🔵 |
| 4 | バックボタンで onCancel が呼ばれる | BackHandler でバックボタン → onCancel | TC-EDGE-102-01 | 🟡 |
| 5 | ボタン固定表示（任意） | 送信・キャンセルボタンが表示されている | NFR-102 | 🟡 |

> 注: Compose UI Test は `androidTest` 配下のため、実行にはデバイス/エミュレータが必要（`connectedAndroidTest`）。`assembleDebug` の成功は必須完了条件。

---

## 7. 品質判定

```
✅ 高品質:
- 要件の曖昧さ: ほぼなし（NoteConfig 受け渡し方法のみ実装時判断、TASK-0019.md で明示）
- 入出力定義: 完全（Composable 引数・EditFormState・SendParams・strings.xml すべて型定義済）
- 制約条件: 明確（NFR-101/102/103, REQ-401/402, EDGE-102 すべて方針明示）
- 実装可能性: 確実（依存タスク TASK-0016/0017/0018 完了済、参考実装 LoadingScreen あり）
- 信頼性レベル: 🔵 が多数
```

### 信頼性レベル分布

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 |
|---------|-------|-------|-------|
| 1. 機能概要 | 4 | 0 | 0 |
| 2. 入出力仕様 | 6 | 1 | 0 |
| 3. 制約条件 | 6 | 3 | 0 |
| 4. 使用例 | 4 | 3 | 0 |
| **合計** | **20** | **7** | **0** |

- 🔵 青信号: 20項目 (約74%)
- 🟡 黄信号: 7項目 (約26%)
- 🔴 赤信号: 0項目 (0%)

**品質評価**: 高品質（赤信号ゼロ。黄信号は NFR-102/EDGE-101/EDGE-102 のレイアウト・UX 推測部分に限定され、いずれも TASK-0019.md で実装方針が補強済み）

---

**作成者**: Claude Code (tsumiki:tdd-requirements)
**最終更新**: 2026-05-30
