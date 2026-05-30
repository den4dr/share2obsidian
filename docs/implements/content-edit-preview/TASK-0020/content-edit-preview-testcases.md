# TASK-0020 MainActivity フロー変更 テストケース定義書

**機能名**: content-edit-preview（展開内容の編集・プレビュー機能）
**タスクID**: TASK-0020
**要件名**: content-edit-preview
**フェーズ**: Phase 2 - UI・統合実装
**作成日**: 2026-05-30
**出力ファイル**: `docs/implements/content-edit-preview/TASK-0020/content-edit-preview-testcases.md`

---

## 信頼性レベル凡例

- 🔵 **青信号**: 元の資料（要件定義・タスク定義・既存実装）を参考にしてほぼ推測していない
- 🟡 **黄信号**: 元の資料から妥当な推測の場合
- 🔴 **赤信号**: 元の資料にない推測の場合

---

## 0. テスト戦略・前提

### 0.1 テスト分類とレイヤ

TASK-0020 は `MainActivity` のフロー変更（処理完了後に即時 Obsidian 起動 → `EditScreen` 表示 → 送信/キャンセルコールバック）が対象である。検証は以下の3レイヤに分けて行う。

| レイヤ | テスト種別 | 実行環境 | 配置 | 本書での優先度 |
|--------|-----------|----------|------|---------------|
| L1: ロジック単体（Robolectric） | JVM ユニットテスト | デバイス不要（Robolectric） | `app/src/test/` | **最優先**（CI で実行可能） |
| L2: コンパイル/ビルド検証 | `assembleDebug` | デバイス不要 | - | 高（フロー変更の整合性確認） |
| L3: UI 操作（Compose UI Test） | インストゥルメントテスト | 実機/エミュレータ必須 | `app/src/androidTest/` | 後回し（参考定義のみ） |

- 🔵 **Robolectric 優先方針**: 既存 `MainActivityTest.kt` が Robolectric（`@RunWith(RobolectricTestRunner::class)` + `@Config(sdk=[34])` + `@LooperMode(PAUSED)`）+ `Shadows.shadowOf(activity).nextStartedActivity` で `startActivity` を検証している。本タスクの単体テストも同パターンを踏襲し、デバイスなしで実行する。
- 🔵 **assembleDebug 中心方針**: note.md / requirements.md（§3 テスト制約）に従い、フロー変更の整合性は `mise exec -- ./gradlew assembleDebug` のコンパイル通過で担保する。`EditScreen(viewModel, config, onSend, onCancel)` のシグネチャ一致が主眼。
- 🟡 **UI 操作テストは後回し**: 送信ボタン/キャンセルボタンの実タップ検証は Compose UI Test（`createAndroidComposeRule` + `onNodeWithText`）が必要で実機依存のため、本書では androidTest として参考定義に留める。

### 0.2 重要な実装上の注意（テスト設計に影響）

- 🔵 **EditScreen シグネチャ**: `EditScreen(viewModel, config, onSend, onCancel)` で `config: NoteConfig` が**第2引数**（`EditScreen.kt:37-43` で確認）。note.md/TASK-0020.md の一部コード例では `config` を省略しているが、実装では必須。MainActivity の `setContent` で `config = config` を渡す必要がある。
- 🔵 **URI 構築主体**: `NoteComposer.buildFrontmatter` / `buildUri` の呼び出しは `MainActivity` の `onSend` コールバック内で行う（requirements.md §2.3 で確定）。
- 🔵 **Robolectric での Looper 進行**: `lifecycleScope.launch`（Dispatchers.Main）内の処理は `Shadows.shadowOf(Looper.getMainLooper()).idle()` を呼ばないと実行されない（既存 `MainActivityTest` 参照）。
- 🟡 **送信/キャンセルの直接検証**: Robolectric で `EditScreen` の Compose ボタンをタップするのは難しいため、L1 ではコールバックロジックそのものの検証（`NoteComposer` 経由 URI 構築・`finish()` 挙動）に分解してテストする。

---

## 1. 正常系テストケース（基本的な動作）

### TC-0020-N01: テキスト共有 → EditScreen 表示まで startActivity が呼ばれない

- **テスト名**: テキスト共有インテントで起動した直後は Obsidian を起動しない
  - **何をテストするか**: 変更後フローで「処理完了直後の即時 Obsidian 起動」が撤廃され、EditScreen 表示段階では `startActivity(ACTION_VIEW, obsidian://...)` が呼ばれないこと
  - **期待される動作**: `onCreate` → コンテンツ処理 → `viewModel.initialize` → `setContent { EditScreen(...) }` まで進むが、ユーザー操作（送信）がないため Obsidian は起動されない
