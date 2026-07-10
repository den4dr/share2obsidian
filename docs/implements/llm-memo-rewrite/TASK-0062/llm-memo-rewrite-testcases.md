# TASK-0062 TDDテストケース定義書: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加

**機能名**: llm-memo-rewrite（MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加）
**タスクID**: TASK-0062
**要件名**: llm-memo-rewrite
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0062/llm-memo-rewrite-testcases.md`
**作成日**: 2026-07-06
**フェーズ**: Phase 3 - 本文リライトUI（Must Have）

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: 要件定義書・設計文書・既存実装を参考にしてほぼ推測していない
- 🟡 **黄信号**: 要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: 要件定義書・設計文書にない推測

---

## 0. テスト設計方針（テスタビリティに関する重要事項）

🟡 *note.md「EditScreenViewModelの拡張」・requirements.md §1・既存実装 EditScreenViewModel.kt より妥当な推測*

TASK-0062 の完了条件は「`initialize()` のシグネチャに `sourceContent` / `bodyLlmPrompt` を追加し、MainActivity から退避した値を渡す」ことである。一方、単体テスト（TC-001〜TC-003）では **`initialize()` に渡された値そのもの** を検証する必要がある。現行 `EditScreenViewModel.initialize()`（`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`）は受け取った引数を `EditFormState` にも内部プロパティにも保持しないため、値を観測する手段が存在しない。

そこで本タスクでは、テスト観測点として **`EditScreenViewModel` に読み取り可能な内部プロパティ**（例: `internal var sourceContent: String` / `internal var bodyLlmPrompt: String`、または `@VisibleForTesting` な公開プロパティ）を最小限追加し、`initialize()` 内で保持する方針とする。これは note.md の「後続 TASK-0063 用に ViewModel 内プライベートプロパティとして保持準備」と整合する（TASK-0063 で `rewriteBody()` から参照する下地）。

- **観測点の選択**:
  - **方式A（推奨・本テストケースが前提とする方式）**: `EditScreenViewModel` に `sourceContent` / `bodyLlmPrompt` プロパティを追加し、`initialize()` で代入。テストは `viewModel.sourceContent` / `viewModel.bodyLlmPrompt` を検証する。🟡
  - **方式B（代替）**: `initialize()` をオーバーライドして引数をキャプチャするテスト用サブクラス／MockK スパイを用意し、MainActivity 経由の呼び出し引数を検証する。ただし現行 MainActivity は `by viewModels()` で実 ViewModel を生成するため、Robolectric 上でスパイ注入が困難。Hilt 化は TASK-0063 の範囲であり本タスクでは行わないため、方式Bは MainActivity 統合レベルでは適用しにくい。🟡

以降のテストケースは **方式A**（ViewModel が受領値を保持し、それを検証する）を前提に記述する。MainActivity 統合レベル（Robolectric 起動）と ViewModel 単体レベルの2階層で検証する。

---

## 1. 正常系テストケース（基本的な動作）

### TC-0062-N01: sourceContent がテンプレート解決前の値であること（MainActivity 統合）

- **テスト名**: プレースホルダ入りテンプレートで共有した際、sourceContent にテンプレート適用前の元コンテンツが渡る
  - **何をテストするか**: `MainActivity.onCreate()` が `TemplateApplicator.buildBody()` 適用前の `processed.body` を `sourceContent` として `initialize()` に渡すこと
  - **期待される動作**: `viewModel` が受領した `sourceContent` が元コンテンツと一致し、テンプレート適用後の `body`（`resolvedBody`）とは異なる値になる
- **入力値**:
  - `ACTION_SEND` / `text/plain` インテント、`EXTRA_TEXT = "元の共有テキスト"`
  - デフォルトテンプレート: 本文に `{{content}}` プレースホルダを含む（例: 本文テンプレート `"# メモ\n\n{{content}}"`、`bodyLlmPrompt = "要約してください"`）
  - **入力データの意味**: テンプレート適用でプレースホルダ展開により `body` が確実に変化する条件。sourceContent と resolvedBody の差異を検出できる代表ケース（要件 §4.1 ケースA）
- **期待される結果**:
  - `viewModel.sourceContent == "元の共有テキスト"`（= `processed.body`、テンプレート適用前）
  - `viewModel.formState.value.body`（= `resolvedBody`）が `"元の共有テキスト"` を内包しつつテンプレート装飾（`"# メモ"` 等）を含み、`sourceContent` とは文字列として異なる
  - **期待結果の理由**: architecture.md「ProcessedContent保持設計」・REQ-406。sourceContent は LLM 入力用の未加工コンテンツであり、テンプレート適用後の表示用 body と独立して保持されねばならない
- **テストの目的**: テンプレート適用前後の値が正しく分離して受け渡されることの確認
  - **確認ポイント**: sourceContent が buildBody() 呼び出し**前**に退避されている（退避位置の正しさ）、resolvedBody とは別値であること
- 🔵 *TASK-0062.md「テストケース1」・要件定義書 TC-001・architecture.md「ProcessedContent保持設計」より*

### TC-0062-N02: bodyLlmPrompt が defaultTemplate の bodyLlmPrompt と一致すること（MainActivity 統合）

- **テスト名**: bodyLlmPrompt 設定済みテンプレートで共有した際、その値が initialize に渡る
  - **何をテストするか**: `defaultTemplate?.bodyLlmPrompt.orEmpty()` が算出され `initialize()` の `bodyLlmPrompt` 引数として渡ること
  - **期待される動作**: `viewModel.bodyLlmPrompt` がデフォルトテンプレートの `bodyLlmPrompt` と完全一致する
- **入力値**:
  - `ACTION_SEND` / `text/plain` インテント、`EXTRA_TEXT = "共有テキスト"`
  - デフォルトテンプレート: `bodyLlmPrompt = "要約してください"`
  - **入力データの意味**: REQ-101（テンプレートの本文用LLMプロンプトを受け渡す）の代表値。要件 §4.1 ケースB
- **期待される結果**:
  - `viewModel.bodyLlmPrompt == "要約してください"`
  - **期待結果の理由**: REQ-101・architecture.md「新規追加コンポーネント」。テンプレートに設定されたプロンプトが LLM リクエスト用に ViewModel へ到達する必要がある
- **テストの目的**: bodyLlmPrompt の受け渡し経路の正確性の確認
  - **確認ポイント**: 文字列が改変されずそのまま伝搬すること
- 🔵 *TASK-0062.md「テストケース2」・要件定義書 TC-002・REQ-101 より*

### TC-0062-N03: initialize() が新規2引数を保持すること（ViewModel 単体）

- **テスト名**: EditScreenViewModel.initialize() に sourceContent / bodyLlmPrompt を渡すと ViewModel が両値を保持する
  - **何をテストするか**: `initialize()` シグネチャ拡張と、受領値の保持（方式A）
  - **期待される動作**: `initialize(..., sourceContent = "S", bodyLlmPrompt = "P")` 後に `viewModel.sourceContent == "S"` かつ `viewModel.bodyLlmPrompt == "P"`
- **入力値**:
  - `ProcessedContent(body = "本文", title = "T", contentType = TEXT)`
  - `config = defaultConfig`
  - `sourceContent = "元コンテンツ"`, `bodyLlmPrompt = "リライトプロンプト"`
  - **入力データの意味**: MainActivity を介さず、ViewModel の契約（signature + 保持）を単体で固定するための最小入力
- **期待される結果**:
  - `viewModel.sourceContent == "元コンテンツ"`
  - `viewModel.bodyLlmPrompt == "リライトプロンプト"`
  - `viewModel.formState.value.body == "本文"`（既存のフォーム初期化が壊れていないこと）
  - **期待結果の理由**: requirements.md §2.2 のシグネチャ定義と、TASK-0063 の下地としての保持要件（note.md）
- **テストの目的**: ViewModel 契約（引数追加＋保持）の単体固定
  - **確認ポイント**: 既存 `formState` 初期化ロジック（vault/title/body/tagsText/folder/customFields）を回帰させないこと
- 🟡 *requirements.md §2.2・note.md「EditScreenViewModel の拡張」より妥当な推測（保持プロパティは方式Aの前提）*

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-0062-E01: defaultTemplate が null の場合 bodyLlmPrompt が空文字になり例外が出ないこと（MainActivity 統合）

- **テスト名**: デフォルトテンプレート未設定時、bodyLlmPrompt が空文字となり NPE が発生しない
  - **エラーケースの概要**: `templateRepository.getDefaultTemplate()` が `null` を返す（デフォルトテンプレート未設定）
  - **エラー処理の重要性**: null 未処理だと `defaultTemplate.bodyLlmPrompt` 参照で `NullPointerException` によりアプリがクラッシュする。`orEmpty()` による安全な既定値化が必須（REQ-102 前提整備）
- **入力値**:
  - `ACTION_SEND` / `text/plain` インテント、`EXTRA_TEXT = "共有テキスト"`
  - `templateRepository.getDefaultTemplate()` が `null` を返す環境
  - **不正な理由**: 「不正」ではなく正当な未設定状態だが、null 参照経路として異常系に分類。デフォルトテンプレート未登録は初回利用時に頻繁に発生
  - **実際の発生シナリオ**: ユーザーがテンプレートを一度も作成/既定化していない初回インストール直後の共有
- **期待される結果**:
  - `viewModel.bodyLlmPrompt == ""`（空文字）
  - 例外（`NullPointerException` 等）が送出されず、MainActivity の初期化フローが正常完了する（`initialize()` が呼ばれる）
  - **エラーメッセージの内容**: ユーザー向けメッセージは本タスクでは発生しない（後続タスクでボタン非活性化）
  - **システムの安全性**: null 由来のクラッシュを防ぎ、EditScreen 表示まで到達できる
- **テストの目的**: null フォールバック（`orEmpty()`）の確認
  - **品質保証の観点**: 未設定状態でのクラッシュ耐性を保証し、初回利用体験を破綻させない
- 🔵 *TASK-0062.md「テストケース3」・要件定義書 TC-003・REQ-102・`orEmpty()` フォールバックより*

### TC-0062-E02: defaultTemplate は存在するが bodyLlmPrompt が空文字の場合、空文字がそのまま渡ること（ViewModel/統合）

- **テスト名**: bodyLlmPrompt 空文字のテンプレートで、空文字が改変なく伝搬する
  - **エラーケースの概要**: デフォルトテンプレートは存在するが `bodyLlmPrompt = ""`（プロンプト未入力の有効テンプレート）
  - **エラー処理の重要性**: null と空文字を区別せず、いずれも「プロンプトなし」＝空文字として一貫処理する必要がある（REQ-102 のボタン非活性判定を後続で単純化するため）
- **入力値**:
  - デフォルトテンプレート: `bodyLlmPrompt = ""`
  - **不正な理由**: 不正ではないが、TC-0062-E01（null）と同一結果に収束すべき別経路
  - **実際の発生シナリオ**: ユーザーがテンプレートは作成したが本文用LLMプロンプト欄を空のまま保存した
- **期待される結果**:
  - `viewModel.bodyLlmPrompt == ""`
  - 例外なし。`orEmpty()` は空文字に対しても空文字を返すため null ケースと同結果
  - **システムの安全性**: null/空文字の両経路で一貫した空文字が保証される
- **テストの目的**: null と空文字の等価な帰結の確認（要件 §4.2 ケースD）
  - **品質保証の観点**: 後続タスクのボタン非活性判定（`bodyLlmPrompt.isBlank()`）が両経路で正しく機能する前提を固定
- 🟡 *要件定義書 §4.2 ケースD より妥当な推測（本タスクでは値の受け渡しのみ検証）*

---

## 3. 境界値テストケース（最小値、最大値、null等）

### TC-0062-B01: sourceContent が空文字（processed.body が空）でもガードせず渡ること

- **テスト名**: 空ノート（本文空文字）共有時、sourceContent="" がガードされず伝搬する
  - **境界値の意味**: `processed.body` の最小値（空文字列）。EDGE-002（空ノート許容）の境界
  - **境界値での動作保証**: 空文字でも例外なく `sourceContent = ""` として渡され、フォーム初期化も破綻しない
- **入力値**:
  - `ProcessedContent(body = "", title = null, contentType = TEXT)`（または空 `EXTRA_TEXT`）
  - `sourceContent = ""`（`processed.body` が空）
  - **境界値選択の根拠**: architecture.md 行210「ガードは bodyLlmPrompt の有無のみ、sourceContent の空文字はガードしない」を検証する最小境界
  - **実際の使用場面**: 本文のない共有（タイトルのみ、または空共有）を LLM 対象にしようとするケース
- **期待される結果**:
  - `viewModel.sourceContent == ""`
  - 例外なし。`sourceContent` に対する空チェック等のガードは行われない
  - **境界での正確性**: 空文字を「無効」として弾かず、そのまま保持する
  - **一貫した動作**: 非空文字と同じ経路で処理される
- **テストの目的**: sourceContent 空文字の非ガード伝搬の確認（EDGE-002 / 要件 §4.2 ケースE）
  - **堅牢性の確認**: 空入力でクラッシュや意図しないフィルタリングが起きないこと
- 🟡 *要件定義書 §4.2 ケースE・architecture.md 行210・EDGE-002 より妥当な推測*

### TC-0062-B02: sourceContent が長大な本文（大サイズ文字列）でも欠損なく渡ること

- **テスト名**: 大きな本文でも sourceContent が切り詰めなく完全一致で渡る
  - **境界値の意味**: 本文サイズの上限側境界。LLM 入力想定の長文（例: 数万文字）
  - **境界値での動作保証**: 長文でも `sourceContent` が `processed.body` と完全一致（切り詰め・改変なし）
- **入力値**:
  - `processed.body` = 10,000 文字程度の文字列（`"あ".repeat(10000)` 等）
  - **境界値選択の根拠**: LLM リライト対象は長文になり得るため、上限側で欠損がないことを確認。実運用の最大側代表
  - **実際の使用場面**: 長い記事本文を共有して LLM 要約する
- **期待される結果**:
  - `viewModel.sourceContent == processed.body`（長さ・内容とも完全一致）
  - **境界での正確性**: バッファ切り詰めやエンコード起因の欠損が起きない
- **テストの目的**: 大サイズ入力での完全性保証
  - **堅牢性の確認**: 長文でも参照渡し相当で欠損しないこと
- 🟡 *REQ-406（元コンテンツ保持）からの妥当な推測（具体的サイズは要件に明記なし）*

### TC-0062-B03: 既存 initialize(processed, config, customFields) 呼び出しが後方互換で動作すること（デフォルト引数境界）

- **テスト名**: 新規引数を省略した既存呼び出しが従来通り動作し sourceContent/bodyLlmPrompt が空文字既定になる
  - **境界値の意味**: 新規引数を渡さない「省略」境界。デフォルト値 `""` の適用確認
  - **境界値での動作保証**: `initialize(processed, config, customFields)` の3引数呼び出しでコンパイル・実行が成立し、`sourceContent`/`bodyLlmPrompt` が既定の空文字になる
- **入力値**:
  - `initialize(processed, defaultConfig)`（customFields も省略、既存テスト TC-0020-B03 と同形）
  - **境界値選択の根拠**: requirements.md §3「後方互換性制約（デフォルト引数）」。既存 `MainActivityEditFlowTest` の呼び出しを壊さないことの境界検証
  - **実際の使用場面**: 既存テストコード・既存呼び出し箇所（回帰対象）
- **期待される結果**:
  - コンパイル成功・実行成功
  - `viewModel.sourceContent == ""` かつ `viewModel.bodyLlmPrompt == ""`
  - 既存の `formState` 初期化（title/body/tagsText/folder）が従来通り
  - **一貫した動作**: 引数省略時と明示的に空文字を渡した時が同一結果
- **テストの目的**: additive 変更（デフォルト引数）による後方互換性の確認
  - **堅牢性の確認**: 既存呼び出し・既存テスト群を破壊しないこと
- 🔵 *requirements.md §3「後方互換性制約」・既存テスト MainActivityEditFlowTest.kt TC-0020-B03 より*

### TC-0062-B04（回帰）: 既存 MainActivity / EditScreenViewModel テスト群が全て合格すること

- **テスト名**: 既存テストスイートの後方互換確認
  - **境界値の意味**: 変更の非破壊性の境界（デグレード検出）
  - **境界値での動作保証**: 変更後も既存の正常系・異常系・境界テストが全て緑
- **入力値**:
  - 既存テスト: `MainActivityEditFlowTest`（TC-0020-N01〜B04）、`domain/model/TemplateTest`、`content/ContentProcessorTest` ほか
  - **境界値選択の根拠**: requirements.md §6「回帰」行・note.md「テストフェーズ 後方互換性」
- **期待される結果**:
  - `mise exec -- ./gradlew test` で新規追加テスト含め全件成功
  - **一貫した動作**: 既存挙動（即起動撤廃・URI 構築・キャンセル・null Intent 即終了）が不変
- **テストの目的**: 変更が既存機能を破壊しないことの保証
  - **堅牢性の確認**: additive 変更としての健全性
- 🔵 *requirements.md §6「回帰」・note.md「実装チェックリスト テストフェーズ」より*

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 既存プロジェクトが Kotlin 単一言語。対象コード（MainActivity / EditScreenViewModel）も Kotlin
  - **テストに適した機能**: バッククォート日本語テスト名、`copy()` によるイミュータブル検証、null 安全（`orEmpty()`）の直接テスト
- **テストフレームワーク**: JUnit 4 + Robolectric（+ 必要に応じ MockK / coroutines-test）
  - **フレームワーク選択の理由**: note.md「技術スタック」および既存 `MainActivityEditFlowTest.kt` が JUnit4 + Robolectric。MainActivity 起動には Robolectric が必要。ViewModel 単体は素の JUnit4 で可
  - **テスト実行環境**: ローカル JVM（`app/src/test/`）。`mise exec -- ./gradlew test`。Robolectric 設定は `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [34])` `@LooperMode(PAUSED)`（既存踏襲）
- 🔵 *note.md「1. 技術スタック」「5. テスト関連情報」・既存 MainActivityEditFlowTest.kt より*

### 4.1 テスト配置（想定）

- **ViewModel 単体テスト**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelInitializeTest.kt`（新規）または既存 ViewModel テストへ追記
- **MainActivity 統合テスト**: `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt` へ追記、または `MainActivitySourceContentTest.kt`（新規, Robolectric）
- 🟡 *note.md「既存テストのディレクトリ構成」からの妥当な推測（正確なファイル名は tdd-red で確定）*

