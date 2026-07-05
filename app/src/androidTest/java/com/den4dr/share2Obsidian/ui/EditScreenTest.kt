package com.den4dr.share2Obsidian.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.format.NoteConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 【テスト概要】: EditScreen Composable の UI テスト（Compose UI Test）
 * 【テスト方針】:
 *   - createAndroidComposeRule<ComponentActivity>() を使用（BackHandler のバックディスパッチ検証に必要）
 *   - EditScreen.kt はまだ存在しないため、このテストはコンパイルエラーになる（Red 状態が正常）
 *   - 実行にはデバイス/エミュレータが必要（connectedAndroidTest）
 * 🔵 信頼性レベル: note.md テスト方針、content-edit-preview-testcases.md より
 */
@RunWith(AndroidJUnit4::class)
class EditScreenTest {

    // 【テスト環境】: createAndroidComposeRule を使用することで、BackHandler のバックプレスディスパッチが可能になる 🔵
    // 【環境初期化】: 各テストごとに新しい Compose 描画環境を用意し、テスト間の状態リークを防ぐ
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // 【共通設定】: 全テストで使用する NoteConfig（AppConfig.fromAppConfig() の値: vault=testVault, folder=70_clippings, defaultTags=["shared"]）
    // 🔵 信頼性レベル: NoteConfig.fromAppConfig() の実装・AppConfig.kt より
    private val testConfig = NoteConfig.fromAppConfig()

    /**
     * 【共通ヘルパー】: 初期化済み EditScreenViewModel を生成するテストフィクスチャ
     * 【役割】: 全テストで共有する ProcessedContent / NoteConfig / ViewModel を組み立てる
     * 🔵 信頼性レベル: content-edit-preview-testcases.md「共通テストフィクスチャ」より
     *
     * @param title タイトル（null の場合は空文字列で初期化: EDGE-001）
     * @param body 本文（デフォルト: "テスト本文"）
     */
    private fun createViewModel(
        title: String? = "テストタイトル",
        body: String = "テスト本文",
    ): EditScreenViewModel {
        // 【テストデータ準備】: ProcessedContent でコンテンツ種別を TEXT、title/body を指定して初期化する
        val viewModel = EditScreenViewModel()
        val processed = ProcessedContent(
            body = body,
            title = title,
            contentType = ContentKind.TEXT,
        )
        // 【ViewModel 初期化】: initialize() で formState の初期値を設定する（TC-003-01〜04 の前提）
        viewModel.initialize(processed, testConfig)
        return viewModel
    }

    // =========================================================================
    // 1. 正常系テストケース: フィールド初期値表示（TC-003-01〜04）
    // =========================================================================

    /**
     * TC-003-01: タイトルフィールドの初期値表示
     */
    @Test
    fun `TC-003-01 タイトルフィールドに ProcessedContent title の値が初期表示される`() {
        // 【テスト目的】: EditScreen 描画後、タイトル用 OutlinedTextField に ViewModel の formState.title が表示されること
        // 【テスト内容】: createViewModel(title="テストタイトル") で初期化した ViewModel を EditScreen に渡して描画する
        // 【期待される動作】: タイトルフィールドに "テストタイトル" が表示される
        // 🔵 信頼性レベル: requirements.md TC-003-01、note.md テストケース要件 TC-003-01 より

        // Arrange: タイトルを持つ ViewModel を用意する
        // 【テストデータ準備】: "テストタイトル" を title に設定し、EditScreen に渡す
        val viewModel = createViewModel(title = "テストタイトル")

        composeTestRule.setContent {
            // 【初期条件設定】: EditScreen を表示する（EditScreen.kt 未実装のためコンパイルエラーになる）
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
            )
        }