- **入力値**: `Intent(ACTION_SEND)` / `type = "text/plain"` / `EXTRA_TEXT = "テスト共有テキスト"`
  - **入力データの意味**: 最も基本的なテキスト共有シナリオ（フロー1 / REQ-001）。既存 `MainActivityTest` と同一の入力を流用し、変更前後の挙動差を明確化する
- **期待される結果**: `Shadows.shadowOf(activity).nextStartedActivity` が `null`（Obsidian へ未起動）。Activity は `isFinishing == false`（EditScreen 表示中で終了していない）
  - **期待結果の理由**: 変更前は即起動 → finish していたが、変更後はユーザー操作待ちで EditScreen を表示し続けるため、未操作時点では起動も finish もしない（TASK-0020.md 変更後フロー）
- **テストの目的**: 「即時起動の撤廃」という本タスクの中核変更を検証する
  - **確認ポイント**: 変更前 `MainActivityTest` の `text plain intent launches obsidian uri`（起動を期待）が、変更後は起動しないこと。既存テストの**期待値反転**が必要になる点に注意
- 🔵 *TASK-0020.md「変更後フロー」・既存 MainActivityTest・dataflow.md フロー1 より*

### TC-0020-N02: 送信パラメータから正しい Obsidian URI が構築される（onSend ロジック）

- **テスト名**: SendParams から NoteComposer 経由で正しい obsidian://new URI が生成される
  - **何をテストするか**: `onSend` コールバックの中核ロジック（`NoteComposer.buildFrontmatter` → `NoteComposer.buildUri`）が、編集後の値から期待どおりの URI を生成すること
  - **期待される動作**: scheme=`obsidian`、host=`new`、クエリに `content` / `title=テスト` / `vault=testVault` / `folder=70_clippings` を含む URI が生成される
- **入力値**: `SendParams(title="テスト", body="本文", tags=listOf("shared","web"), config=NoteConfig(vault="testVault", folder="70_clippings", defaultTags=listOf("shared")))`
  - **入力データの意味**: TC-101-01 / note.md テストケース2 の代表値。タイトル・本文・複数タグ・フォルダがすべて埋まった典型送信ケース
- **期待される結果**:
  - `uri.scheme == "obsidian"`、`uri.host == "new"`
  - `uri.getQueryParameter("title") == "テスト"`
  - `uri.getQueryParameter("vault") == "testVault"`
  - `uri.getQueryParameter("folder") == "70_clippings"`
  - `uri.getQueryParameter("content")` が `---\ntitle: \"テスト\"\ntags: [shared, web]\n---\n\n本文` を含む
  - **期待結果の理由**: `NoteComposer.buildFrontmatter`（title 行あり・tags カンマ+スペース区切り）と `buildUri`（NoteConfig の vault/folder 反映）の実装に基づく（NoteComposer.kt:37-79）
- **テストの目的**: 送信時の URI 構築が REQ-101 / NFR-101（URI エンコード委譲）どおりであることを検証
  - **確認ポイント**: MainActivity が生 URI を組み立てず NoteComposer に委譲していること。`Uri.parse` を使うため Robolectric 実行が必要
- 🔵 *TC-101-01・REQ-101・NoteComposer.kt 実装・note.md テストケース2 より*

### TC-0020-N03: キャンセルで startActivity を呼ばずに finish される（onCancel ロジック）

- **テスト名**: キャンセルコールバックは Obsidian を起動せず Activity を終了する
  - **何をテストするか**: `onCancel = { finish() }` の挙動。キャンセル経路では `startActivity` が一切呼ばれず `finish()` のみが実行されること
  - **期待される動作**: `onCancel` 実行後、Activity が `isFinishing == true` となり、`nextStartedActivity` は `null` のまま
- **入力値**: テキスト共有インテントで起動 → EditScreen 表示後に `onCancel` 相当を実行
  - **入力データの意味**: フロー4（キャンセル）/ REQ-201。ユーザーが送信せず離脱するシナリオ
- **期待される結果**: `shadowOf(activity).nextStartedActivity == null` かつ `activity.isFinishing == true`
  - **期待結果の理由**: REQ-201「キャンセルで Obsidian を起動せず終了」に基づく。`onCancel` は `finish()` のみを呼ぶ（TASK-0020.md:99）
- **テストの目的**: キャンセル経路で Obsidian が誤起動しないこと（副作用なし）を保証
  - **確認ポイント**: `startActivity` が呼ばれない点（誤起動の防止）が主眼