### 4.2 MainActivity 統合テストの実現メモ（Robolectric）

🟡 *note.md「MockK によるモック・スタブ」からの妥当な推測*

- `templateRepository` / `noteSettingsRepository` は現行 MainActivity 内でどう供給されているかに依存する。Hilt 化は TASK-0063 のため、本タスクでは既存の供給方法（フィールド/生成箇所）に合わせてテストダブルを差し込む必要がある。既存 MainActivity のリポジトリ生成方式によっては、MainActivity 統合レベル（TC-0062-N01/N02/E01）を Robolectric で完全再現するのが難しく、その場合は **ViewModel 単体レベル（TC-0062-N03/E02/B01〜B03）＋ MainActivity ロジック抜粋の等価再現**（既存 `MainActivityEditFlowTest` が `onSend` ロジックを抜粋再現しているのと同じ戦略）で代替する。
- 供給方式が判明する tdd-red フェーズで、統合レベルを「実起動」で行うか「ロジック抜粋再現」で行うかを確定する。既存テストの前例（`NoteComposer` ロジック抜粋）に倣うのが現実的。

---

## 5. テストケース実装時の日本語コメント指針

各テスト実装時、以下の日本語コメントを必ず含める。

### テストケース開始時のコメント（例: TC-0062-N01）

