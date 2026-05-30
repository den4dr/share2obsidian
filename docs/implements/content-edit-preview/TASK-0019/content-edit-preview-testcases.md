# TASK-0019: EditScreen Composable - TDDテストケース定義

**機能名**: content-edit-preview（展開内容の編集・プレビュー機能）
**タスクID**: TASK-0019
**要件名**: content-edit-preview
**フェーズ**: Phase 2 - UI・統合実装
**作成日**: 2026-05-30
**実装対象**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`
**テスト対象**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt`

---

## 【信頼性レベル凡例】

各テストケースについて、元の資料（要件定義、既存実装、ライブラリドキュメント等）との照合状況を以下の信号で示します:

- 🔵 **青信号**: 元の資料（要件定義書・設計文書・既存実装）を参考にしてほぼ推測していない
- 🟡 **黄信号**: 元の資料から妥当な推測
- 🔴 **赤信号**: 元の資料にない推測

---

## 0. テスト方針・前提

### テスト種別

- **Compose UI Test**（`androidTest` 配下）として実装する。
- `androidx.compose.ui.test.junit4.createComposeRule()` を使用して `EditScreen` Composable を単体描画し、UI 状態とコールバック発火を検証する。
- 実機/エミュレータが必要なため、実際のテスト実行は後続ステップ（`connectedAndroidTest`）で対応する。本フェーズではテスト定義とコンパイル（`assembleDebug`）が完了条件。
- 🔵 信頼性レベル: note.md「5. テスト関連情報」、TASK-0019.md「単体テスト要件」より

### NoteConfig 受け渡しの前提（実装時判断事項）

- `buildSendParams(config: NoteConfig)` は config を引数に取る。本テストケースでは「EditScreen が config を引数で受け取り、送信ボタン押下時に `viewModel.buildSendParams(config)` を呼ぶ」方式を前提とする。
- もし実装で「ViewModel に config を保持させる」方式を採用した場合は、テストの `EditScreen(...)` 呼び出し引数を読み替える（テストの検証観点・期待値は変わらない）。
- 🟡 信頼性レベル: requirements.md「NoteConfig の受け渡し（実装時判断事項）」、TASK-0019.md「注意事項」より妥当な推測

### 共通テストフィクスチャ

```kotlin
// 【テストデータ準備】: 全テストで共有する初期 ProcessedContent / NoteConfig / ViewModel を組み立てる
private fun createViewModel(
    title: String? = "テストタイトル",
    body: String = "テスト本文",
): EditScreenViewModel {
    val viewModel = EditScreenViewModel()
    val processed = ProcessedContent(
        body = body,
        title = title,
        contentType = ContentKind.TEXT,
    )
    val config = NoteConfig.fromAppConfig() // vault="testVault", folder="70_clippings", defaultTags=["shared"]
    viewModel.initialize(processed, config)
    return viewModel
}

private val testConfig = NoteConfig.fromAppConfig()
```

- 初期化後の `EditFormState` の値（`initialize()` のマッピング仕様より）:
  - `title`    = `"テストタイトル"`（`processed.title ?: ""`）
  - `body`     = `"テスト本文"`（`processed.body`）
  - `tagsText` = `"shared"`（`config.defaultTags.joinToString(", ")` → `["shared"]` → `"shared"`）
  - `folder`   = `"70_clippings"`（`config.folder`）
- 🔵 信頼性レベル: EditScreenViewModel.kt `initialize()`、AppConfig.kt、NoteConfig.kt より

---

## 1. 正常系テストケース（基本的な動作）

### TC-003-01: タイトルフィールドの初期値表示

- **テスト名**: タイトルフィールドに ProcessedContent.title の値が初期表示される
  - **何をテストするか**: `EditScreen` 描画後、タイトル用 `OutlinedTextField` に ViewModel の `formState.title` が表示されること
  - **期待される動作**: タイトルフィールドに `"テストタイトル"` が表示される
- **入力値**: `createViewModel(title = "テストタイトル")` で初期化した ViewModel を `EditScreen` に渡す
  - **入力データの意味**: 共有元アプリがタイトル（EXTRA_SUBJECT 相当）を提供したケースを代表する
