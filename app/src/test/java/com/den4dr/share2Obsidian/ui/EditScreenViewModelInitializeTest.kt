package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.TemplateApplicator
import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.data.llm.LlmRewriteRepository
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepository
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.format.NoteConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-0062: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加
 *
 * テスト対象:
 *   - TC-0062-N01: sourceContent がテンプレート解決前の値であること（MainActivity ロジック抜粋再現）
 *   - TC-0062-N02: bodyLlmPrompt が defaultTemplate の bodyLlmPrompt と一致すること（MainActivity ロジック抜粋再現）
 *   - TC-0062-N03: initialize() が新規2引数を保持すること（ViewModel 単体）
 *   - TC-0062-E01: defaultTemplate が null の場合 bodyLlmPrompt が空文字になり例外が出ないこと
 *   - TC-0062-E02: defaultTemplate は存在するが bodyLlmPrompt が空文字の場合、空文字がそのまま渡ること
 *   - TC-0062-B01: sourceContent が空文字でもガードせず渡ること
 *   - TC-0062-B02: sourceContent が長大な本文でも欠損なく渡ること
 *   - TC-0062-B03: 新規引数を省略した既存呼び出しが後方互換で動作すること（デフォルト引数境界）
 *
 * テスト戦略（testcases.md §0/§4.2 より）:
 *   MainActivity の `viewModel` フィールドは private であり、かつ MainActivity は @AndroidEntryPoint で
 *   実 Hilt/Room DI を使用するため、Robolectric 上で MainActivity を実起動して ViewModel の内部状態を
 *   外部から直接観測することは困難。そこで既存 `MainActivityEditFlowTest`（TC-0020-N02/E01）が
 *   onSend コールバックのロジックを抜粋再現しているのと同じ戦略を採用し、
 *   MainActivity.onCreate() 内の該当ロジック（sourceContent 退避 → buildBody → bodyLlmPrompt 算出 →
 *   viewModel.initialize() 呼び出し）を TemplateApplicator・EditScreenViewModel を直接使用して再現する。
 *
 * 観測点（方式A）: `EditScreenViewModel` に `sourceContent` / `bodyLlmPrompt` の読み取り可能プロパティを
 * 追加し、`initialize()` で保持する方針（testcases.md §0 方式A）を前提にテストを記述している。
 *
 * 実行: mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelInitializeTest"
 */
class EditScreenViewModelInitializeTest {

    // ----------------------------------------------------------------
    // ヘルパー
    // ----------------------------------------------------------------

    // 【TASK-0063 追加】: EditScreenViewModel が @Inject constructor(llmRewriteRepository, llmSettingsRepository)
    // を要求するようになったため、本テストでは呼び出されない MockK スタブを渡してインスタンス化する 🔵
    private val mockRewrite = mockk<LlmRewriteRepository>()
    private val mockSettings = mockk<LlmSettingsRepository>()

    /**
     * 【テストヘルパー】: 本テストクラスは sourceContent/bodyLlmPrompt の保持ロジックを検証対象とし、
     *   LLM リポジトリを一切使用しないため、呼び出されない MockK スタブを渡すだけの
     *   EditScreenViewModel を生成する 🔵
     */
    private fun newViewModel() = EditScreenViewModel(mockRewrite, mockSettings)

    /** テスト共通 NoteConfig（既存 MainActivityEditFlowTest と同一値） */
    private val defaultConfig = NoteConfig(
        vault = "testVault",
        folder = "70_clippings",
        defaultTags = listOf("shared"),
    )

    // ================================================================
    // 1. 正常系テストケース
    // ================================================================