```kotlin
// 【テスト目的】: sourceContent がテンプレート適用前の processed.body と一致し、resolvedBody と異なることを検証する
// 【テスト内容】: プレースホルダ入りデフォルトテンプレートで共有し、initialize() に渡る sourceContent を確認する
// 【期待される動作】: viewModel.sourceContent == 元コンテンツ、formState.body（resolvedBody）とは別値
// 🔵 信頼性レベル: TASK-0062.md テストケース1・REQ-406 に基づく
```

### Given（準備フェーズ）のコメント

```kotlin
// 【テストデータ準備】: {{content}} プレースホルダを含むデフォルトテンプレートと共有インテントを用意する理由＝適用前後の差分を確実に発生させるため
// 【初期条件設定】: templateRepository.getDefaultTemplate() が当該テンプレートを返す状態
// 【前提条件確認】: Template.bodyLlmPrompt フィールドが存在（TASK-0056/0057 完了前提）
```

### When（実行フェーズ）のコメント

```kotlin
// 【実際の処理実行】: MainActivity を Robolectric で起動し、Looper を idle() して lifecycleScope の処理を進める
// 【処理内容】: onCreate 内で sourceContent 退避 → buildBody → bodyLlmPrompt 算出 → initialize() 呼び出しが走る
// 【実行タイミング】: idle() を呼ばないと lifecycleScope.launch 内の initialize() が実行されない点に注意
```