- **期待される結果**: 画面上に `"テストタイトル"` テキストノードが表示される（`onNodeWithText("テストタイトル").assertIsDisplayed()`）
  - **期待結果の理由**: REQ-003 で4フィールド表示が定義され、TC-003-01 でタイトル初期値が `ProcessedContent.title` 由来と規定されているため
- **テストの目的**: フォーム初期値表示（タイトル）の確認
  - **確認ポイント**: `viewModel.formState.collectAsState()` の値が正しくタイトルフィールドにバインドされていること
- 🔵 信頼性レベル: requirements.md TC-003-01、note.md テストケース要件 TC-003-01、EditScreenViewModel.initialize() より

### TC-003-02: 本文フィールドの初期値表示

- **テスト名**: 本文フィールドに ProcessedContent.body の値が初期表示される
  - **何をテストするか**: 本文用 `OutlinedTextField` に `formState.body` が表示されること
  - **期待される動作**: 本文フィールドに `"テスト本文"` が表示される
- **入力値**: `createViewModel(body = "テスト本文")` で初期化した ViewModel
  - **入力データの意味**: 共有テキスト本文が存在する標準ケースを代表する
- **期待される結果**: `onNodeWithText("テスト本文").assertIsDisplayed()` が成功する
  - **期待結果の理由**: REQ-003 で本文フィールド表示が定義され、初期値は `processed.body` をそのまま使う（initialize 仕様）ため
- **テストの目的**: フォーム初期値表示（本文）の確認
  - **確認ポイント**: 複数行フィールド（`minLines = 5`）でも初期値テキストが描画されること
- 🔵 信頼性レベル: note.md テストケース要件 TC-003-02、EditScreenViewModel.initialize() より

### TC-003-03: タグフィールドの初期値表示

- **テスト名**: タグフィールドに defaultTags 由来のカンマ区切り文字列が初期表示される
  - **何をテストするか**: タグ用 `OutlinedTextField` に `formState.tagsText` が表示されること
  - **期待される動作**: タグフィールドに `"shared"` が表示される
- **入力値**: `createViewModel()`（`NoteConfig.fromAppConfig()` → `defaultTags = ["shared"]`）
  - **入力データの意味**: アプリ既定タグ（`AppConfig.OBSIDIAN_TAGS = ["shared"]`）から初期タグが生成されるケースを代表する
- **期待される結果**: `onNodeWithText("shared").assertIsDisplayed()` が成功する
  - **期待結果の理由**: TC-003-03 でタグ初期値が `AppConfig.OBSIDIAN_TAGS` から生成されると規定。`["shared"].joinToString(", ")` = `"shared"` となるため
  - **補足**: ラベル `"タグ（カンマ区切り）"` とは別ノードであり、入力値 `"shared"` を検証対象とする
- **テストの目的**: フォーム初期値表示（タグ）の確認
  - **確認ポイント**: `List<String> → カンマ区切り文字列` の変換結果が表示されること
- 🔵 信頼性レベル: requirements.md TC-003-03、AppConfig.kt、EditScreenViewModel.initialize() より

### TC-003-04: フォルダフィールドの初期値表示

- **テスト名**: フォルダフィールドに NoteConfig.folder の値が初期表示される
  - **何をテストするか**: フォルダ用 `OutlinedTextField` に `formState.folder` が表示されること
  - **期待される動作**: フォルダフィールドに `"70_clippings"` が表示される
- **入力値**: `createViewModel()`（`NoteConfig.fromAppConfig()` → `folder = "70_clippings"`）
  - **入力データの意味**: アプリ既定フォルダ（`AppConfig.OBSIDIAN_FOLDER = "70_clippings"`）が初期保存先になるケースを代表する
- **期待される結果**: `onNodeWithText("70_clippings").assertIsDisplayed()` が成功する
  - **期待結果の理由**: TC-003-04 でフォルダ初期値が `AppConfig.OBSIDIAN_FOLDER` と規定されているため
- **テストの目的**: フォーム初期値表示（フォルダ）の確認
  - **確認ポイント**: `config.folder` が正しくフォルダフィールドにバインドされていること
- 🔵 信頼性レベル: requirements.md TC-003-04、AppConfig.kt、EditScreenViewModel.initialize() より

### TC-LABEL-01: 4フィールドのラベルとボタンが表示される