    /**
     * TC-0062-N01: sourceContent がテンプレート解決前の値であること（MainActivity ロジック抜粋再現）
     *
     * 🔵 信頼性レベル: TASK-0062.md「テストケース1」・要件定義書 TC-001・
     *                  architecture.md「ProcessedContent保持設計」より
     */
    @Test
    fun `TC-0062-N01 プレースホルダ入りテンプレートで共有した際 sourceContent にテンプレート適用前の元コンテンツが渡る`() {
        // 【テスト目的】: sourceContent がテンプレート適用前の processed.body と一致し、
        //                resolvedBody（テンプレート適用後の body）と異なることを検証する
        // 【テスト内容】: プレースホルダ入りデフォルトテンプレートで共有し、initialize() に渡る sourceContent を確認する
        // 【期待される動作】: viewModel.sourceContent == 元コンテンツ、formState.body（resolvedBody）とは別値
        // 🔵 信頼性レベル: TASK-0062.md テストケース1・REQ-406 に基づく

        // 【テストデータ準備】: {{content}} プレースホルダを含むデフォルトテンプレートを用意する
        //                      理由＝適用前後の差分を確実に発生させるため
        // 【初期条件設定】: defaultTemplate は本文にプレースホルダを含み bodyLlmPrompt も設定済み
        val defaultTemplate = Template(
            name = "デフォルトテンプレート",
            body = "# メモ\n\n{{content}}",
            bodyLlmPrompt = "要約してください",
            fields = emptyList(),
            isDefault = true,
        )
        val processed = ProcessedContent(
            body = "元の共有テキスト",
            title = null,
            contentType = ContentKind.TEXT,
        )

        // 【実際の処理実行】: MainActivity.onCreate() 内のロジック（変更後仕様）を再現する
        // 【処理内容】: sourceContent 退避 → buildBody → bodyLlmPrompt 算出 → initialize() 呼び出し
        // 【実行タイミング】: sourceContent は TemplateApplicator.buildBody() 呼び出し前の値である必要がある
        val sourceContent = processed.body // 🔵 REQ-406: テンプレート適用前の元コンテンツを退避
        val bodyLlmPrompt = defaultTemplate.bodyLlmPrompt // 🔵 REQ-002, REQ-101（orEmpty 相当。null でないため直接値）
        val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
        val customFields = TemplateApplicator.buildCustomFields(defaultTemplate, processed)

        val viewModel = newViewModel()
        viewModel.initialize(
            processed = processed.copy(body = resolvedBody),
            config = defaultConfig,
            customFields = customFields,
            sourceContent = sourceContent,
            bodyLlmPrompt = bodyLlmPrompt,
        )

        // 【結果検証】: sourceContent がテンプレート適用前の元コンテンツであること
        // 【期待値確認】: architecture.md「ProcessedContent保持設計」に基づき、
        //                sourceContent は LLM 入力用の未加工コンテンツとして独立保持される
        assertEquals(
            "sourceContent はテンプレート適用前の元コンテンツであること",
            "元の共有テキスト",
            viewModel.sourceContent,
        ) // 【確認内容】: sourceContent が processed.body（適用前）と一致すること 🔵

        // 【追加検証】: sourceContent と resolvedBody（formState.body）が別値であること
        assertNotEquals(
            "sourceContent と resolvedBody(body) は別値であること",
            viewModel.sourceContent,
            viewModel.formState.value.body,
        ) // 【確認内容】: テンプレート適用の有無で値が分離されていること 🔵
        assertTrue(
            "formState.body はテンプレート装飾を含むテンプレート適用後の値であること",
            viewModel.formState.value.body.contains("# メモ") &&
                viewModel.formState.value.body.contains("元の共有テキスト"),
        ) // 【確認内容】: resolvedBody がプレースホルダ展開された結果であること 🔵
    }