### Then（検証フェーズ）のコメント

```kotlin
// 【結果検証】: initialize() に渡された sourceContent / bodyLlmPrompt を ViewModel 保持値経由で確認する
// 【期待値確認】: sourceContent はテンプレート適用前、bodyLlmPrompt はテンプレート値と一致
// 【品質保証】: LLM 入力経路（REQ-406/REQ-101）の下地が正しく敷かれることを保証する
```

### 各 expect（assert）ステートメントのコメント

```kotlin
// 【検証項目】: sourceContent がテンプレート適用前の元コンテンツであること
// 🔵 信頼性レベル: TASK-0062.md テストケース1 に基づく
assertEquals("sourceContent はテンプレート適用前の元コンテンツであること", "元の共有テキスト", viewModel.sourceContent)
assertNotEquals("sourceContent と resolvedBody(body) は別値であること", viewModel.sourceContent, viewModel.formState.value.body)
```

### セットアップ・クリーンアップのコメント

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: 各テスト用に ViewModel / リポジトリのテストダブルを初期化する
    // 【環境初期化】: initialized フラグ未設定の新規 ViewModel を用意し、テスト間の状態汚染を防ぐ
}

@After
fun tearDown() {
    // 【テスト後処理】: Robolectric の Activity controller を破棄し、Looper 状態をリセットする
    // 【状態復元】: 次テストへ ViewModel/Toast 等の副作用を持ち越さない
}
```

---

## 6. 要件定義との対応関係

- **参照した機能概要**: requirements.md §1（sourceContent 退避・initialize 引数追加の目的）
- **参照した入力・出力仕様**: requirements.md §2.1（MainActivity 中間算出）・§2.2（initialize シグネチャ変更前後）・§2.3（入出力の関係性）
- **参照した制約条件**: requirements.md §3（後方互換=デフォルト引数、ProcessedContent 不変性、null フォールバック、スコープ境界）
- **参照した使用例**: requirements.md §4.1（ケースA/B）・§4.2（ケースC/D/E）
- **参照した単体テスト要件**: requirements.md §6（TC-001〜TC-003＋回帰）、TASK-0062.md「単体テスト要件」
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`（ProcessedContent保持設計・行167-210）、`dataflow.md`、`interfaces.kt`（EditScreenViewModelSpec）
- **参照した既存実装**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（現行 initialize）、`app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt`（onCreate 行100-107）、`app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt`（Robolectric テストパターン）