- **テスト名**: 全フィールドラベルと2ボタンのテキストが strings.xml から表示される
  - **何をテストするか**: `stringResource` で取得したラベル・ボタンテキストが画面に存在すること
  - **期待される動作**: `"タイトル"`, `"本文"`, `"タグ（カンマ区切り）"`, `"フォルダ"`, `"送信"`, `"キャンセル"` が表示される
- **入力値**: `createViewModel()`
  - **入力データの意味**: NFR-103（UI 文字列を strings.xml から取得）が満たされていることを代表的に検証する
- **期待される結果**: 上記6つのテキストノードがそれぞれ表示される（`assertIsDisplayed()` または `assertExists()`）
  - **期待結果の理由**: REQ-003（4フィールド）・REQ-004（2ボタン）・NFR-103（文字列リソース化）より
- **テストの目的**: UI 要素の存在確認（ラベル・ボタン）と文字列リソース利用の確認
  - **確認ポイント**: ハードコードではなく `R.string.label_*` / `R.string.button_*` が使われていること（表示テキストで間接確認）
- 🔵 信頼性レベル: strings.xml、requirements.md「UI 文字列リソース」、NFR-103 より

### TC-101-01: 送信ボタンで onSend が呼ばれる

- **テスト名**: 送信ボタンタップで onSend コールバックが SendParams を伴って呼ばれる
  - **何をテストするか**: 送信ボタン押下時に `onSend(viewModel.buildSendParams(config))` が実行されること
  - **期待される動作**: `onSend` が1回呼ばれ、渡された `SendParams` がフォーム状態を反映している
- **入力値**:
  - `createViewModel(title = "テストタイトル", body = "テスト本文")`
  - `onSend = { params -> captured = params; callCount++ }`、`onCancel = {}`
  - 送信ボタン（`"送信"`）を `performClick()`
  - **入力データの意味**: ユーザーが内容確認後に送信する標準フローを代表する
- **期待される結果**:
  - `onSend` の呼び出し回数 = 1
  - `captured.title == "テストタイトル"`、`captured.body == "テスト本文"`、`captured.tags == listOf("shared")`、`captured.config == testConfig`
  - **期待結果の理由**: REQ-101・TC-101-01 で送信時に編集後内容で onSend が呼ばれると規定。SendParams 生成ロジックは ViewModel 側（検証済）だが、EditScreen が正しく結線していることを確認する
- **テストの目的**: 送信コールバック結線の確認
  - **確認ポイント**: ボタン押下が `onSend` に正しく伝播し、`buildSendParams` の戻り値が渡されること
- 🔵 信頼性レベル: requirements.md TC-101-01、TASK-0019.md テストケース2、EditScreenViewModel.buildSendParams() より

### TC-201-01: キャンセルボタンで onCancel が呼ばれる

- **テスト名**: キャンセルボタンタップで onCancel コールバックが呼ばれ onSend は呼ばれない
  - **何をテストするか**: キャンセルボタン押下時に `onCancel()` のみが実行されること
  - **期待される動作**: `onCancel` が1回呼ばれ、`onSend` は呼ばれない（Obsidian 未起動相当）
- **入力値**:
  - `createViewModel()`
  - `onCancel = { cancelCount++ }`、`onSend = { sendCount++ }`
  - キャンセルボタン（`"キャンセル"`）を `performClick()`
  - **入力データの意味**: ユーザーが送信を取りやめるフローを代表する
- **期待される結果**:
  - `cancelCount == 1`
  - `sendCount == 0`
  - **期待結果の理由**: REQ-201・TC-201-01 でキャンセル時は Obsidian 起動せず（onSend 不発）に終了すると規定。startActivity 相当の onSend が呼ばれないことを保証する
- **テストの目的**: キャンセルコールバック結線の確認
  - **確認ポイント**: キャンセルが送信と排他的に動作すること（onSend 非発火）
- 🔵 信頼性レベル: requirements.md TC-201-01、TASK-0019.md テストケース3より

### TC-FIELD-EDIT-01: フィールド編集が送信値に反映される

- **テスト名**: タイトルフィールドを編集してから送信すると編集後の値が onSend に渡る
  - **何をテストするか**: `OutlinedTextField.onValueChange` → `viewModel.updateTitle()` → `buildSendParams` の経路が機能すること
  - **期待される動作**: 編集後のタイトルで `SendParams` が生成され onSend に渡る