    /**
     * TC-0062-N02: bodyLlmPrompt が defaultTemplate の bodyLlmPrompt と一致すること（MainActivity ロジック抜粋再現）
     *
     * 🔵 信頼性レベル: TASK-0062.md「テストケース2」・要件定義書 TC-002・REQ-101 より
     */
    @Test
    fun `TC-0062-N02 bodyLlmPrompt 設定済みテンプレートで共有した際その値が initialize に渡る`() {
        // 【テスト目的】: defaultTemplate?.bodyLlmPrompt.orEmpty() が算出され
        //                initialize() の bodyLlmPrompt 引数として渡ることを検証する
        // 【テスト内容】: bodyLlmPrompt = "要約してください" のデフォルトテンプレートで initialize() を呼ぶ
        // 【期待される動作】: viewModel.bodyLlmPrompt がデフォルトテンプレートの bodyLlmPrompt と完全一致する
        // 🔵 信頼性レベル: REQ-101・architecture.md「新規追加コンポーネント」より

        // 【テストデータ準備】: bodyLlmPrompt 設定済みのデフォルトテンプレートを用意する
        // 【初期条件設定】: REQ-101（テンプレートの本文用LLMプロンプトを受け渡す）の代表値
        val defaultTemplate = Template(
            name = "デフォルトテンプレート",
            body = "",
            bodyLlmPrompt = "要約してください",
            fields = emptyList(),
            isDefault = true,
        )
        val processed = ProcessedContent(
            body = "共有テキスト",
            title = null,
            contentType = ContentKind.TEXT,
        )

        // 【実際の処理実行】: MainActivity.onCreate() 内のロジックを再現する
        // 【処理内容】: bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty() の算出を再現する
        val sourceContent = processed.body
        val bodyLlmPrompt = defaultTemplate.bodyLlmPrompt.ifEmpty { "" }
        val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
        val customFields = TemplateApplicator.buildCustomFields(defaultTemplate, processed)

        val viewModel = newViewModel()
        viewModel.initialize(
            processed = processed.copy(body = resolvedBody),
            config = defaultConfig,
            customFields = customFields,
            sourceContent = sourceContent,
            bodyLlmPrompt = bodyLlmPrompt,
        )

        // 【結果検証】: bodyLlmPrompt が改変されずそのまま伝搬すること
        // 【期待値確認】: テンプレートに設定されたプロンプトが LLM リクエスト用に ViewModel へ到達する必要がある
        assertEquals(
            "bodyLlmPrompt がデフォルトテンプレートの bodyLlmPrompt と一致すること",
            "要約してください",
            viewModel.bodyLlmPrompt,
        ) // 【確認内容】: 文字列が改変されずそのまま伝搬すること 🔵
    }