        // Assert: タイトルフィールドに "テストタイトル" が表示されている
        // 【結果検証】: onNodeWithText で "テストタイトル" ノードが表示されていることを確認する
        composeTestRule.onNodeWithText("テストタイトル").assertIsDisplayed() // 【確認内容】: タイトルフィールドの初期値が正しく表示される 🔵
    }

    /**
     * TC-003-02: 本文フィールドの初期値表示
     */
    @Test
    fun `TC-003-02 本文フィールドに ProcessedContent body の値が初期表示される`() {
        // 【テスト目的】: 本文用 OutlinedTextField に formState.body が表示されること
        // 【テスト内容】: createViewModel(body="テスト本文") で初期化した ViewModel を EditScreen に渡して描画する
        // 【期待される動作】: 本文フィールドに "テスト本文" が表示される
        // 🔵 信頼性レベル: note.md テストケース要件 TC-003-02、EditScreenViewModel.initialize() より

        // Arrange: 本文を持つ ViewModel を用意する
        // 【テストデータ準備】: "テスト本文" を body に設定し、EditScreen に渡す（標準ケース）
        val viewModel = createViewModel(body = "テスト本文")

        composeTestRule.setContent {
            // 【初期条件設定】: EditScreen を複数行本文フィールド付きで表示する
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
            )
        }

        // Assert: 本文フィールドに "テスト本文" が表示されている
        // 【結果検証】: 複数行フィールド（minLines=5）でも初期値テキストが描画されることを確認
        composeTestRule.onNodeWithText("テスト本文").assertIsDisplayed() // 【確認内容】: 本文フィールドの初期値が正しく表示される 🔵
    }

    /**
     * TC-003-03: タグフィールドの初期値表示
     */
    @Test
    fun `TC-003-03 タグフィールドに defaultTags 由来のカンマ区切り文字列が初期表示される`() {
        // 【テスト目的】: タグ用 OutlinedTextField に formState.tagsText が表示されること
        // 【テスト内容】: NoteConfig.fromAppConfig()（defaultTags=["shared"]）で初期化した ViewModel を使用する
        // 【期待される動作】: タグフィールドに "shared" が表示される（["shared"].joinToString(", ") = "shared"）
        // 🔵 信頼性レベル: requirements.md TC-003-03、AppConfig.kt、EditScreenViewModel.initialize() より

        // Arrange: デフォルト設定の ViewModel を用意する
        // 【テストデータ準備】: NoteConfig.fromAppConfig() の defaultTags=["shared"] からタグ初期値が生成される
        val viewModel = createViewModel()

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
            )
        }

        // Assert: タグフィールドに "shared" が表示されている
        // 【結果検証】: List<String> → カンマ区切り文字列の変換結果が表示されることを確認
        composeTestRule.onNodeWithText("shared").assertIsDisplayed() // 【確認内容】: タグフィールドの初期値（joinToString結果）が正しく表示される 🔵
    }

    /**
     * TC-003-04: フォルダフィールドの初期値表示
     */
    @Test
    fun `TC-003-04 フォルダフィールドに NoteConfig folder の値が初期表示される`() {
        // 【テスト目的】: フォルダ用 OutlinedTextField に formState.folder が表示されること
        // 【テスト内容】: NoteConfig.fromAppConfig()（folder="70_clippings"）で初期化した ViewModel を使用する
        // 【期待される動作】: フォルダフィールドに "70_clippings" が表示される
        // 🔵 信頼性レベル: requirements.md TC-003-04、AppConfig.kt、EditScreenViewModel.initialize() より

        // Arrange: デフォルト設定の ViewModel を用意する
        // 【テストデータ準備】: NoteConfig.fromAppConfig() の folder="70_clippings" がフォルダ初期値になる
        val viewModel = createViewModel()

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
            )
        }

        // Assert: フォルダフィールドに "70_clippings" が表示されている
        // 【結果検証】: config.folder が正しくフォルダフィールドにバインドされていることを確認
        composeTestRule.onNodeWithText("70_clippings").assertIsDisplayed() // 【確認内容】: フォルダフィールドの初期値が正しく表示される 🔵
    }

    // =========================================================================
    // 2. ラベル・ボタン表示テスト（TC-LABEL-01）
    // =========================================================================

    /**
     * TC-LABEL-01: 全フィールドラベルとボタンが strings.xml から表示される
     */
    @Test
    fun `TC-LABEL-01 全フィールドラベルと2ボタンのテキストが表示される`() {
        // 【テスト目的】: stringResource で取得したラベル・ボタンテキストが画面に存在することを確認する
        // 【テスト内容】: EditScreen を描画して6つのテキストノードの存在を検証する
        // 【期待される動作】: "タイトル", "本文", "タグ（カンマ区切り）", "フォルダ", "送信", "キャンセル" が表示される
        // 🔵 信頼性レベル: strings.xml、requirements.md「UI 文字列リソース」、NFR-103 より

        // Arrange: デフォルト設定の ViewModel を用意する
        // 【テストデータ準備】: NFR-103（strings.xml 利用）が満たされていることを確認するため標準入力を使用
        val viewModel = createViewModel()

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
            )
        }

        // Assert: フィールドラベルが全て表示されている
        // 【結果検証】: ハードコードではなく R.string.label_* が使われていることを表示テキストで間接確認
        composeTestRule.onNodeWithText("タイトル").assertIsDisplayed()              // 【確認内容】: label_title が表示される 🔵
        composeTestRule.onNodeWithText("本文").assertIsDisplayed()                 // 【確認内容】: label_body が表示される 🔵
        composeTestRule.onNodeWithText("タグ（カンマ区切り）").assertIsDisplayed()  // 【確認内容】: label_tags が表示される 🔵
        composeTestRule.onNodeWithText("フォルダ").assertIsDisplayed()             // 【確認内容】: label_folder が表示される 🔵
        composeTestRule.onNodeWithText("送信").assertIsDisplayed()                 // 【確認内容】: button_send が表示される 🔵
        composeTestRule.onNodeWithText("キャンセル").assertIsDisplayed()           // 【確認内容】: button_cancel が表示される 🔵
    }

    // =========================================================================
    // 3. 送信・キャンセルコールバックテスト（TC-101-01, TC-201-01）
    // =========================================================================

    /**
     * TC-101-01: 送信ボタンで onSend が呼ばれる
     */
    @Test
    fun `TC-101-01 送信ボタンタップで onSend コールバックが SendParams を伴って呼ばれる`() {
        // 【テスト目的】: 送信ボタン押下時に onSend(viewModel.buildSendParams(config)) が実行されることを確認する
        // 【テスト内容】: EditScreen を描画し、送信ボタンを performClick して onSend の発火と引数を検証する
        // 【期待される動作】: onSend が1回呼ばれ、渡された SendParams がフォーム状態を反映する
        // 🔵 信頼性レベル: requirements.md TC-101-01、TASK-0019.md テストケース2、EditScreenViewModel.buildSendParams() より

        // Arrange: キャプチャ用変数を用意して onSend の呼び出しを記録する
        // 【テストデータ準備】: タイトル/本文を持つ ViewModel で具体的な SendParams 値を検証する
        // 【初期条件設定】: onSend の呼び出し回数と引数を記録するキャプチャ変数を用意する
        val captured = mutableListOf<SendParams>()
        val viewModel = createViewModel(title = "テストタイトル", body = "テスト本文")

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = { params -> captured.add(params) }, // 【コールバックモック】: 呼び出し引数をキャプチャ
                onCancel = {},
            )
        }

        // Act: 送信ボタンを押下する
        // 【実際の処理実行】: 送信ボタン（"送信"）を performClick で押下する
        // 【処理内容】: onClick → onSend(viewModel.buildSendParams(config)) が実行される
        composeTestRule.onNodeWithText("送信").performClick()

        // Assert: onSend が1回呼ばれ、SendParams がフォーム状態を反映している
        // 【結果検証】: onSend が1回だけ呼ばれ、引数 SendParams が初期フォーム値を反映していることを検証する
        assertEquals(1, captured.size)                           // 【確認内容】: onSend が1回呼ばれた 🔵
        assertEquals("テストタイトル", captured[0].title)          // 【確認内容】: タイトルが送信値に反映 🔵
        assertEquals("テスト本文", captured[0].body)              // 【確認内容】: 本文が送信値に反映 🔵
        assertEquals(listOf("shared"), captured[0].tags)         // 【確認内容】: タグがパース済みで反映 🔵
        // buildSendParams() は EditFormState 由来の vault/folder で config を構築する（REQ-062）
        assertEquals("testVault", captured[0].config.vault)      // 【確認内容】: vault が EditFormState 由来で反映 🔵
        assertEquals("70_clippings", captured[0].config.folder)  // 【確認内容】: folder が EditFormState 由来で反映 🔵
    }

    /**
     * TC-201-01: キャンセルボタンで onCancel が呼ばれる
     */
    @Test
    fun `TC-201-01 キャンセルボタンタップで onCancel コールバックが呼ばれ onSend は呼ばれない`() {
        // 【テスト目的】: キャンセルボタン押下時に onCancel() のみが実行されることを確認する
        // 【テスト内容】: EditScreen を描画し、キャンセルボタンを performClick して onCancel の発火を確認する
        // 【期待される動作】: onCancel が1回呼ばれ、onSend は呼ばれない（Obsidian 未起動相当）
        // 🔵 信頼性レベル: requirements.md TC-201-01、TASK-0019.md テストケース3 より

        // Arrange: 呼び出しカウンタを用意する
        // 【テストデータ準備】: キャンセルが送信と排他的に動作することを確認するためカウンタを使用
        var cancelCount = 0
        var sendCount = 0
        val viewModel = createViewModel()

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = { sendCount++ },    // 【コールバックモック】: onSend 発火回数を記録
                onCancel = { cancelCount++ }, // 【コールバックモック】: onCancel 発火回数を記録
            )
        }

        // Act: キャンセルボタンを押下する
        // 【実際の処理実行】: キャンセルボタン（"キャンセル"）を performClick で押下する
        // 【処理内容】: onClick → onCancel() が実行される（Obsidian は起動しない）
        composeTestRule.onNodeWithText("キャンセル").performClick()

        // Assert: onCancel が1回呼ばれ、onSend は呼ばれていない
        // 【結果検証】: キャンセルが送信と排他的に動作すること（onSend 非発火）を確認する
        assertEquals(1, cancelCount) // 【確認内容】: onCancel が1回呼ばれた 🔵
        assertEquals(0, sendCount)   // 【確認内容】: onSend は呼ばれていない（Obsidian 未起動相当）🔵
    }

    // =========================================================================
    // 4. フィールド編集テスト（TC-FIELD-EDIT-01）
    // =========================================================================

    /**
     * TC-FIELD-EDIT-01: フィールド編集が送信値に反映される
     */
    @Test
    fun `TC-FIELD-EDIT-01 タイトルフィールドを編集してから送信すると編集後の値が onSend に渡る`() {
        // 【テスト目的】: OutlinedTextField.onValueChange → viewModel.updateTitle() → buildSendParams の経路が機能することを確認する
        // 【テスト内容】: タイトルフィールドを performTextReplacement で編集してから送信ボタンを押下する
        // 【期待される動作】: 編集後のタイトルで SendParams が生成され onSend に渡る
        // 🟡 信頼性レベル: requirements.md「入出力の関係性・データフロー」より妥当な推測（個別 TC 番号なし）

        // Arrange: キャプチャ用変数と初期タイトル付き ViewModel を用意する
        // 【テストデータ準備】: 本機能の中核ユースケース（送信前に内容を修正）を検証するため、初期値と異なる編集後値を用意
        // 【初期条件設定】: タイトル "テストタイトル" で初期化し、"編集後タイトル" に変更する
        val captured = mutableListOf<SendParams>()
        val viewModel = createViewModel(title = "テストタイトル")

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = { params -> captured.add(params) },
                onCancel = {},
            )
        }

        // Act: タイトルフィールドを編集して送信する
        // 【実際の処理実行】: タイトルフィールドに新しいテキストを入力し、送信ボタンを押下する
        // 【処理内容】: performTextReplacement → onValueChange → viewModel.updateTitle() → StateFlow 更新 → Recomposition
        composeTestRule.onNodeWithText("テストタイトル").performTextReplacement("編集後タイトル")
        composeTestRule.onNodeWithText("送信").performClick()

        // Assert: 編集後のタイトルが SendParams に反映されている
        // 【結果検証】: UI 入力が ViewModel 状態へ反映され送信値に伝わること（双方向データフロー）を確認
        assertEquals(1, captured.size)                    // 【確認内容】: onSend が1回呼ばれた 🟡
        assertEquals("編集後タイトル", captured[0].title)  // 【確認内容】: 編集後のタイトルが送信値に反映された 🟡
    }

    // =========================================================================
    // 5. 異常系テストケース（EDGE-001, EDGE-002, EDGE-003, TC-NOSEND-ON-CANCEL-01）
    // =========================================================================

    /**
     * TC-EDGE-001-01: 空タイトルで送信しても onSend が呼ばれる（title=null 許容）
     */
    @Test
    fun `TC-EDGE-001-01 タイトル空欄で送信ボタンを押しても送信できる`() {
        // 【テスト目的】: 空タイトルが許容仕様（EDGE-001）であることを確認する
        // 【テスト内容】: title=null で初期化した ViewModel を使い（→ formState.title=""）送信ボタンを押下する
        // 【期待される動作】: onSend が1回呼ばれ、captured.title == null（ifBlank { null } により null 変換）
        // 🔵 信頼性レベル: requirements.md EDGE-001、EditScreenViewModel.buildSendParams() `ifBlank { null }` より

        // Arrange: タイトル null（→ 空文字列）の ViewModel を用意する
        // 【テストデータ準備】: 共有元アプリが EXTRA_SUBJECT を提供しないケース（title=null）を代表する
        val captured = mutableListOf<SendParams>()
        val viewModel = createViewModel(title = null) // initialize で title="" になる

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = { params -> captured.add(params) },
                onCancel = {},
            )
        }

        // Act: 送信ボタンを押下する（タイトル空欄のまま）
        // 【実際の処理実行】: 空タイトルのまま送信ボタンを押下し、エラーなく送信できることを確認
        composeTestRule.onNodeWithText("送信").performClick()

        // Assert: onSend が呼ばれ、title が null になっている
        // 【結果検証】: 空タイトルでもクラッシュせず onSend が正常に呼ばれることを確認
        assertEquals(1, captured.size)    // 【確認内容】: onSend が1回呼ばれた（空タイトルでブロックされていない）🔵
        assertEquals(null, captured[0].title) // 【確認内容】: 空タイトルが null に変換されている（EDGE-001）🔵
    }

    /**
     * TC-EDGE-002-01: 空本文で送信しても onSend が呼ばれる
     */
    @Test
    fun `TC-EDGE-002-01 本文空欄で送信ボタンを押しても送信できる`() {
        // 【テスト目的】: 空本文が許容仕様（EDGE-002）であることを確認する
        // 【テスト内容】: body="" で初期化した ViewModel を使い送信ボタンを押下する
        // 【期待される動作】: onSend が1回呼ばれ、captured.body == ""
        // 🔵 信頼性レベル: requirements.md EDGE-002、EditFormState/SendParams の空文字許容定義より

        // Arrange: 空本文の ViewModel を用意する
        // 【テストデータ準備】: ユーザーがタイトルのみのメモを作成する（本文を消して送信する）ケースを代表する
        val captured = mutableListOf<SendParams>()
        val viewModel = createViewModel(body = "")

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = { params -> captured.add(params) },
                onCancel = {},
            )
        }

        // Act: 送信ボタンを押下する（本文空欄のまま）
        // 【実際の処理実行】: 空本文のまま送信ボタンを押下し、エラーなく送信できることを確認
        composeTestRule.onNodeWithText("送信").performClick()

        // Assert: onSend が呼ばれ、body が空文字列になっている
        // 【結果検証】: 空本文でもクラッシュせず onSend が正常に呼ばれることを確認
        assertEquals(1, captured.size)   // 【確認内容】: onSend が1回呼ばれた（空本文でブロックされていない）🔵
        assertEquals("", captured[0].body) // 【確認内容】: 空本文がそのまま渡される（EDGE-002）🔵
    }

    /**
     * TC-EDGE-003-01: タグ空欄/カンマのみで送信すると tags が空リストになる
     */
    @Test
    fun `TC-EDGE-003-01 タグをカンマのみに変更して送信すると tags が空リストで onSend に渡る`() {
        // 【テスト目的】: 空タグ/カンマのみ入力でも送信でき、tags が空リストになることを確認する（EDGE-003）
        // 【テスト内容】: タグフィールドを ", ," に変更してから送信ボタンを押下する
        // 【期待される動作】: onSend が1回呼ばれ、captured.tags == emptyList()
        // 🔵 信頼性レベル: requirements.md EDGE-003、parseTagsText() の filter 仕様より

        // Arrange: デフォルト ViewModel（tagsText="shared"）を用意し、タグフィールドを無効な値に変更する
        // 【テストデータ準備】: ユーザーが既定タグを消す／区切り文字だけ残すケースを代表する
        val captured = mutableListOf<SendParams>()
        val viewModel = createViewModel()

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = { params -> captured.add(params) },
                onCancel = {},
                onNavigateToSettings = {},
            )
        }

        // Act: タグフィールドをカンマのみに変更して送信する
        // 【実際の処理実行】: タグフィールドを ", ," に変更（実質的に空タグ）してから送信ボタンを押下する
        composeTestRule.onNodeWithText("shared").performTextReplacement(", ,")
        composeTestRule.onNodeWithText("送信").performClick()

        // Assert: onSend が呼ばれ、tags が空リストになっている
        // 【結果検証】: カンマのみ入力でも空文字タグを生成せず送信できることを確認
        assertEquals(1, captured.size)                           // 【確認内容】: onSend が1回呼ばれた 🔵
        assertEquals(emptyList<String>(), captured[0].tags)      // 【確認内容】: タグが空リストに正規化されている（EDGE-003）🔵
    }

    /**
     * TC-NOSEND-ON-CANCEL-01: キャンセル時に送信処理が行われない
     */
    @Test
    fun `TC-NOSEND-ON-CANCEL-01 キャンセルではフォーム値に関わらず onSend が一度も呼ばれない`() {
        // 【テスト目的】: キャンセル経路で誤って送信処理が走らないことを確認する（REQ-201 副作用なし）
        // 【テスト内容】: タイトルを設定した状態でキャンセルボタンを押下し、onSend の非発火を確認する
        // 【期待される動作】: sendCount == 0、cancelCount == 1
        // 🟡 信頼性レベル: requirements.md REQ-201・TC-201-01 から妥当な推測（副作用観点補強）

        // Arrange: 誤送信防止のためタイトル付き ViewModel でキャンセルの副作用を検証する
        // 【テストデータ準備】: "送信されてはいけない" というタイトルでキャンセルの副作用なしを確認する
        var cancelCount = 0
        var sendCount = 0
        val viewModel = createViewModel(title = "送信されてはいけない")

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = { sendCount++ },
                onCancel = { cancelCount++ },
                onNavigateToSettings = {},
            )
        }

        // Act: キャンセルボタンを押下する
        // 【実際の処理実行】: キャンセルボタンを押下し、onSend が呼ばれないことを確認する
        composeTestRule.onNodeWithText("キャンセル").performClick()

        // Assert: onSend が呼ばれず、onCancel のみ呼ばれている
        // 【結果検証】: キャンセルが送信副作用を一切伴わないことを確認する
        assertEquals(0, sendCount)   // 【確認内容】: onSend は一度も呼ばれていない（Obsidian 未起動保証）🟡
        assertEquals(1, cancelCount) // 【確認内容】: onCancel が1回呼ばれた 🟡
    }

    // =========================================================================
    // 6. バックボタン対応テスト（TC-EDGE-102-01）
    // =========================================================================

    /**
     * TC-EDGE-102-01: バックボタン押下で onCancel が呼ばれる（BackHandler）
     */
    @Test
    fun `TC-EDGE-102-01 Android バックボタン押下がキャンセルと同等に onCancel を呼ぶ`() {
        // 【テスト目的】: BackHandler { onCancel() } の発火経路を直接検証する（EDGE-102）
        // 【テスト内容】: activity.onBackPressedDispatcher.onBackPressed() をトリガーし、onCancel の発火を確認する
        // 【期待される動作】: onCancel が1回呼ばれ、onSend は呼ばれない
        // 🟡 信頼性レベル: requirements.md EDGE-102・note.md BackHandler 実装方針より妥当な推測

        // Arrange: バックボタン操作のためのカウンタを用意する
        // 【テストデータ準備】: 編集中にシステムの戻る操作で離脱するケースを代表する
        var cancelCount = 0
        var sendCount = 0
        val viewModel = createViewModel()

        composeTestRule.setContent {
            // 【初期条件設定】: EditScreen を表示して BackHandler が登録された状態にする
            EditScreen(
                viewModel = viewModel,
                onSend = { sendCount++ },
                onCancel = { cancelCount++ },
                onNavigateToSettings = {},
            )
        }

        // Act: バックボタンをディスパッチする
        // 【実際の処理実行】: onBackPressedDispatcher.onBackPressed() で BackHandler を発火させる
        // 【処理内容】: BackHandler { onCancel() } が実行される（EDGE-102）
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        // Assert: onCancel が1回呼ばれ、onSend は呼ばれていない
        // 【結果検証】: バックボタンがキャンセルボタンと等価に振る舞うことを確認する
        assertEquals(1, cancelCount) // 【確認内容】: onCancel が1回呼ばれた（バックボタン → キャンセル同等）🟡
        assertEquals(0, sendCount)   // 【確認内容】: onSend は呼ばれていない（バックボタンで意図せず送信されない）🟡
    }

    // =========================================================================
    // 7. ボタン固定表示テスト（TC-NFR-102-01）
    // =========================================================================

    /**
     * TC-NFR-102-01: 送信・キャンセルボタンが画面下部に常に表示される（NFR-102）
     */
    @Test
    fun `TC-NFR-102-01 送信とキャンセルボタンが常に表示される`() {
        // 【テスト目的】: Scaffold.bottomBar 固定とフィールド verticalScroll の分離が機能することを確認する（NFR-102）
        // 【テスト内容】: 長文本文（画面高を超える）を設定して描画し、ボタンが表示されることを確認する
        // 【期待される動作】: "送信" と "キャンセル" が assertIsDisplayed() で表示確認できる
        // 🟡 信頼性レベル: requirements.md NFR-102・note.md ボタン固定表示パターンより妥当な推測

        // Arrange: 画面高を超える長文本文を持つ ViewModel を用意する
        // 【テストデータ準備】: "長文".repeat(500) で画面をはるかに超える本文を生成し、スクロールが発生する状態にする
        // 【初期条件設定】: Scaffold.bottomBar に配置されたボタンがスクロール対象外であることを確認する
        val viewModel = createViewModel(body = "長文".repeat(500))

        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
                onNavigateToSettings = {},
            )
        }

        // Assert: 送信・キャンセルボタンが表示されている（下部固定のためスクロールに関わらず表示される）
        // 【結果検証】: ボタンが bottomBar に固定され、スクロール対象外であることを確認する
        composeTestRule.onNodeWithText("送信").assertIsDisplayed()     // 【確認内容】: 送信ボタンが常に表示される（NFR-102）🟡
        composeTestRule.onNodeWithText("キャンセル").assertIsDisplayed() // 【確認内容】: キャンセルボタンが常に表示される（NFR-102）🟡
    }

    // TC-041: 表示順（vault → folder → title → body）の各フィールドが存在する
    @Test
    fun `TC-041 vault folder title body フィールドが存在する`() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
            )
        }
        composeTestRule.onNodeWithTag("vault_field").assertExists()
        composeTestRule.onNodeWithTag("folder_field").assertExists()
        composeTestRule.onNodeWithTag("title_field").assertExists()
        composeTestRule.onNodeWithTag("body_field").assertExists()
    }

    // TC-CUSTOM-UI-001: customFields が空の場合、カスタムフィールドセクションが非表示
    @Test
    fun `TC-CUSTOM-UI-001 customFields が空のとき セクションが非表示`() {
        val viewModel = createViewModel()
        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
                onNavigateToSettings = {},
            )
        }
        composeTestRule.onAllNodesWithText("カスタムフィールド").assertCountEquals(0)
    }

    // TC-CUSTOM-UI-002: customFields があるとき、キーと値が表示される
    @Test
    fun `TC-CUSTOM-UI-002 customFields があるとき キーと値が表示される`() {
        val viewModel = EditScreenViewModel()
        val processed = ProcessedContent(body = "本文", contentType = ContentKind.TEXT)
        val customFields = listOf(CustomFieldState("source", "https://example.com", FieldValueType.STRING))
        viewModel.initialize(processed, testConfig, customFields)
        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
                onNavigateToSettings = {},
            )
        }
        composeTestRule.onNodeWithText("source").assertIsDisplayed()
        composeTestRule.onNodeWithText("https://example.com").assertIsDisplayed()
    }

    // TC-CUSTOM-UI-003: カスタムフィールドの値を編集すると ViewModel に反映される
    @Test
    fun `TC-CUSTOM-UI-003 カスタムフィールドの値編集が ViewModel に反映される`() {
        val viewModel = EditScreenViewModel()
        val processed = ProcessedContent(body = "本文", contentType = ContentKind.TEXT)
        val customFields = listOf(CustomFieldState("source", "", FieldValueType.STRING))
        viewModel.initialize(processed, testConfig, customFields)
        composeTestRule.setContent {
            EditScreen(
                viewModel = viewModel,
                onSend = {},
                onCancel = {},
                onNavigateToSettings = {},
            )
        }
        composeTestRule.onNodeWithText("source").performTextInput("new-value")
        assertEquals("new-value", viewModel.formState.value.customFields[0].value)
    }
}