- **入力値**:
  - `createViewModel(title = "テストタイトル")`
  - タイトルフィールドに対し `performTextReplacement("編集後タイトル")`（または既存クリア後 `performTextInput`）
  - 送信ボタンを `performClick()`
  - **入力データの意味**: ユーザーが送信前に内容を修正する本機能の中核ユースケースを代表する
- **期待される結果**: `captured.title == "編集後タイトル"`
  - **期待結果の理由**: 本機能の目的（送信前に内容を確認・修正できる）を満たすには、UI 入力が ViewModel 状態へ反映され送信値に伝わる必要があるため
- **テストの目的**: 双方向データフロー（UI入力 → StateFlow → 送信値）の確認
  - **確認ポイント**: `onValueChange` が `update*` を呼び、Recomposition 後の状態が送信に反映されること
- 🟡 信頼性レベル: requirements.md「入出力の関係性・データフロー」より妥当な推測（個別 TC 番号なし）

---

## 2. 異常系テストケース（エラーハンドリング）

> 注: EditScreen は「コールバックを呼ぶ」までが責務であり、Obsidian 未インストール等のエラー処理は MainActivity 側の責務（requirements.md「エラーケース」）。そのため EditScreen の異常系は「空入力の許容」と「責務外動作を行わない」ことの検証が中心となる。

### TC-EDGE-001-01: 空タイトルで送信しても onSend が呼ばれる（title=null 許容）

- **テスト名**: タイトル空欄で送信ボタンを押しても送信できる（EDGE-001）
  - **エラーケースの概要**: 必須入力に見えるタイトルが空のまま送信される異常入力ケース
  - **エラー処理の重要性**: タイトルなしノートを許容する仕様のため、空入力でブロックしてはならない
- **入力値**:
  - `createViewModel(title = null)`（→ initialize で `title = ""`）
  - 送信ボタンを `performClick()`
  - **不正な理由**: 一般的フォームではタイトル空欄を弾くことがあるが、本機能では許容仕様（EDGE-001）
  - **実際の発生シナリオ**: 共有元アプリが EXTRA_SUBJECT を提供しない（タイトル null）まま送信するケース
- **期待される結果**:
  - `onSend` が1回呼ばれる
  - `captured.title == null`（`ifBlank { null }` により null 変換、ViewModel 担当）
  - **エラーメッセージの内容**: エラーは発生しない（許容仕様のため正常完了）
  - **システムの安全性**: 空タイトルでもクラッシュせず onSend が正常に呼ばれる
- **テストの目的**: 空タイトル許容（EDGE-001）の確認
  - **品質保証の観点**: 仕様上許容すべき入力をブロックしていないことを保証する
- 🔵 信頼性レベル: requirements.md EDGE-001、EditScreenViewModel.buildSendParams() `ifBlank { null }` より

### TC-EDGE-002-01: 空本文で送信しても onSend が呼ばれる

- **テスト名**: 本文空欄で送信ボタンを押しても送信できる（EDGE-002）
  - **エラーケースの概要**: 本文が空のまま送信される異常入力ケース
  - **エラー処理の重要性**: 空ノート作成を許容する仕様のため、空本文でブロックしてはならない
- **入力値**:
  - `createViewModel(body = "")`
  - 送信ボタンを `performClick()`
  - **不正な理由**: 内容が空のノートは無意味に見えるが、本機能では許容仕様（EDGE-002）
  - **実際の発生シナリオ**: ユーザーがタイトルのみのメモを作成する／本文を消して送信するケース
- **期待される結果**:
  - `onSend` が1回呼ばれる
  - `captured.body == ""`
  - **システムの安全性**: 空本文でもクラッシュせず onSend が正常に呼ばれる
- **テストの目的**: 空本文許容（EDGE-002）の確認
  - **品質保証の観点**: 空文字列を不正扱いせず送信フローを継続できることを保証する
- 🔵 信頼性レベル: requirements.md EDGE-002、EditFormState/SendParams の空文字許容定義より

### TC-EDGE-003-01: タグ空欄/カンマのみで送信すると tags が空リストになる