- 🔵 *TC-201-01・REQ-201・note.md テストケース1 より*

### TC-0020-N04: 共有対象外 Intent（null）は setContent せず即 finish（リグレッション）

- **テスト名**: ContentTypeDetector が null を返す場合は EditScreen を表示せず即終了する
  - **何をテストするか**: フロー変更後も「共有対象外 Intent → 即 finish」の既存挙動が維持されていること（リグレッション防止）
  - **期待される動作**: `ContentTypeDetector.detect(intent) == null` の場合、`setContent` も `startActivity` も呼ばず `finish(); return`
- **入力値**: `Intent(ACTION_SEND)`（MIME type なし・EXTRA_TEXT なし）
  - **入力データの意味**: 既存 `MainActivityTest` の `null content type does not start obsidian` と同一。判定不能 Intent
- **期待される結果**: `shadowOf(activity).nextStartedActivity == null` かつ `activity.isFinishing == true`
  - **期待結果の理由**: requirements.md §4.3「共有対象外 Intent → 即 finish」。フロー変更で壊れていないことを確認
- **テストの目的**: フロー変更による既存正常系のデグレード有無を検証
  - **確認ポイント**: 既存テストと同じ期待値（変更不要）であること
- 🔵 *requirements.md §4.3・既存 MainActivityTest より*

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-0020-E01: Obsidian 未インストール時にトースト表示＋finish（onSend 例外処理）

- **テスト名**: startActivity が ActivityNotFoundException を投げた場合にトーストを表示して終了する
  - **エラーケースの概要**: Obsidian 未インストール環境で送信した際、`startActivity` が `ActivityNotFoundException` を投げる
  - **エラー処理の重要性**: 例外を握りつぶさずユーザーに状況を通知し、かつクラッシュさせず安全に終了する必要がある（REQ-401）
- **入力値**: 有効な `SendParams`（TC-0020-N02 と同等）で送信。ただし `obsidian://` を解決できる Activity が登録されていない環境
  - **不正な理由**: 入力自体は正常だが、解決先 Activity が存在しない実行環境がエラー要因
  - **実際の発生シナリオ**: Obsidian アプリが未インストールのデバイスで送信ボタンを押した場合
- **期待される結果**:
  - `ActivityNotFoundException` がキャッチされ、`R.string.error_obsidian_not_installed`（"Obsidian がインストールされていません"）のトーストが `Toast.LENGTH_LONG` で表示される
  - その後 `finish()` が呼ばれ `activity.isFinishing == true`
  - アプリがクラッシュしない
  - **エラーメッセージの内容**: 既存日本語文字列リソース。ユーザーに原因が明確
  - **システムの安全性**: 例外発生後も finish して安全に終了
- **テストの目的**: 既存 REQ-401 エラー処理がフロー変更後も維持されていることを検証
  - **品質保証の観点**: 例外時のクラッシュ防止とユーザー通知。Robolectric では `ShadowToast.getTextOfLatestToast()` で検証可能
- 🔵 *TC-101-E01・REQ-401・note.md テストケース3 より*

### TC-0020-E02: タイトル null の SendParams で title 行を省略した URI が構築される

- **テスト名**: タイトルなし送信時に Frontmatter の title 行が省略され URI の title が空文字になる
  - **エラーケースの概要**: タイトル未入力（空文字/空白のみ）で送信 → ViewModel 側で `title = null` 化済みの SendParams が onSend に渡る（EDGE-001）
  - **エラー処理の重要性**: title が null でも NPE なくクラッシュせず正しい Frontmatter/URI を生成する必要がある
- **入力値**: `SendParams(title=null, body="本文", tags=listOf("shared"), config=...)`
  - **不正な理由**: title が null（タイトルなし）という境界的入力
  - **実際の発生シナリオ**: ユーザーがタイトルフィールドを空にして送信したケース（EDGE-001）
- **期待される結果**:
  - `content` に `title:` 行が含まれない（`---\ntags: [shared]\n---\n\n本文`）
  - `uri.getQueryParameter("title") == ""`（空文字）
  - **エラーメッセージの内容**: エラーではなく正常終了（タイトルなしノート作成は許容）
  - **システムの安全性**: null 安全に処理されクラッシュしない
- **テストの目的**: EDGE-001 のタイトル null 経路が onSend で正しく扱われることを検証
  - **品質保証の観点**: null 入力に対する堅牢性（NoteComposer.kt:40 `title?.let{...} ?: ""`、:73 `title ?: ""`）