### テストケース一覧（要件 TC との対応）

| テストID | 分類 | 対応要件TC | 対応 REQ/EDGE | レベル | 信頼性 |
|----------|------|-----------|---------------|--------|--------|
| TC-0062-N01 | 正常系 | TC-001 | REQ-406 | MainActivity統合 | 🔵 |
| TC-0062-N02 | 正常系 | TC-002 | REQ-101 | MainActivity統合 | 🔵 |
| TC-0062-N03 | 正常系 | (signature) | REQ-406/REQ-101 | ViewModel単体 | 🟡 |
| TC-0062-E01 | 異常系 | TC-003 | REQ-102 | MainActivity統合 | 🔵 |
| TC-0062-E02 | 異常系 | §4.2 ケースD | REQ-102 | ViewModel/統合 | 🟡 |
| TC-0062-B01 | 境界値 | §4.2 ケースE | EDGE-002 | ViewModel単体 | 🟡 |
| TC-0062-B02 | 境界値 | (長文) | REQ-406 | ViewModel単体 | 🟡 |
| TC-0062-B03 | 境界値 | 回帰 | 後方互換 | ViewModel単体 | 🔵 |
| TC-0062-B04 | 回帰 | 回帰 | 後方互換 | スイート全体 | 🔵 |

---

## 7. 品質判定