- **テスト名**: タグを空欄またはカンマのみにして送信すると tags=emptyList で onSend が呼ばれる（EDGE-003）
  - **エラーケースの概要**: タグ入力が実質的に空（空文字 or `","` のみ）の異常入力ケース
  - **エラー処理の重要性**: 空タグ／カンマのみ入力で不正なタグ（空文字要素）を生成してはならない
- **入力値**:
  - `createViewModel()` 後、タグフィールドを `performTextReplacement(", ,")`（または空文字に置換）
  - 送信ボタンを `performClick()`
  - **不正な理由**: `","` のみはタグとして無効（trim 後に空文字になる要素）
  - **実際の発生シナリオ**: ユーザーが既定タグを消す／区切り文字だけ残すケース
- **期待される結果**:
  - `onSend` が1回呼ばれる
  - `captured.tags == emptyList<String>()`（`parseTagsText` の空文字フィルタ、ViewModel 担当）
  - **システムの安全性**: 空タグ要素を含まない正規化済みリストが渡る
- **テストの目的**: 空タグ許容・正規化（EDGE-003）の確認
  - **品質保証の観点**: カンマのみ入力でも空文字タグを生成せず送信できることを保証する
- 🔵 信頼性レベル: requirements.md EDGE-003、parseTagsText() の filter 仕様より

### TC-NOSEND-ON-CANCEL-01: キャンセル時に送信パラメータ生成・Obsidian 起動相当が行われない

- **テスト名**: キャンセルではフォーム値に関わらず onSend が一度も呼ばれない
  - **エラーケースの概要**: キャンセル経路で誤って送信処理が走る回帰リスク
  - **エラー処理の重要性**: キャンセルは「Obsidian を起動しない」ことが要件（REQ-201）のため、送信副作用を厳禁とする
- **入力値**:
  - `createViewModel(title = "送信されてはいけない")`
  - キャンセルボタンを `performClick()`
  - **不正な理由**: キャンセル時に onSend が呼ばれるのは要件違反
  - **実際の発生シナリオ**: ボタン結線ミス・共通ハンドラ誤用による回帰
- **期待される結果**: `sendCount == 0`、`cancelCount == 1`
  - **システムの安全性**: キャンセルが送信副作用を一切伴わない
- **テストの目的**: キャンセルの副作用なし（REQ-201）の確認
  - **品質保証の観点**: 送信/キャンセルの責務分離を回帰から守る
- 🟡 信頼性レベル: requirements.md REQ-201・TC-201-01 から妥当な推測（TC-201-01 を副作用観点で補強）

---

## 3. 境界値・エッジケーステストケース（EDGE / 固定表示）

### TC-EDGE-102-01: バックボタン押下で onCancel が呼ばれる（BackHandler）

- **テスト名**: Android バックボタン押下がキャンセルと同等に onCancel を呼ぶ（EDGE-102）
  - **境界値の意味**: ボタン UI ではなくシステムバックジェスチャという別経路の終了操作であり、UX 一貫性の境界となる
  - **境界値での動作保証**: バックボタンとキャンセルボタンが同一の終了動作になること
- **入力値**:
  - `createViewModel()`
  - バックボタンをディスパッチする（`activity.onBackPressedDispatcher.onBackPressed()` 相当、または `composeTestRule` 経由のバックイベント）
  - **境界値選択の根拠**: `BackHandler { onCancel() }` の発火経路を直接検証するため
  - **実際の使用場面**: ユーザーが編集中にシステムの戻る操作で離脱するケース
- **期待される結果**:
  - `onCancel` が1回呼ばれる
  - `onSend` は呼ばれない
  - **境界での正確性**: バックボタンがキャンセルボタンと等価に振る舞う
  - **一貫した動作**: ボタン経由・バック経由のいずれでも onCancel に収束する
- **テストの目的**: バックボタン対応（EDGE-102）の確認
  - **堅牢性の確認**: システム戻る操作で意図せず送信されたり、画面が残留したりしないこと
- **実装メモ**: `createComposeRule()` 単体ではバックディスパッチが扱いにくいため、`createAndroidComposeRule<ComponentActivity>()` を使い `composeTestRule.activity.onBackPressedDispatcher.onBackPressed()` を `runOnUiThread` で実行する方式を想定する。
- 🟡 信頼性レベル: requirements.md EDGE-102（信頼性🟡）、note.md BackHandler 実装方針より妥当な推測