- 🔵 *EDGE-001・NoteComposer.kt 実装・requirements.md §4.2 より*

---

## 3. 境界値テストケース（最小値、最大値、null 等）

### TC-0020-B01: 本文空文字の SendParams で空ノート URI が構築される

- **テスト名**: 本文が空文字でもクラッシュせず空ノートの URI が生成される
  - **境界値の意味**: body の最小値（空文字 `""`）。EDGE-002 の「空ノート送信」
  - **境界値での動作保証**: 本文が空でも Frontmatter 構造（`---\n...\n---\n\n`）が崩れず URI 化される
- **入力値**: `SendParams(title="タイトル", body="", tags=listOf("shared"), config=...)`
  - **境界値選択の根拠**: body の下限。EDGE-002 で明示的に許容される入力
  - **実際の使用場面**: タイトル・タグのみで内容のないノートを作成したいケース
- **期待される結果**: `content == "---\ntitle: \"タイトル\"\ntags: [shared]\n---\n\n"`（本文部が空）。`startActivity` が正常に呼ばれる
  - **境界での正確性**: 末尾 `\n\n` の後ろが空文字でも URI 構築が成功する
  - **一貫した動作**: 本文ありケース（TC-0020-N02）と同じ構造で末尾のみ空
- **テストの目的**: EDGE-002 の空本文境界を検証
  - **堅牢性の確認**: 空文字入力でもクラッシュ・URI 破損が起きないこと
- 🔵 *EDGE-002・NoteComposer.kt 実装・requirements.md §4.2 より*

### TC-0020-B02: タグ空リストの SendParams で tags: [] が出力される

- **テスト名**: タグ空リストで送信すると Frontmatter に tags: [] が出力される
  - **境界値の意味**: tags の最小値（`emptyList()`）。EDGE-003 の「タグ空で送信」
  - **境界値での動作保証**: 空リストでも tags フィールドが `[]` として正しく出力される
- **入力値**: `SendParams(title="タイトル", body="本文", tags=emptyList(), config=...)`
  - **境界値選択の根拠**: tags の下限。`tagsText=""` または `",,,"` が `parseTagsText` で `[]` に正規化された結果に相当
  - **実際の使用場面**: タグを付けずにノートを作成するケース
- **期待される結果**: `content` に `tags: []` を含む（`joinToString` 結果が空文字 → `tags: []`）。`startActivity` が正常に呼ばれる
  - **境界での正確性**: `emptyList<String>().joinToString(", ")` が `""` を返し `tags: []` になる
  - **一貫した動作**: タグありケースと同じ位置・形式でタグ行が出力される
- **テストの目的**: EDGE-003 の空タグ境界を検証
  - **堅牢性の確認**: 空リストでも Frontmatter 構造が崩れないこと
- 🔵 *EDGE-003・NoteComposer.kt:45 実装・requirements.md §4.2 より*

### TC-0020-B03: 画面回転（Activity 再作成）後も編集内容が保持される

- **テスト名**: 画面回転で Activity が再作成されても ViewModel の編集内容が保持される
  - **境界値の意味**: Activity ライフサイクルの境界（再作成）。EDGE-101 の画面回転
  - **境界値での動作保証**: `viewModels()` デリゲートで保持された ViewModel に対し、再 `onCreate` 時の `initialize()` が `initialized` フラグで無視され編集内容が上書きされない
- **入力値**: テキスト共有で起動 → `viewModel.updateTitle("編集後")` → 構成変更（再作成）→ 再度 `initialize` が呼ばれる
  - **境界値選択の根拠**: 構成変更は Android で頻発する再作成イベントの代表
  - **実際の使用場面**: ユーザーが編集中に端末を回転させたケース
- **期待される結果**: 再作成後も `formState.value.title == "編集後"` が保持される（`initialized` ガードにより 2 回目の `initialize` が無視）
  - **境界での正確性**: ViewModel スコープが Activity 再作成をまたいで生存
  - **一貫した動作**: EditScreenViewModelTest の TC-002 と同じガードロジックが MainActivity 経由でも機能
  - **補足**: ViewModel 単体の重複初期化防止は `EditScreenViewModelTest.TC-002` で検証済み。本ケースは MainActivity フロー（`viewModels()` 束縛）レベルの統合確認であり、厳密な再作成検証は L3（androidTest / `ActivityScenario.recreate()`）が望ましい
- **テストの目的**: EDGE-101 の状態保持を検証
  - **堅牢性の確認**: 構成変更で編集内容が失われないこと