```
✅ 高品質:
- テストケース分類: 正常系（N01-N03）・異常系（E01-E02）・境界値（B01-B04）を網羅
- 期待値定義: 各テストケースの期待値が具体的な文字列・条件で明確
- 技術選択: Kotlin + JUnit4 + Robolectric（既存踏襲）で確定
- 実装可能性: 前提 TASK-0056/0057 完了済み、additive 変更で実現可能。観測点（方式A）も最小追加で実装可能
- 信頼性レベル: 🔵 が主要 TC（要件由来）を占める
```

### 信頼性レベル分布

| 分類 | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|------|-------|-------|-------|------|
| 正常系 | 2 | 1 | 0 | 3 |
| 異常系 | 1 | 1 | 0 | 2 |
| 境界値・回帰 | 2 | 2 | 0 | 4 |
| **合計** | **5** | **4** | **0** | **9** |

- 🔵 青信号: 5件（56%）— 要件定義書 TC-001〜003・後方互換・回帰に直接対応
- 🟡 黄信号: 4件（44%）— 観測点（方式A）の実装前提、長文サイズ・空文字ケースなど設計文書からの妥当な推測
- 🔴 赤信号: 0件（0%）

**総合品質評価**: ✅ 高品質

### 補足（tdd-red で確定すべき事項）

1. **観測点の実装方式**（方式A: ViewModel 保持プロパティ）を tdd-green で導入する。TC-0062-N01/N02/N03/E01/E02/B01/B02 はこの保持値を検証する。
2. **MainActivity 統合テストの実現方式**（実起動 vs ロジック抜粋再現）を、MainActivity のリポジトリ供給方式（Hilt 化前の現状）に応じて tdd-red で確定する。既存 `MainActivityEditFlowTest` のロジック抜粋再現パターンが有力な代替。

---

**次のお勧めステップ**: `/tsumiki:tdd-red llm-memo-rewrite TASK-0062` で Red フェーズ（失敗テスト作成）を開始します。