### TC-NFR-102-01: 送信・キャンセルボタンが常に表示される（下部固定表示）

- **テスト名**: 本文に長文を入力してフィールドをスクロールさせてもボタンが表示され続ける（NFR-102）
  - **境界値の意味**: コンテンツ量がビューポートを超える「スクロール発生」境界での固定表示の保証
  - **境界値での動作保証**: スクロール有無に関わらずボタンが画面に存在すること
- **入力値**:
  - `createViewModel(body = "長文".repeat(500))`（画面高を超える本文）
  - 必要に応じてフィールド領域を `performScrollTo()` / スワイプでスクロール
  - **境界値選択の根拠**: `Scaffold.bottomBar` 固定とフィールド `verticalScroll` の分離が機能するかを最も厳しく問う条件
  - **実際の使用場面**: 長いクリップ本文を編集する実運用ケース
- **期待される結果**:
  - スクロール操作の前後いずれでも `"送信"` と `"キャンセル"` が `assertIsDisplayed()` で表示確認できる
  - **境界での正確性**: ボタンが bottomBar に固定され、スクロール対象外であること
  - **一貫した動作**: 短文時・長文時でボタン表示が一貫すること
- **テストの目的**: ボタン下部固定表示（NFR-102）の確認
  - **堅牢性の確認**: 長文編集時でも送信/キャンセルへ常時アクセスできること
- **実装メモ（簡易版）**: スクロール検証が UI Test 上で不安定な場合は、最低限「初期表示時に `"送信"`・`"キャンセル"` が `assertIsDisplayed()`」を検証する縮退版（note.md の「ボタンが画面下部に固定表示される」確認）でも可。
- 🟡 信頼性レベル: requirements.md NFR-102（信頼性🟡）、note.md ボタン固定表示パターンより妥当な推測

### TC-EDGE-101-01: 状態保持（参考・ViewModel 担当のため EditScreen 単体では限定検証）

- **テスト名**: 同一 ViewModel で再描画してもフォーム値が保持される（EDGE-101 の EditScreen 側確認）
  - **境界値の意味**: 画面回転（Activity 再作成）に相当する「再 setContent」境界での状態保持
  - **境界値での動作保証**: EditScreen が状態を持たず、ViewModel から常に最新値を表示すること
- **入力値**:
  - `createViewModel(title = "保持タイトル")` を1度生成
  - 同じ viewModel インスタンスで `EditScreen` を描画 → タイトル編集 → （再 setContent をシミュレートして）再描画
  - **境界値選択の根拠**: EDGE-101 の状態保持責務は ViewModel にあるため、EditScreen は「ViewModel の値を表示するだけ」を確認する
  - **実際の使用場面**: 編集途中での画面回転
- **期待される結果**: 再描画後もタイトルフィールドに編集後の値が表示される
  - **境界での正確性**: EditScreen が独自状態を持たないこと（ViewModel 由来表示）
- **テストの目的**: EDGE-101 における EditScreen の無状態性の確認
  - **堅牢性の確認**: UI 層が状態を二重管理しないこと
- **備考**: 真の Activity 再作成テスト（`recreate()`）は androidTest で別途扱う。本ケースは EditScreen の無状態設計の確認に限定する。**優先度: 低（任意）**。実装難度が高い場合は省略可。
- 🟡 信頼性レベル: requirements.md EDGE-101（ViewModel 担当・信頼性🟡）より妥当な推測（参考ケース）

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 本プロジェクトの実装言語であり、Jetpack Compose / AndroidX ViewModel が Kotlin 前提のため
  - **テストに適した機能**: ラムダによるコールバックモック（`onSend = { ... }`）、`mutableListOf` での呼び出し記録、null 安全による期待値検証が容易
- **テストフレームワーク**: JUnit 4 + Compose UI Test（`androidx.compose.ui.test.junit4`）
  - **フレームワーク選択の理由**: note.md の技術スタックで指定。`createComposeRule()` / `createAndroidComposeRule()` で Composable を単体描画でき、`onNodeWithText` / `performClick` / `assertIsDisplayed` で UI 検証が可能
  - **テスト実行環境**: `app/src/androidTest` 配下に配置し、`connectedAndroidTest` でデバイス/エミュレータ実行。本フェーズでは定義とコンパイル（`assembleDebug`）まで
  - **テストランナー**: `@RunWith(AndroidJUnit4::class)`