- 🟡 *EDGE-101・requirements.md §4.2・note.md「EDGE-101 画面回転」より（MainActivity 経由の再作成検証は androidTest 依存のため黄信号）*

### TC-0020-B04: バックボタンがキャンセルと同等に扱われ finish される

- **テスト名**: Android バックボタン押下でキャンセルと同じく Obsidian を起動せず終了する
  - **境界値の意味**: ユーザー操作の境界（明示的キャンセルボタンとバックボタンの等価性）。EDGE-102
  - **境界値での動作保証**: `EditScreen` 内 `BackHandler { onCancel() }` によりバックボタンが `onCancel`（= `finish()`）に接続される
- **入力値**: テキスト共有で起動 → EditScreen 表示 → バックボタン押下
  - **境界値選択の根拠**: バックボタンはキャンセルボタンと並ぶ「離脱」操作の代表
  - **実際の使用場面**: ユーザーが送信せずバックキーで離脱したケース
- **期待される結果**: `startActivity` が呼ばれず（`nextStartedActivity == null`）、`finish()` により `isFinishing == true`
  - **境界での正確性**: バックボタンとキャンセルボタンで挙動が一致
  - **一貫した動作**: TC-0020-N03（キャンセル）と同じ結果
  - **補足**: `BackHandler` の実発火検証は Compose UI Test（L3 / androidTest）が必要。MainActivity 側は `onCancel = { finish() }` のみのため、L1 では onCancel ロジック（TC-0020-N03）で代替検証する
- **テストの目的**: EDGE-102 のバックボタン＝キャンセル等価を検証
  - **堅牢性の確認**: 離脱操作の一貫性
- 🟡 *EDGE-102・EditScreen.kt:47 BackHandler・note.md「EDGE-102 バックボタン」より（実発火は androidTest 依存のため黄信号）*

---

## 4. URL フロー統合テストケース（参考：androidTest 後回し）

### TC-0020-I01: URL 共有で LoadingScreen → EditScreen に遷移する

- **テスト名**: URL 共有インテントで LoadingScreen 表示後に EditScreen へ切り替わる
  - **何をテストするか**: URL タイプ時の `setContent { LoadingScreen() }` → 本文抽出完了後 `setContent { EditScreen(...) }` の画面遷移（REQ-301）
  - **期待される動作**: ローディング画面表示 → WebView 本文抽出 → EditScreen 表示（本文フィールドに抽出テキスト入力済み）
- **入力値**: `Intent(ACTION_SEND)` / `type = "text/plain"` / `EXTRA_TEXT = "https://example.com"`（URL 文字列）
  - **入力データの意味**: フロー2（URL 共有）/ REQ-301。URL 判定される共有
- **期待される結果**: 画面状態が Loading → Edit に遷移し、EditScreen の body フィールドに抽出本文（タイムアウト時は URL 文字列フォールバック）が表示される
  - **期待結果の理由**: REQ-301 / REQ-302 / TC-301-01。dataflow.md フロー2 の遷移仕様
- **テストの目的**: URL フローの LoadingScreen → EditScreen 遷移と初期値反映を検証
  - **確認ポイント**: WebView 抽出が絡むため実機/エミュレータ必須。Compose UI Test（`createAndroidComposeRule` + `onNodeWithText` / `waitUntil`）で実装する
- 🟡 *TC-301-01・REQ-301・REQ-302・note.md テストケース4 より（WebView/Compose 依存のため androidTest・後回し、信頼性は要件記載ベースだが実装手段が実機依存のため黄信号）*

### TC-0020-I02: 送信ボタンの実タップで Obsidian が起動し finish される（参考）

- **テスト名**: EditScreen の送信ボタンタップで onSend が発火し Obsidian 起動 → 終了する
  - **何をテストするか**: `Button(button_send)` タップ → `viewModel.buildSendParams(config)` → `onSend` → `startActivity` → `finish()` のエンドツーエンド
  - **期待される動作**: 送信ボタンタップで Obsidian 起動 Intent が発行され Activity が終了
- **入力値**: テキスト共有で起動 → 各フィールド編集 → 送信ボタン（`stringResource(R.string.button_send)`）タップ
  - **入力データの意味**: フロー3（送信ボタン）のエンドツーエンド検証
- **期待される結果**: `obsidian://new?...` の Intent で `startActivity` が呼ばれ、`finish()` される
  - **期待結果の理由**: REQ-101 / REQ-102。EditScreen の `onClick = { onSend(viewModel.buildSendParams(config)) }`（EditScreen.kt:64）経由
