package com.den4dr.share2Obsidian.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.den4dr.share2Obsidian.data.datastore.NoteSettings
import com.den4dr.share2Obsidian.data.datastore.NoteSettingsRepository
import com.den4dr.share2Obsidian.data.llm.LlmSettings
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // 【テスト対応】: SettingsViewModel のコンストラクタが2引数化（llmSettingsRepository追加）されたことに伴う修正
    // 🔵 信頼性レベル: TASK-0066 red-phase.md TC-E-02（既存呼び出し側の2引数化）より
    // 【TASK-0067追加】: llmRepository を差し替え可能にし、RecordingFakeLlmSettingsRepository を注入できるようにする
    // 【改善内容】: 表示確認専用の非記録Fakeと記録用Fakeが重複していたため、
    //              save*() の記録機能を持つ RecordingFakeLlmSettingsRepository 1本に統合した（DRY原則）
    // 🔵 信頼性レベル: testcases.md「テスト実装上の重要な前提」より
    private fun createViewModel(
        settings: NoteSettings = NoteSettings(),
        llmRepository: LlmSettingsRepository = RecordingFakeLlmSettingsRepository(),
    ): SettingsViewModel = SettingsViewModel(FakeNoteSettingsRepository(settings), llmRepository)

    // TC-1: 「テンプレート管理」タップで onNavigateToTemplates が呼ばれる
    @Test
    fun templateManagementItem_callsOnNavigateToTemplates() {
        var called = false
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = { called = true },
                viewModel = createViewModel(),
            )
        }
        composeTestRule.onNodeWithText("テンプレート管理").performClick()
        assertTrue("onNavigateToTemplates が呼ばれること", called)
    }

    // SettingsScreen にテンプレート管理メニューが表示される
    @Test
    fun settingsScreen_showsTemplateManagementItem() {
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(),
            )
        }
        composeTestRule.onNodeWithText("テンプレート管理").assertIsDisplayed()
    }

    // vault/folder の初期値が DataStore 設定で表示される（REQ-021）
    @Test
    fun settingsScreen_showsVaultAndFolderValues() {
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(NoteSettings(vault = "MyVault", folder = "Notes")),
            )
        }
        composeTestRule.onNodeWithText("MyVault").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notes").assertIsDisplayed()
    }

    // ============================================================
    // TASK-0067: SettingsScreen LLM設定入力欄追加（Redフェーズ）
    // 以下、settings-screen-llm-fields-testcases.md TC-N-01〜TC-B-03 に対応
    // ============================================================

    // TC-N-01: endpointUrl欄への入力でsaveEndpointUrlが入力値で呼ばれる
    @Test
    fun llmEndpointField_inputTriggersSaveEndpointUrlWithInputValue() {
        // 【テスト目的】: endpoint欄への入力で saveEndpointUrl が入力値で呼ばれることを確認する
        // 【テスト内容】: settings_llm_endpoint_field に performTextInput し、RecordingFake の記録値を検証する
        // 【期待される動作】: onValueChange → updateLlmEndpointUrl → saveEndpointUrl(input) が発火する
        // 🔵 信頼性レベル: TASK-0067.md テストケース1・testcases.md TC-N-01・REQ-004より

        // 【テストデータ準備】: save 呼び出しを記録する RecordingFakeLlmSettingsRepository を生成する
        // 【初期条件設定】: SettingsViewModel に Fake を注入し、SettingsScreen を setContent で表示する
        val fake = RecordingFakeLlmSettingsRepository()
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(llmRepository = fake),
            )
        }

        // 【実際の処理実行】: onNodeWithTag("settings_llm_endpoint_field").performTextInput(input) を実行
        // 【処理内容】: OutlinedTextField の onValueChange 経由で updateLlmEndpointUrl が呼ばれる
        composeTestRule.onNodeWithTag("settings_llm_endpoint_field").performTextInput("https://api.example.com/v1")

        // 【結果検証】: RecordingFake に記録された savedEndpointUrl を確認する
        // 【期待値確認】: 記録値が入力値と完全一致すること（加工されていないこと）
        composeTestRule.waitUntil(timeoutMillis = 2000) { fake.savedEndpointUrl != null }
        assertEquals("https://api.example.com/v1", fake.savedEndpointUrl) // 【確認内容】: 入力値がそのまま保存委譲されたことを確認
    }

    // TC-N-02: apiKey欄への入力でsaveApiKeyが入力値で呼ばれる
    @Test
    fun llmApiKeyField_inputTriggersSaveApiKeyWithInputValue() {
        // 【テスト目的】: apiKey欄への入力で saveApiKey が入力値で呼ばれることを確認する
        // 【テスト内容】: マスク表示中でも onValueChange は平文の入力値を受け取ることを検証する
        // 【期待される動作】: onValueChange → updateLlmApiKey → saveApiKey(input) が発火する
        // 🔵 信頼性レベル: TASK-0067.md テストケース2・testcases.md TC-N-02・REQ-004より

        // 【テストデータ準備】: save 呼び出しを記録する RecordingFakeLlmSettingsRepository を生成する
        // 【初期条件設定】: SettingsViewModel に Fake を注入する
        val fake = RecordingFakeLlmSettingsRepository()
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(llmRepository = fake),
            )
        }

        // 【実際の処理実行】: apiKey欄へテキストを入力する
        composeTestRule.onNodeWithTag("settings_llm_apikey_field").performTextInput("sk-test-1234567890")

        // 【結果検証】: マスクによって保存値が変質しないことを確認する
        composeTestRule.waitUntil(timeoutMillis = 2000) { fake.savedApiKey != null }
        assertEquals("sk-test-1234567890", fake.savedApiKey) // 【確認内容】: マスク表示があっても保存値は平文で正しく渡ることを確認
    }

    // TC-N-03: model欄への入力でsaveModelが入力値で呼ばれる
    @Test
    fun llmModelField_inputTriggersSaveModelWithInputValue() {
        // 【テスト目的】: model欄への入力で saveModel が入力値で呼ばれることを確認する
        // 【テスト内容】: settings_llm_model_field への入力が updateLlmModel 経由で保存されることを検証する
        // 【期待される動作】: onValueChange → updateLlmModel → saveModel(input) が発火する
        // 🔵 信頼性レベル: TASK-0067.md テストケース3・testcases.md TC-N-03・REQ-004より

        // 【テストデータ準備】: save 呼び出しを記録する RecordingFakeLlmSettingsRepository を生成する
        val fake = RecordingFakeLlmSettingsRepository()
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(llmRepository = fake),
            )
        }

        // 【実際の処理実行】: model欄へテキストを入力する
        composeTestRule.onNodeWithTag("settings_llm_model_field").performTextInput("gpt-4o")

        // 【結果検証】: 記録された値が入力値と一致することを確認する
        composeTestRule.waitUntil(timeoutMillis = 2000) { fake.savedModel != null }
        assertEquals("gpt-4o", fake.savedModel) // 【確認内容】: model欄の配線が正しいことを確認
    }

    // TC-N-04: 画面起動時にuiStateの初期値が各入力欄に反映される
    @Test
    fun llmFields_showInitialUiStateValuesOnLaunch() {
        // 【テスト目的】: Fakeへ初期LlmSettingsを注入した状態でSettingsScreenを表示すると、
        //              endpoint欄とmodel欄に初期値が表示されることを確認する
        // 【テスト内容】: uiState（llmEndpointUrl / llmModel）が各OutlinedTextFieldのvalueに反映され表示される
        // 【期待される動作】: 単方向データフロー（uiState→表示）が機能する
        // 🔵 信頼性レベル: TASK-0067.md テストケース5・testcases.md TC-N-04・REQ-004/UC-2より

        // 【テストデータ準備】: 既にLLM設定が保存済みで設定画面を開くユースケース（UC-2）を代表するデータ
        // 【初期条件設定】: LlmSettings(endpointUrl, apiKey, model) をFakeに設定する
        val initialSettings = LlmSettings(
            endpointUrl = "https://example.com",
            apiKey = "sk-xxxx",
            model = "gpt-4o",
        )
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(llmRepository = RecordingFakeLlmSettingsRepository(initialSettings)),
            )
        }

        // 【結果検証】: endpoint/modelの平文値が正しく表示されることを確認する
        composeTestRule.onNodeWithTag("settings_llm_endpoint_field")
            .assertTextContains("https://example.com") // 【確認内容】: endpoint欄に初期値が反映されていることを確認
        composeTestRule.onNodeWithTag("settings_llm_model_field")
            .assertTextContains("gpt-4o") // 【確認内容】: model欄に初期値が反映されていることを確認
    }

    // TC-N-05: LLM設定3入力欄がすべて画面に表示される
    @Test
    fun llmSection_allThreeFieldsAreDisplayed() {
        // 【テスト目的】: SettingsScreenにendpoint/apiKey/modelの3つのtestTagノードが存在し表示されることを確認する
        // 【テスト内容】: LLM設定セクションが追加され、3欄が描画されることを検証する
        // 【期待される動作】: LLM設定セクションが表示される
        // 🔵 信頼性レベル: TASK-0067.md 完了条件・testcases.md TC-N-05・requirements.md 3.4より

        // 【テストデータ準備】: 新規ユーザーが初めて設定画面を開くユースケース（UC-1前提）を代表するデフォルト状態
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(),
            )
        }

        // 【結果検証】: testTagが仕様どおり命名され、3欄すべてが可視であることを確認する
        composeTestRule.onNodeWithTag("settings_llm_endpoint_field").assertIsDisplayed() // 【確認内容】: endpoint欄が表示されていることを確認
        composeTestRule.onNodeWithTag("settings_llm_apikey_field").assertIsDisplayed() // 【確認内容】: apiKey欄が表示されていることを確認
        composeTestRule.onNodeWithTag("settings_llm_model_field").assertIsDisplayed() // 【確認内容】: model欄が表示されていることを確認
    }

    // TC-E-01: apiKey欄を空文字入力するとsaveApiKeyが空文字で呼ばれる（EC-1）
    @Test
    fun llmApiKeyField_clearingInputTriggersSaveApiKeyWithEmptyString() {
        // 【テスト目的】: 入力済みの値をクリアして空文字にするケースで、バリデーションなしで空文字保存が委譲されることを確認する
        // 【テスト内容】: 初期値ありのapiKey欄をperformTextClearanceで空文字化する
        // 【期待される動作】: 空文字入力でも例外が発生せず、update関数が空文字で正常に呼ばれる
        // 🟡 信頼性レベル: requirements.md 4.2 EC-1（TASK-0066 TC-B-01由来の妥当な推測）より

        // 【テストデータ準備】: 初期値 apiKey = "sk-existing" を保持するRecordingFakeを生成する
        // 【初期条件設定】: 初期値ありの状態からクリア操作を行う
        val fake = RecordingFakeLlmSettingsRepository(LlmSettings(apiKey = "sk-existing"))
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(llmRepository = fake),
            )
        }

        // 【実際の処理実行】: apiKey欄を空文字にクリアする
        composeTestRule.onNodeWithTag("settings_llm_apikey_field").performTextClearance()

        // 【結果検証】: 空文字入力がバリデーションで弾かれず、そのまま保存委譲されることを確認する
        composeTestRule.waitUntil(timeoutMillis = 2000) { fake.savedApiKey != null }
        assertEquals("", fake.savedApiKey) // 【確認内容】: 空文字入力でも例外なくsaveApiKeyが空文字で呼ばれることを確認
    }

    // TC-E-02: LLM設定欄追加後も既存のvault/folder/テンプレート管理表示が引き続き成功する（後方互換）
    @Test
    fun existingVaultFolderAndTemplateItem_stillDisplayedAlongsideLlmFields() {
        // 【テスト目的】: 新規UI追加によって既存機能（vault/folder入力・テンプレート管理ナビゲーション表示）が
        //              壊れる回帰（レグレッション）が発生しないことを確認する
        // 【テスト内容】: LLM設定欄と既存のvault/folder値・テンプレート管理メニューが同時に表示されることを検証する
        // 【期待される動作】: LLM設定欄の追加が既存UIの構造・可視性を損なわない
        // 🔵 信頼性レベル: requirements.md 3.2 互換性要件・既存SettingsScreenTest.ktより

        // 【テストデータ準備】: vault/folder に既存値を設定した状態でSettingsScreenを表示する
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(NoteSettings(vault = "MyVault", folder = "Notes")),
            )
        }

        // 【結果検証】: 既存要素とLLM設定欄がともに表示されていることを確認する
        composeTestRule.onNodeWithText("MyVault").assertIsDisplayed() // 【確認内容】: 既存vault値の表示が壊れていないことを確認
        composeTestRule.onNodeWithText("Notes").assertIsDisplayed() // 【確認内容】: 既存folder値の表示が壊れていないことを確認
        composeTestRule.onNodeWithText("テンプレート管理").assertIsDisplayed() // 【確認内容】: 既存テンプレート管理メニューの表示が壊れていないことを確認
        composeTestRule.onNodeWithTag("settings_llm_endpoint_field").assertIsDisplayed() // 【確認内容】: LLM設定欄が新たに表示されていることを確認
    }

    // TC-B-01: 全フィールドがデフォルト空文字でも入力欄が空欄で正常表示される（EC-2）
    @Test
    fun llmFields_displayCorrectlyWhenAllFieldsAreDefaultEmpty() {
        // 【テスト目的】: 「未設定＝空文字」という初期状態の境界で、何も保存されていない状態でもクラッシュせず空欄が表示されることを確認する
        // 【テスト内容】: LlmSettings()（全フィールド""）をFakeに設定してSettingsScreenを表示する
        // 【期待される動作】: 3欄すべてが例外なく表示される（空欄として表示）
        // 🟡 信頼性レベル: requirements.md 4.2 EC-2（null非許容・デフォルト空文字）より

        // 【テストデータ準備】: アプリ導入直後、まだLLM設定を一度も入力していないユーザーの状態を代表する
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(llmRepository = RecordingFakeLlmSettingsRepository(LlmSettings())),
            )
        }

        // 【結果検証】: 空文字が value="" として正しく扱われ、null参照等が起きないことを確認する
        composeTestRule.onNodeWithTag("settings_llm_endpoint_field").assertIsDisplayed() // 【確認内容】: デフォルト空文字でもendpoint欄が安定描画されることを確認
        composeTestRule.onNodeWithTag("settings_llm_apikey_field").assertIsDisplayed() // 【確認内容】: デフォルト空文字でもapiKey欄が安定描画されることを確認
        composeTestRule.onNodeWithTag("settings_llm_model_field").assertIsDisplayed() // 【確認内容】: デフォルト空文字でもmodel欄が安定描画されることを確認
    }

    // TC-B-02: apiKey欄が非空値でマスク表示され平文が表示されない（TC-4 / EC-3）
    @Test
    fun llmApiKeyField_masksNonEmptyValueAndDoesNotShowPlaintext() {
        // 【テスト目的】: 非空のapiKeyが必ずマスク文字へ変換され、平文が画面テキストとして露出しないことを確認する
        // 【テスト内容】: LlmSettings(apiKey = "sk-secret-value")をFakeに設定してSettingsScreenを表示する
        // 【期待される動作】: PasswordVisualTransformation()が適用され、平文がノードのテキストとして現れない
        // 🟡 信頼性レベル: TASK-0067.md テストケース4・testcases.md TC-B-02（マスクは妥当な推測）より

        // 【テストデータ準備】: 保存済みAPIキーを持つユーザーが設定画面を開く場面を代表するデータ
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(llmRepository = RecordingFakeLlmSettingsRepository(LlmSettings(apiKey = "sk-secret-value"))),
            )
        }

        // 【結果検証】: 画面に表示されるテキスト（EditableText）に平文"sk-secret-value"が含まれないことを確認する
        // 【注記】: hasText() は自動入力用の InputText セマンティクス（変換前の生値）も比較対象に含めるため
        //          使用できない。ユーザーに見える EditableText（PasswordVisualTransformation 適用後）のみを検証する
        composeTestRule.onNodeWithTag("settings_llm_apikey_field")
            .assert(
                SemanticsMatcher("EditableTextに平文APIキーが含まれない") { node ->
                    val visibleText = node.config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""
                    !visibleText.contains("sk-secret-value")
                },
            ) // 【確認内容】: apiKey欄が平文表示されずマスクされていることを確認
    }

    // TC-B-03: 長い文字列を入力しても入力値がそのままsaveEndpointUrlに渡る（最大長境界）
    @Test
    fun llmEndpointField_longInputIsSavedWithoutTruncation() {
        // 【テスト目的】: UI層に長さ制限はないため、長文でも切り詰め・加工なく保存されることを確認する
        // 【テスト内容】: 長いURL文字列をendpointUrl欄に入力し、RecordingFakeの記録値が完全一致することを検証する
        // 【期待される動作】: 長文でも文字欠落・切り詰めが起きない
        // 🟡 信頼性レベル: requirements.md 2.2「入力値そのまま渡す（加工なし）」からの妥当な推測（具体長は例示）より

        // 【テストデータ準備】: プロキシ・パス・クエリを含む長いエンドポイントURLを想定した上限側の代表値
        val longUrl = "https://api.example.com/v1/" + "a".repeat(200)
        val fake = RecordingFakeLlmSettingsRepository()
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onNavigateToTemplates = {},
                viewModel = createViewModel(llmRepository = fake),
            )
        }

        // 【実際の処理実行】: endpoint欄へ長い文字列を入力する
        composeTestRule.onNodeWithTag("settings_llm_endpoint_field").performTextInput(longUrl)

        // 【結果検証】: 長文でも文字欠落・切り詰めが起きないことを確認する
        composeTestRule.waitUntil(timeoutMillis = 2000) { fake.savedEndpointUrl != null }
        assertEquals(longUrl, fake.savedEndpointUrl) // 【確認内容】: 長い入力値が完全一致で保存委譲されたことを確認
    }
}