- 🔵 信頼性レベル: note.md「1. 技術スタック」「5. テスト関連情報」より

---

## 5. テストケース実装時の日本語コメント指針

各テストケース実装時には以下の3点セット＋AAA コメントを必ず付与する。

### テストケース開始時のコメント例

```kotlin
@Test
fun `TC-101-01 送信ボタンで onSend が呼ばれる`() {
    // 【テスト目的】: 送信ボタンタップで onSend コールバックが SendParams を伴って呼ばれることを確認する
    // 【テスト内容】: EditScreen を描画し、送信ボタンを performClick して onSend の発火と引数を検証する
    // 【期待される動作】: onSend が1回呼ばれ、渡された SendParams がフォーム状態を反映する
    // 🔵 信頼性レベル: requirements.md TC-101-01 より
```

### Given（準備フェーズ）のコメント例

```kotlin
    // 【テストデータ準備】: タイトル/本文を持つ ProcessedContent で ViewModel を初期化する理由 = 送信値の検証に具体値が必要なため
    // 【初期条件設定】: onSend の呼び出し回数と引数を記録するキャプチャ変数を用意する
    // 【前提条件確認】: viewModel.initialize() 済みで formState が初期値を保持していること
    val captured = mutableListOf<SendParams>()
    val viewModel = createViewModel(title = "テストタイトル", body = "テスト本文")
    composeTestRule.setContent {
        EditScreen(viewModel = viewModel, config = testConfig, onSend = { captured.add(it) }, onCancel = {})
    }
```

### When（実行フェーズ）のコメント例

```kotlin
    // 【実際の処理実行】: 送信ボタン（"送信"）を performClick で押下する
    // 【処理内容】: onClick → onSend(viewModel.buildSendParams(config)) が実行される
    // 【実行タイミング】: フォーム表示直後（初期値のまま送信）
    composeTestRule.onNodeWithText("送信").performClick()
```

### Then（検証フェーズ）のコメント例

```kotlin
    // 【結果検証】: onSend が1回だけ呼ばれ、引数 SendParams が初期フォーム値を反映していることを検証する
    // 【期待値確認】: title/body/tags/config が初期化値と一致する
    // 【品質保証】: 送信ボタンとコールバックの結線が正しいことを保証する
    assertEquals(1, captured.size)                       // 【検証項目】: onSend が1回呼ばれた 🔵
    assertEquals("テストタイトル", captured[0].title)      // 【検証項目】: タイトルが送信値に反映 🔵
    assertEquals("テスト本文", captured[0].body)          // 【検証項目】: 本文が送信値に反映 🔵
    assertEquals(listOf("shared"), captured[0].tags)     // 【検証項目】: タグがパース済みで反映 🔵
```

### セットアップ・クリーンアップのコメント例

```kotlin
@get:Rule
val composeTestRule = createAndroidComposeRule<ComponentActivity>()
// 【テスト前準備】: 各テストごとに新しい Compose 描画環境を用意する（createAndroidComposeRule はバックディスパッチ検証に必要）
// 【環境初期化】: setContent 前の状態をクリーンに保ち、テスト間の状態リークを防ぐ
```

---

## 6. テストケース一覧（実装対応表）