- **テストの目的**: 送信ボタンの実 UI 操作からの一連フローを検証
  - **確認ポイント**: Compose ボタンタップが必要なため androidTest。L1 ではロジック分解（TC-0020-N02）で代替検証済み
- 🟡 *REQ-101・REQ-102・EditScreen.kt:64・dataflow.md フロー3 より（Compose UI 操作依存のため androidTest・後回し）*

### TC-0020-I03: キャンセルボタンの実タップで Obsidian を起動せず finish される（参考）

- **テスト名**: EditScreen のキャンセルボタンタップで onCancel が発火し終了する
  - **何をテストするか**: `OutlinedButton(button_cancel)` タップ → `onCancel` → `finish()`、`startActivity` 未呼び出し
  - **期待される動作**: キャンセルボタンタップで Obsidian を起動せず Activity 終了
- **入力値**: テキスト共有で起動 → キャンセルボタン（`stringResource(R.string.button_cancel)`）タップ
  - **入力データの意味**: フロー4（キャンセル）のエンドツーエンド検証
- **期待される結果**: `startActivity` が呼ばれず `finish()` される
  - **期待結果の理由**: REQ-201。EditScreen の `OutlinedButton(onClick = onCancel)`（EditScreen.kt:57-58）経由
- **テストの目的**: キャンセルボタンの実 UI 操作からの離脱フローを検証
  - **確認ポイント**: androidTest。L1 ではロジック分解（TC-0020-N03）で代替検証済み
- 🟡 *REQ-201・EditScreen.kt:57・dataflow.md フロー4 より（Compose UI 操作依存のため androidTest・後回し）*

---

## 5. ビルド／コンパイル検証ケース

### TC-0020-C01: assembleDebug がエラーなく通過する

- **テスト名**: フロー変更後の MainActivity が assembleDebug でコンパイル通過する
  - **何をテストするか**: `EditScreen(viewModel, config, onSend, onCancel)` のシグネチャ一致、未使用 import（FrontmatterBuilder/ObsidianUriBuilder）の整理、`viewModels()` デリゲートの解決
  - **期待される動作**: `mise exec -- ./gradlew assembleDebug` がエラー・未解決参照なしで成功
- **入力値**: 変更後の `MainActivity.kt`（ビルド対象）
  - **入力データの意味**: requirements.md §3 のテスト制約「単体検証の中心は assembleDebug」
- **期待される結果**: BUILD SUCCESSFUL。`Unresolved reference` / 引数不一致エラーが出ない
  - **期待結果の理由**: TASK-0020.md 完了条件「assembleDebug がエラーなく通過する」
- **テストの目的**: フロー変更の静的整合性（シグネチャ・依存・import）を保証
  - **確認ポイント**: `EditScreen` の第2引数 `config` 渡し漏れ、`androidx.activity.viewModels` import、削除コードの残存 import がないこと
- 🔵 *TASK-0020.md 完了条件・requirements.md §3 テスト制約より*

---

## 6. テストケース一覧（サマリ）

| TC-ID | 分類 | テスト概要 | 実行レイヤ | 信頼性 |
|-------|------|-----------|-----------|--------|
| TC-0020-N01 | 正常系 | テキスト共有直後は Obsidian 未起動（即時起動撤廃） | L1 Robolectric | 🔵 |
| TC-0020-N02 | 正常系 | SendParams → 正しい obsidian URI 構築（onSend） | L1 Robolectric | 🔵 |
| TC-0020-N03 | 正常系 | キャンセルで未起動＋finish（onCancel） | L1 Robolectric | 🔵 |
| TC-0020-N04 | 正常系 | 共有対象外 Intent（null）は即 finish（リグレッション） | L1 Robolectric | 🔵 |
| TC-0020-E01 | 異常系 | Obsidian 未インストール時トースト＋finish | L1 Robolectric | 🔵 |
| TC-0020-E02 | 異常系 | title=null で title 行省略・title 空文字 URI | L1 Robolectric | 🔵 |
| TC-0020-B01 | 境界値 | 本文空文字で空ノート URI 構築（EDGE-002） | L1 Robolectric | 🔵 |
| TC-0020-B02 | 境界値 | タグ空リストで tags: [] 出力（EDGE-003） | L1 Robolectric | 🔵 |
| TC-0020-B03 | 境界値 | 画面回転後も編集内容保持（EDGE-101） | L1/L3 | 🟡 |
| TC-0020-B04 | 境界値 | バックボタン＝キャンセルで未起動＋finish（EDGE-102） | L1/L3 | 🟡 |
| TC-0020-I01 | 統合 | URL: LoadingScreen → EditScreen 遷移 | L3 androidTest | 🟡 |
| TC-0020-I02 | 統合 | 送信ボタン実タップ E2E | L3 androidTest | 🟡 |
| TC-0020-I03 | 統合 | キャンセルボタン実タップ E2E | L3 androidTest | 🟡 |
| TC-0020-C01 | ビルド | assembleDebug コンパイル通過 | L2 build | 🔵 |