private class FakeNoteSettingsRepository(
    private val settings: NoteSettings = NoteSettings(),
) : NoteSettingsRepository {
    override fun getSettings(): Flow<NoteSettings> = flowOf(settings)
    override suspend fun saveVault(vault: String) {}
    override suspend fun saveFolder(folder: String) {}
}

/**
 * 【ヘルパー関数】: SettingsViewModel の2引数コンストラクタ化に伴うテスト用フェイク実装。
 * 【単一責任】: `LlmSettingsRepository` の振る舞い（初期値提供 + save*() 呼び出しの記録）のみを担う。
 * 【改善内容】: 表示確認のみに使う非記録版と、save*()の引数を記録する版とで重複していたクラスを、
 *              このクラス1本に統合した（DRY原則）。記録が不要なテストではフィールドを読まないだけでよく、
 *              振る舞いに差異はない。
 * 【再利用性】: 静的flowOfのためuiStateは再emitされない点に留意（表示ではなく記録引数で検証する）
 * 🔵 信頼性レベル: testcases.md「テスト実装上の重要な前提」・RecordingFake実装イメージより
 */
private class RecordingFakeLlmSettingsRepository(
    private val settings: LlmSettings = LlmSettings(),
) : LlmSettingsRepository {
    var savedEndpointUrl: String? = null
    var savedApiKey: String? = null
    var savedModel: String? = null
    override fun getSettings(): Flow<LlmSettings> = flowOf(settings)
    override suspend fun saveEndpointUrl(url: String) { savedEndpointUrl = url }
    override suspend fun saveApiKey(apiKey: String) { savedApiKey = apiKey }
    override suspend fun saveModel(model: String) { savedModel = model }
}