| # | テストID | テスト名 | 分類 | 対応要件/AC | 優先度 | 信頼性 |
|---|---------|---------|------|------------|--------|--------|
| 1 | TC-003-01 | タイトル初期値表示 | 正常系 | REQ-003 / TC-003-01 | 高 | 🔵 |
| 2 | TC-003-02 | 本文初期値表示 | 正常系 | REQ-003 / TC-003-02 | 高 | 🔵 |
| 3 | TC-003-03 | タグ初期値表示 | 正常系 | REQ-003 / TC-003-03 | 高 | 🔵 |
| 4 | TC-003-04 | フォルダ初期値表示 | 正常系 | REQ-003 / TC-003-04 | 高 | 🔵 |
| 5 | TC-LABEL-01 | ラベル・ボタン表示 | 正常系 | REQ-003,004 / NFR-103 | 高 | 🔵 |
| 6 | TC-101-01 | 送信ボタンで onSend | 正常系 | REQ-101 / TC-101-01 | 高 | 🔵 |
| 7 | TC-201-01 | キャンセルで onCancel | 正常系 | REQ-201 / TC-201-01 | 高 | 🔵 |
| 8 | TC-FIELD-EDIT-01 | 編集値が送信に反映 | 正常系 | REQ-101（データフロー） | 中 | 🟡 |
| 9 | TC-EDGE-001-01 | 空タイトル送信 | 異常系 | EDGE-001 | 中 | 🔵 |
| 10 | TC-EDGE-002-01 | 空本文送信 | 異常系 | EDGE-002 | 中 | 🔵 |
| 11 | TC-EDGE-003-01 | 空タグ/カンマのみ送信 | 異常系 | EDGE-003 | 中 | 🔵 |
| 12 | TC-NOSEND-ON-CANCEL-01 | キャンセル時 onSend 不発 | 異常系 | REQ-201 | 中 | 🟡 |
| 13 | TC-EDGE-102-01 | バックボタンで onCancel | 境界/エッジ | EDGE-102 / TC-EDGE-102-01 | 高 | 🟡 |
| 14 | TC-NFR-102-01 | ボタン下部固定表示 | 境界/エッジ | NFR-102 | 中 | 🟡 |
| 15 | TC-EDGE-101-01 | 状態保持（無状態設計） | 境界/エッジ | EDGE-101 | 低(任意) | 🟡 |

**必須（note.md テストケース要件8件に対応）**: #1, #2, #3, #4, #6, #7, #13, #14
**追加で網羅性を高める推奨**: #5, #8, #9, #10, #11, #12
**任意（高難度・省略可）**: #15

---

## 7. 要件定義との対応関係

- **参照した機能概要**: requirements.md「1. 機能の概要」（4フィールド + 2ボタン、BackHandler、ボタン下部固定）
- **参照した入力・出力仕様**: requirements.md「2. 入力・出力の仕様」（Composable 引数 viewModel/onSend/onCancel、EditFormState、SendParams、strings.xml リソース）
- **参照した制約条件**: requirements.md「3. 制約条件」（NFR-101/102/103, REQ-201/401/402, EDGE-101/102）
- **参照した使用例**: requirements.md「4. 想定される使用例」（基本フロー、データフロー、エッジケース EDGE-001〜102）
- **参照した受け入れ基準**: requirements.md「6. テストケース概要」、acceptance-criteria 由来の TC-003-01/03/04, TC-101-01, TC-201-01, TC-EDGE-102-01
- **参照した既存実装**: EditScreenViewModel.kt（formState/update*/buildSendParams）、EditFormState.kt（parseTagsText）、SendParams.kt、NoteConfig.kt（fromAppConfig）、AppConfig.kt（vault/folder/tags 定数）、strings.xml

---

## 8. 品質判定

```
✅ 高品質:
- テストケース分類: 正常系(8) / 異常系(4) / 境界・エッジ(3) を網羅
- 期待値定義: 各ケースで具体的な期待値（"テストタイトル", "shared", "70_clippings", emptyList, callCount==1 等）を明記
- 技術選択: Kotlin + JUnit4 + Compose UI Test（createAndroidComposeRule）で確定
- 実装可能性: 依存タスク TASK-0016/0017/0018 実装済、参考 LoadingScreen あり。既存シグネチャと整合
- 信頼性レベル: 🔵 9件 / 🟡 6件 / 🔴 0件（赤信号ゼロ）
```

### 信頼性レベル分布

| 信号 | 件数 | 該当テストID |
|------|------|------------|
| 🔵 青 | 9 | TC-003-01〜04, TC-LABEL-01, TC-101-01, TC-201-01, TC-EDGE-001-01, TC-EDGE-002-01, TC-EDGE-003-01 |
| 🟡 黄 | 6 | TC-FIELD-EDIT-01, TC-NOSEND-ON-CANCEL-01, TC-EDGE-102-01, TC-NFR-102-01, TC-EDGE-101-01 |
| 🔴 赤 | 0 | （なし） |

> 黄信号は主に Compose UI Test 上の操作手段（バックディスパッチ・スクロール検証・再描画）の技術的実現方法に関する推測であり、検証する仕様自体は要件定義に明記されている。

---

**作成者**: Claude Code (tsumiki:tdd-testcases)
**最終更新**: 2026-05-30