    /**
     * TC-0062-N03: initialize() に sourceContent / bodyLlmPrompt を渡すと ViewModel が両値を保持する（ViewModel 単体）
     *
     * 🟡 信頼性レベル: requirements.md §2.2・note.md「EditScreenViewModel の拡張」より妥当な推測
     *                  （保持プロパティは方式Aの前提）
     */
    @Test
    fun `TC-0062-N03 initialize が新規2引数を保持する`() {
        // 【テスト目的】: initialize() シグネチャ拡張と、受領値の保持（方式A）を検証する
        // 【テスト内容】: initialize(..., sourceContent = "S", bodyLlmPrompt = "P") 後に両値が保持されることを確認
        // 【期待される動作】: viewModel.sourceContent == "元コンテンツ" かつ viewModel.bodyLlmPrompt == "リライトプロンプト"
        // 🟡 信頼性レベル: requirements.md §2.2 のシグネチャ定義・TASK-0063 の下地としての保持要件（note.md）より

        // 【テストデータ準備】: MainActivity を介さず ViewModel の契約を単体で固定するための最小入力
        // 【初期条件設定】: ProcessedContent・NoteConfig は既存の初期化ロジックを回帰させない代表値
        val processed = ProcessedContent(
            body = "本文",
            title = "T",
            contentType = ContentKind.TEXT,
        )

        // 【実際の処理実行】: initialize() を新規引数付きで直接呼び出す
        // 【処理内容】: sourceContent / bodyLlmPrompt を明示的に指定して initialize() を呼ぶ
        val viewModel = newViewModel()
        viewModel.initialize(
            processed = processed,
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "リライトプロンプト",
        )

        // 【結果検証】: sourceContent / bodyLlmPrompt が保持されていること
        assertEquals(
            "sourceContent が initialize() の引数値で保持されること",
            "元コンテンツ",
            viewModel.sourceContent,
        ) // 【確認内容】: ViewModel 内部プロパティへの保持（方式A） 🟡
        assertEquals(
            "bodyLlmPrompt が initialize() の引数値で保持されること",
            "リライトプロンプト",
            viewModel.bodyLlmPrompt,
        ) // 【確認内容】: ViewModel 内部プロパティへの保持（方式A） 🟡

        // 【追加検証】: 既存の formState 初期化ロジックが壊れていないこと（回帰防止）
        assertEquals(
            "既存の formState.body 初期化ロジックが回帰しないこと",
            "本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: 新規引数追加が既存フォーム初期化に影響しないこと 🟡
    }

    // ================================================================
    // 2. 異常系テストケース
    // ================================================================

    /**
     * TC-0062-E01: defaultTemplate が null の場合 bodyLlmPrompt が空文字になり例外が出ないこと
     *
     * 🔵 信頼性レベル: TASK-0062.md「テストケース3」・要件定義書 TC-003・REQ-102・orEmpty() フォールバックより
     */
    @Test
    fun `TC-0062-E01 デフォルトテンプレート未設定時 bodyLlmPrompt が空文字となり例外が発生しない`() {
        // 【テスト目的】: defaultTemplate が null の場合の null フォールバック（orEmpty()）を検証する
        // 【エラーケースの概要】: templateRepository.getDefaultTemplate() が null を返す（デフォルトテンプレート未設定）
        // 【エラー処理の重要性】: null 未処理だと defaultTemplate.bodyLlmPrompt 参照で
        //                        NullPointerException によりアプリがクラッシュする
        // 🔵 信頼性レベル: TASK-0062.md テストケース3・REQ-102 に基づく

        // 【テストデータ準備】: デフォルトテンプレート未設定環境（null）を再現する
        // 【初期条件設定】: 初回インストール直後の共有を想定した代表値
        val defaultTemplate: Template? = null
        val processed = ProcessedContent(
            body = "共有テキスト",
            title = null,
            contentType = ContentKind.TEXT,
        )

        // 【実際の処理実行】: MainActivity.onCreate() の null フォールバックロジックを再現する
        // 【処理内容】: defaultTemplate?.bodyLlmPrompt.orEmpty() が例外なく空文字を返すことを確認する
        val sourceContent = processed.body
        val bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty()
        val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
        val customFields = TemplateApplicator.buildCustomFields(defaultTemplate, processed)

        val viewModel = newViewModel()
        // 【安全性確認】: この呼び出しで例外（NullPointerException 等）が発生しないことも検証対象
        viewModel.initialize(
            processed = processed.copy(body = resolvedBody),
            config = defaultConfig,
            customFields = customFields,
            sourceContent = sourceContent,
            bodyLlmPrompt = bodyLlmPrompt,
        )

        // 【結果検証】: bodyLlmPrompt が空文字であること（例外が発生していれば assert 到達前に失敗する）
        // 【品質保証の観点】: 未設定状態でのクラッシュ耐性を保証し、初回利用体験を破綻させない
        assertEquals(
            "defaultTemplate が null の場合 bodyLlmPrompt が空文字になること",
            "",
            viewModel.bodyLlmPrompt,
        ) // 【確認内容】: orEmpty() による null フォールバックが機能していること 🔵
    }

    /**
     * TC-0062-E02: defaultTemplate は存在するが bodyLlmPrompt が空文字の場合、空文字がそのまま渡ること
     *
     * 🟡 信頼性レベル: 要件定義書 §4.2 ケースD より妥当な推測（本タスクでは値の受け渡しのみ検証）
     */
    @Test
    fun `TC-0062-E02 bodyLlmPrompt 空文字のテンプレートで空文字が改変なく伝搬する`() {
        // 【テスト目的】: null と空文字を区別せず、いずれも「プロンプトなし」＝空文字として
        //                一貫処理されることを検証する
        // 【エラーケースの概要】: デフォルトテンプレートは存在するが bodyLlmPrompt = ""（プロンプト未入力）
        // 🟡 信頼性レベル: 要件定義書 §4.2 ケースD より

        // 【テストデータ準備】: bodyLlmPrompt が空文字の有効なデフォルトテンプレートを用意する
        // 【初期条件設定】: ユーザーがテンプレートは作成したがプロンプト欄を空のまま保存したケース
        val defaultTemplate = Template(
            name = "プロンプト未設定テンプレート",
            body = "",
            bodyLlmPrompt = "",
            fields = emptyList(),
            isDefault = true,
        )
        val processed = ProcessedContent(
            body = "共有テキスト",
            title = null,
            contentType = ContentKind.TEXT,
        )

        // 【実際の処理実行】: orEmpty() が空文字に対しても空文字を返すことを確認する
        val sourceContent = processed.body
        val bodyLlmPrompt = defaultTemplate.bodyLlmPrompt.orEmpty()
        val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
        val customFields = TemplateApplicator.buildCustomFields(defaultTemplate, processed)

        val viewModel = newViewModel()
        viewModel.initialize(
            processed = processed.copy(body = resolvedBody),
            config = defaultConfig,
            customFields = customFields,
            sourceContent = sourceContent,
            bodyLlmPrompt = bodyLlmPrompt,
        )

        // 【結果検証】: bodyLlmPrompt が空文字のまま伝搬すること（null ケースと同結果に収束）
        // 【品質保証の観点】: 後続タスクのボタン非活性判定（bodyLlmPrompt.isBlank()）が
        //                    null/空文字の両経路で正しく機能する前提を固定する
        assertEquals(
            "bodyLlmPrompt 空文字のテンプレートで viewModel.bodyLlmPrompt が空文字のまま渡ること",
            "",
            viewModel.bodyLlmPrompt,
        ) // 【確認内容】: 空文字が改変されずそのまま伝搬すること 🟡
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    /**
     * TC-0062-B01: sourceContent が空文字（processed.body が空）でもガードせず渡ること
     *
     * 🟡 信頼性レベル: 要件定義書 §4.2 ケースE・architecture.md 行210・EDGE-002 より妥当な推測
     */
    @Test
    fun `TC-0062-B01 空ノート共有時 sourceContent が空文字のままガードされず伝搬する`() {
        // 【テスト目的】: sourceContent の空文字が「無効」として弾かれず、そのまま保持されることを検証する
        // 【境界値の意味】: processed.body の最小値（空文字列）。EDGE-002（空ノート許容）の境界
        // 🟡 信頼性レベル: architecture.md 行210「ガードは bodyLlmPrompt の有無のみ、
        //                  sourceContent の空文字はガードしない」より

        // 【テストデータ準備】: 本文が空文字の ProcessedContent を用意する
        // 【境界値選択の根拠】: 本文のない共有（タイトルのみ、または空共有）を LLM 対象にしようとするケース
        val processed = ProcessedContent(
            body = "",
            title = null,
            contentType = ContentKind.TEXT,
        )

        // 【実際の処理実行】: sourceContent = processed.body（空文字）をそのまま initialize() に渡す
        val viewModel = newViewModel()
        viewModel.initialize(
            processed = processed,
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = processed.body,
            bodyLlmPrompt = "",
        )

        // 【結果検証】: sourceContent が空文字のまま保持されること（例外なし）
        // 【堅牢性の確認】: 空入力でクラッシュや意図しないフィルタリングが起きないこと
        assertEquals(
            "sourceContent が空文字のままガードされず伝搬すること",
            "",
            viewModel.sourceContent,
        ) // 【確認内容】: 空文字を「無効」として弾かず、そのまま保持すること 🟡
    }

    /**
     * TC-0062-B02: sourceContent が長大な本文（大サイズ文字列）でも欠損なく渡ること
     *
     * 🟡 信頼性レベル: REQ-406（元コンテンツ保持）からの妥当な推測（具体的サイズは要件に明記なし）
     */
    @Test
    fun `TC-0062-B02 大きな本文でも sourceContent が切り詰めなく完全一致で渡る`() {
        // 【テスト目的】: 長文でも sourceContent が processed.body と完全一致し、
        //                切り詰め・改変が起きないことを検証する
        // 【境界値の意味】: 本文サイズの上限側境界。LLM 入力想定の長文（10,000文字程度）
        // 🟡 信頼性レベル: REQ-406（元コンテンツ保持）からの妥当な推測

        // 【テストデータ準備】: 10,000文字の長大な本文を用意する
        // 【境界値選択の根拠】: LLM リライト対象は長文になり得るため、上限側で欠損がないことを確認する
        val longBody = "あ".repeat(10_000)
        val processed = ProcessedContent(
            body = longBody,
            title = null,
            contentType = ContentKind.TEXT,
        )

        // 【実際の処理実行】: 長文の sourceContent をそのまま initialize() に渡す
        val viewModel = newViewModel()
        viewModel.initialize(
            processed = processed,
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = processed.body,
            bodyLlmPrompt = "",
        )

        // 【結果検証】: sourceContent が長さ・内容とも完全一致すること
        // 【堅牢性の確認】: 長文でも参照渡し相当で欠損しないこと
        assertEquals(
            "sourceContent が長さ 10000 文字であること（切り詰めがないこと）",
            10_000,
            viewModel.sourceContent.length,
        ) // 【確認内容】: バッファ切り詰めが起きていないこと 🟡
        assertEquals(
            "sourceContent が processed.body と完全一致すること",
            longBody,
            viewModel.sourceContent,
        ) // 【確認内容】: エンコード起因の欠損・改変が起きていないこと 🟡
    }

    /**
     * TC-0062-B03: 既存 initialize(processed, config, customFields) 呼び出しが後方互換で動作すること（デフォルト引数境界）
     *
     * 🔵 信頼性レベル: requirements.md §3「後方互換性制約」・既存テスト MainActivityEditFlowTest.kt TC-0020-B03 より
     */
    @Test
    fun `TC-0062-B03 新規引数を省略した既存呼び出しが従来通り動作し sourceContent bodyLlmPrompt が空文字既定になる`() {
        // 【テスト目的】: additive 変更（デフォルト引数）による後方互換性を検証する
        // 【境界値の意味】: 新規引数を渡さない「省略」境界。デフォルト値 "" の適用確認
        // 🔵 信頼性レベル: requirements.md §3「後方互換性制約（デフォルト引数）」より

        // 【テストデータ準備】: 既存呼び出し箇所（回帰対象）と同形の3引数呼び出しを用意する
        // 【境界値選択の根拠】: 既存 MainActivityEditFlowTest の呼び出しを壊さないことの境界検証
        val processed = ProcessedContent(
            body = "本文",
            title = "タイトル",
            contentType = ContentKind.TEXT,
        )

        // 【実際の処理実行】: sourceContent / bodyLlmPrompt を省略して initialize() を呼び出す
        // 【処理内容】: 既存呼び出し箇所（MainActivity 変更前・既存テスト）と同一のシグネチャ呼び出しを再現する
        val viewModel = newViewModel()
        viewModel.initialize(processed, defaultConfig)

        // 【結果検証】: sourceContent / bodyLlmPrompt が既定の空文字になること
        // 【一貫した動作】: 引数省略時と明示的に空文字を渡した時が同一結果であること
        assertEquals(
            "sourceContent 省略時は既定値の空文字になること",
            "",
            viewModel.sourceContent,
        ) // 【確認内容】: デフォルト引数 sourceContent: String = "" が適用されること 🔵
        assertEquals(
            "bodyLlmPrompt 省略時は既定値の空文字になること",
            "",
            viewModel.bodyLlmPrompt,
        ) // 【確認内容】: デフォルト引数 bodyLlmPrompt: String = "" が適用されること 🔵

        // 【回帰確認】: 既存の formState 初期化（title/body）が従来通りであること
        assertEquals(
            "既存の formState.title 初期化が従来通りであること",
            "タイトル",
            viewModel.formState.value.title,
        ) // 【確認内容】: 新規引数追加が既存フォーム初期化ロジックを破壊しないこと 🔵
        assertEquals(
            "既存の formState.body 初期化が従来通りであること",
            "本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: 新規引数追加が既存フォーム初期化ロジックを破壊しないこと 🔵
    }
}