- **合計**: 14 ケース（正常系 4・異常系 2・境界値 4・統合 3・ビルド 1）
- **L1（Robolectric, デバイス不要）**: 8 ケース（最優先で実装）
- **L2（build）**: 1 ケース
- **L3（androidTest, 実機必須・後回し）**: 5 ケース（うち B03/B04 は L1 で代替検証あり）

---

## 7. 開発言語・テストフレームワーク

- **プログラミング言語**: Kotlin（2.2.10）
  - **言語選択の理由**: プロジェクト全体が Kotlin で実装されており、`MainActivity` / `EditScreen` / `NoteComposer` もすべて Kotlin。テスト対象と同一言語で記述する
  - **テストに適した機能**: data class の `equals`（`SendParams` / `NoteConfig` 比較）、null 安全（`title: String?` の検証）、文字列テンプレート（Frontmatter 期待値）
- **テストフレームワーク**:
  - **L1 ユニット**: JUnit4 + **Robolectric**（`@RunWith(RobolectricTestRunner::class)` / `@Config(sdk=[34])` / `@LooperMode(PAUSED)`）+ `org.robolectric.Shadows`（`shadowOf(activity).nextStartedActivity`, `ShadowToast`）
  - **L3 UI**: AndroidX Compose UI Test（`createAndroidComposeRule` / `onNodeWithText` / `waitUntil`）+ `ActivityScenario`
  - **フレームワーク選択の理由**: 既存 `MainActivityTest.kt` / `EditScreenViewModelTest.kt` が Robolectric を採用しており、`Uri.parse` や `startActivity` を JVM 上で検証できるため。これにより本タスクの URI 構築・起動有無・トースト・finish 判定をデバイスなしで実行可能
  - **テスト実行環境**: `mise exec -- ./gradlew test`（L1）、`mise exec -- ./gradlew assembleDebug`（L2）、`mise exec -- ./gradlew connectedAndroidTest`（L3・実機/エミュレータ必須）
- 🔵 *既存 MainActivityTest.kt / EditScreenViewModelTest.kt・CLAUDE.md ビルドコマンド・note.md 技術スタックより*

---

## 8. テストケース実装時の日本語コメント指針

各テストの実装時には以下の日本語コメントを必ず含める（既存 `EditScreenViewModelTest` のスタイルに統一）。

### テストケース開始時のコメント

```kotlin
// 【テスト目的】: [このテストで何を確認するかを日本語で明記]
// 【テスト内容】: [具体的にどのような処理をテストするかを説明]
// 【期待される動作】: [正常に動作した場合の結果を説明]
// 🔵🟡🔴 この内容の信頼性レベルを記載
```

### Given（準備フェーズ）のコメント

```kotlin
// 【テストデータ準備】: [なぜこのデータを用意するかの理由]
// 【初期条件設定】: [テスト実行前の状態を説明]
// 【前提条件確認】: [テスト実行に必要な前提条件を明記]
```

### When（実行フェーズ）のコメント

```kotlin
// 【実際の処理実行】: [どの機能/メソッドを呼び出すかを説明]
// 【処理内容】: [実行される処理の内容を日本語で説明]
// 【実行タイミング】: [なぜこのタイミングで実行するかを説明（例: Looper.idle() で coroutine を進める）]
```

### Then（検証フェーズ）のコメント

```kotlin
// 【結果検証】: [何を検証するかを具体的に説明]
// 【期待値確認】: [期待される結果とその理由を説明]
// 【品質保証】: [この検証がシステム品質にどう貢献するかを説明]
```

### 各 assert ステートメントのコメント例

```kotlin
// 【検証項目】: 送信前は Obsidian が起動されていないこと
// 🔵 この内容の信頼性レベルを記載
assertNull("EditScreen 表示中は Obsidian 未起動", shadowOf(activity).nextStartedActivity) // 【確認内容】: 即時起動が撤廃されていることを確認
assertEquals("title クエリが期待値であること", "テスト", uri.getQueryParameter("title")) // 【確認内容】: NoteComposer 経由で title が正しく URI 化されることを確認
```

### Robolectric セットアップの注意コメント

```kotlin
// 【テスト前準備】: Robolectric で Activity を buildActivity().create().start().resume() で起動する
// 【環境初期化】: @LooperMode(PAUSED) のため、lifecycleScope.launch の処理は
//                Shadows.shadowOf(Looper.getMainLooper()).idle() を呼ぶまで実行されない点に注意
```

---

## 9. 要件定義との対応関係

- **参照した機能概要**: requirements.md §1「機能の概要」（即時起動撤廃 → EditScreen 表示・送信/キャンセルコールバック）
- **参照した入力・出力仕様**: requirements.md §2「入力・出力の仕様」（Intent / ProcessedContent / NoteConfig / SendParams → 画面表示 / URI / トースト / finish）
- **参照した制約条件**: requirements.md §3「制約条件」（REQ-401 シングルアクティビティ・REQ-402 既存クラス不変更・EditScreen シグネチャ・viewModels 依存・テスト制約）
- **参照した使用例**: requirements.md §4「想定される使用例」（フロー1〜4・EDGE-001/002/003/101/102・エラーケース）
- **参照した受け入れ基準**: TC-201-01 / TC-101-01 / TC-101-E01 / TC-301-01（requirements.md §5）
- **参照したタスク定義**: TASK-0020.md「単体テスト要件」「統合テスト要件」「完了条件」
- **参照したコンテキストノート**: note.md「テスト要件」テストケース1〜4・「エッジケース（EDGE）対応」
- **参照した既存実装**:
  - `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt`（変更前）
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（シグネチャ `EditScreen(viewModel, config, onSend, onCancel)`）
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（`initialize` / `buildSendParams`）
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/SendParams.kt` / `EditFormState.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/format/NoteComposer.kt` / `NoteConfig.kt`
  - `app/src/test/java/com/den4dr/share2Obsidian/MainActivityTest.kt`（Robolectric パターン）
  - `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt`（コメント・命名スタイル）

---

## 10. 品質判定結果

### 評価項目

| 項目 | 評価 | 根拠 |
|------|------|------|
| テストケース分類 | ✅ 網羅 | 正常系 4・異常系 2・境界値 4・統合 3・ビルド 1 を定義。EDGE-001〜102 / REQ-101/201/301/401/402 / NFR-101 を網羅 |
| 期待値定義 | ✅ 明確 | 各ケースに URI クエリ・Frontmatter 文字列・`isFinishing` / `nextStartedActivity` 等の具体的期待値を記載 |
| 技術選択 | ✅ 確定 | Kotlin + JUnit4 + Robolectric（既存パターン踏襲）/ Compose UI Test（L3）を明示 |
| 実装可能性 | ✅ 確実 | 依存コンポーネント（EditScreen/ViewModel/NoteComposer/NoteConfig/SendParams）実装済み。L1 はデバイスなしで実行可能 |
| 信頼性レベル分布 | ✅ 良好 | 🔵 9 / 🟡 5 / 🔴 0（🟡 は androidTest 依存 or 再作成検証の手段に起因する妥当な推測） |

### 信頼性レベル分布

- 🔵 青信号: 9 ケース（約64%）— 正常系・異常系・空本文/空タグ境界・ビルド
- 🟡 黄信号: 5 ケース（約36%）— 画面回転/バックボタンの再作成・実発火検証、URL 遷移、ボタン実タップ E2E（いずれも androidTest 依存）
- 🔴 赤信号: 0 ケース

### 総合判定

**✅ 高品質**

- テストケース分類: 正常系・異常系・境界値・統合・ビルドを網羅
- 期待値定義: すべて具体的（URI クエリ・Frontmatter 文字列・Activity 状態）
- 技術選択: 既存 Robolectric/Compose UI Test パターンに整合
- 実装可能性: L1（8 ケース）はデバイスなしで即実装可能、L3（5 ケース）は実機環境で後追い実装

---

## 11. 次のステップ

次のお勧めステップ: `/tsumiki:tdd-red content-edit-preview 0020` で Red フェーズ（失敗テスト作成）を開始します。

- まず L1（Robolectric, デバイス不要）の TC-0020-N01〜N04 / E01〜E02 / B01〜B02 を `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt` に実装する
- L3（androidTest）の TC-0020-I01〜I03・B03/B04 の実発火検証は実機/エミュレータ環境で後追い実装する
- 最終確認として TC-0020-C01（`assembleDebug`）を実施する
