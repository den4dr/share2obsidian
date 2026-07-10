package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.data.llm.LlmRewriteRepository
import com.den4dr.share2Obsidian.data.llm.LlmRewriteResult
import com.den4dr.share2Obsidian.data.llm.LlmSettings
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepository
import com.den4dr.share2Obsidian.format.NoteConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TASK-0068: EditScreenViewModel suggestTags()実装
 *
 * 【テストクラス目的】: `EditScreenViewModel` に追加される `suggestTags()` メソッド、
 *   `EditFormState.isSuggestingTags` を検証する。
 * 【テスト戦略】: `EditScreenViewModelRewriteBodyTest`（TASK-0063）と同一のテストパターン
 *   （MockK コンストラクタ注入・共有 StandardTestDispatcher・通常 launch での errorEvents 購読）を踏襲する。
 * 【現状】: `suggestTags()` / `EditFormState.isSuggestingTags` は本タスク時点で未実装のため、
 *   このテストファイルはコンパイル段階で失敗する（Red フェーズの想定失敗）。
 *
 * 実行: mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditScreenViewModelSuggestTagsTest {

    // ----------------------------------------------------------------
    // ヘルパー
    // ----------------------------------------------------------------

    private val mockRewrite = mockk<LlmRewriteRepository>()
    private val mockSettings = mockk<LlmSettingsRepository>()

    // 【共有スケジューラ】: Dispatchers.Main と runTest() の TestScope が同一の TestCoroutineScheduler を
    // 使用するように、明示的に同じ StandardTestDispatcher インスタンスを両方に渡す（TASK-0063 踏襲）。
    private val testDispatcher = StandardTestDispatcher()

    /** テスト共通 NoteConfig */
    private val defaultConfig = NoteConfig(
        vault = "testVault",
        folder = "70_clippings",
        defaultTags = emptyList(),
    )

    private fun processedContent(body: String, title: String? = null) = ProcessedContent(
        body = body,
        title = title,
        contentType = ContentKind.TEXT,
    )

    /**
     * 【テストヘルパー】: `viewModel.errorEvents` を購読し、受信した messageResId を貯める
     *   リストと、購読コルーチンの `Job` をまとめて返す（TASK-0063 の collectErrorEvents を踏襲）
     */
    private fun TestScope.collectErrorEvents(viewModel: EditScreenViewModel): Pair<MutableList<Int>, Job> {
        val received = mutableListOf<Int>()
        val job = launch { viewModel.errorEvents.collect { received.add(it) } }
        return received to job
    }

    @Before
    fun setUp() {
        // 【テスト前準備】: viewModelScope が使用する Main dispatcher をテスト用に差し替える
        // 【環境初期化】: StandardTestDispatcher で launch の実行タイミングを制御可能にする
        Dispatchers.setMain(testDispatcher)
        // 【共通スタブ】: 設定取得は既定で有効な LlmSettings を返す
        coEvery { mockSettings.getSettings() } returns flowOf(
            LlmSettings(endpointUrl = "https://api.example.com/v1/chat/completions", apiKey = "sk-test", model = "gpt-4o-mini"),
        )
    }

    @After
    fun tearDown() {
        // 【テスト後処理】: 差し替えた Main dispatcher を元に戻す
        // 【状態復元】: 後続テストへの dispatcher 汚染を防ぐ
        Dispatchers.resetMain()
    }

    // ================================================================
    // 1. 正常系テストケース
    // ================================================================

    // TC-0068-N01: suggestTags() 成功時に既存 tagsText へ生成タグが追加連結される 🔵
    @Test
    fun `TC-0068-N01 suggestTags 成功時に既存 tagsText へ生成タグが追加連結される`() = runTest(testDispatcher) {
        // 【テスト目的】: suggestTags 成功時に既存 tagsText へ生成タグが追加連結されることを確認する
        // 【テスト内容】: Success スタブに対し suggestTags() を呼び、advanceUntilIdle() 後の tagsText を検証する
        // 【期待される動作】: tagsText が "private, tag1, tag2, tag3" に更新される（置換ではなく追加）
        // 🔵 信頼性レベル: TASK-0068.md テストケース1・REQ-302 より

        // 【テストデータ準備】: 既存タグありの一般的状態を代表するため tagsText を "private, tag1" に設定する
        // 【初期条件設定】: rewrite() は Success("tag2, tag3") を返すようスタブ化する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("tag2, tag3")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )
        viewModel.updateTagsText("private, tag1")

        // 【実際の処理実行】: suggestTags() を呼び出し viewModelScope の非同期処理を進める
        // 【処理内容】: isSuggestingTags=true → getSettings().first() → rewrite() → tagsText マージ → isSuggestingTags=false
        viewModel.suggestTags()
        advanceUntilIdle()

        // 【結果検証】: tagsText が既存タグ保持のまま追加連結されたことを確認する
        // 【期待値確認】: REQ-302 の追加方針。current が非空なので "$current, ${result.text}" となる
        assertEquals(
            "suggestTags 成功時に既存 tagsText へ生成タグが追加連結されること",
            "private, tag1, tag2, tag3",
            viewModel.formState.value.tagsText,
        ) // 【確認内容】: 既存タグ保持＋生成結果の追加連結 🔵
    }

    // TC-0068-N02: 既存 tagsText が空文字の場合は生成結果のみが設定される 🔵
    @Test
    fun `TC-0068-N02 既存 tagsText が空文字の場合は生成結果のみが設定される`() = runTest(testDispatcher) {
        // 【テスト目的】: 既存タグが空（isBlank）のとき、区切り文字なしで生成結果のみが設定されることを確認する
        // 【テスト内容】: Success スタブに対し suggestTags() を呼び、advanceUntilIdle() 後の tagsText を検証する
        // 【期待される動作】: 先頭に不要な ", " が付かず result.text がそのまま設定される
        // 🔵 信頼性レベル: TASK-0068.md テストケース2・完了条件 より

        // 【テストデータ準備】: タグ未入力の初期状態を代表する
        // 【初期条件設定】: rewrite() は Success("tag1, tag2") を返すようスタブ化する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("tag1, tag2")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )
        // 【前提条件確認】: defaultConfig.defaultTags が空のため initialize() 直後の tagsText は "" になる

        // 【実際の処理実行】: suggestTags() を呼び出し viewModelScope の非同期処理を進める
        viewModel.suggestTags()
        advanceUntilIdle()

        // 【結果検証】: 先頭が ", tag1..." にならず生成結果のみが設定されることを確認する
        // 【期待値確認】: 完了条件。current.isBlank() が true のとき result.text のみを設定する分岐
        assertEquals(
            "既存 tagsText が空文字の場合は生成結果のみが tagsText に設定されること",
            "tag1, tag2",
            viewModel.formState.value.tagsText,
        ) // 【確認内容】: 空タグ時のマージ分岐（先頭区切り文字混入の防止） 🔵
    }

    // TC-0068-N03（補完）: suggestTags() は sourceContent を入力とし body/tagsText を入力にしない 🟡
    @Test
    fun `TC-0068-N03 suggestTags は sourceContent を入力とし body tagsText を入力にしない`() = runTest(testDispatcher) {
        // 【テスト目的】: updateBody() 等でフォームを編集した後に suggestTags() を呼んでも、
        //                rewrite() の content 引数が保持済み sourceContent であることを確認する
        // 【テスト内容】: 元コンテンツで初期化後、ユーザーが本文・タグを編集してから suggestTags() を呼ぶ
        // 【期待される動作】: rewrite(settings, prompt, "元コンテンツ") が呼ばれ、編集後 body では呼ばれない
        // 🟡 信頼性レベル: requirements.md §2・REQ-406、踏襲元 TC-0063-N04 からの妥当な移植（TASK-0068.md には明示なし）

        // 【テストデータ準備】: 編集前後で sourceContent と formState.body に確実な差分を作る
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("tag")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )
        viewModel.updateBody("編集後本文")

        // 【実際の処理実行】: suggestTags() を呼び出す
        viewModel.suggestTags()
        advanceUntilIdle()

        // 【結果検証】: 入力ソースが常に sourceContent であることを検証する
        // 【期待値確認】: REQ-406「元コンテンツをユーザー編集と独立して保持」・REQ-302（入力ソースは本文リライトと同一）
        coVerify {
            mockRewrite.rewrite(any(), any(), "元コンテンツ")
        } // 【確認内容】: 第3引数が sourceContent であること 🟡
        coVerify(exactly = 0) {
            mockRewrite.rewrite(any(), any(), "編集後本文")
        } // 【確認内容】: 編集後の formState.body では呼ばれないこと 🟡
    }

    // ================================================================
    // 2. 異常系テストケース
    // ================================================================

    // TC-0068-E01: suggestTags() 失敗時に tagsText が変更されず errorEvents が発行される 🔵
    @Test
    fun `TC-0068-E01 suggestTags 失敗時 tagsText 不変で messageResId が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: 失敗時に既存タグ入力を破壊せず、エラーを明示することを確認する
        // 【エラーケースの概要】: LLM呼び出しがネットワーク失敗等で Failure を返すケース（EDGE-001〜004）
        // 【エラー処理の重要性】: 失敗時に既存タグ入力を破壊せず、ユーザーに再試行可能な状態を保つため（NFR-201）
        // 🔵 信頼性レベル: TASK-0068.md テストケース3・EDGE-004・NFR-201 より

        // 【テストデータ準備】: NetworkError を返すスタブと既存タグを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.NetworkError(R.string.error_llm_network)
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )
        viewModel.updateTagsText("private")

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: suggestTags() を呼び出す
        viewModel.suggestTags()
        advanceUntilIdle()

        // 【結果検証】: tagsText が不変であり、error_llm_network が1件発行されること
        // 【品質保証】: tagsText を変更しないためユーザー入力が保護される
        assertEquals(
            "suggestTags 失敗時に formState.tagsText が変更されないこと",
            "private",
            viewModel.formState.value.tagsText,
        ) // 【確認内容】: 失敗時の非破壊性の確認 🔵
        assertEquals(
            "errorEvents から error_llm_network が1件発行されること",
            listOf(R.string.error_llm_network),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    // TC-0068-B01: suggestTags() 実行中は isSuggestingTags が true になり完了後 false に戻る 🔵
    @Test
    fun `TC-0068-B01 suggestTags 実行中は isSuggestingTags が true になり完了後 false に戻る`() = runTest(testDispatcher) {
        // 【テスト目的】: REQ-201 ローディング状態管理（false→true→false）の確認
        // 【境界値の意味】: ローディングフラグの状態遷移境界。呼び出し前後の2時点を観測する
        // 🔵 信頼性レベル: TASK-0068.md テストケース4・REQ-201 より

        // 【テストデータ準備】: 応答完了前と完了後の2時点を観測するために完了を外部制御する
        val deferred = CompletableDeferred<LlmRewriteResult>()
        coEvery { mockRewrite.rewrite(any(), any(), any()) } coAnswers { deferred.await() }
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )

        // 【実際の処理実行】: suggestTags() を呼び出し、rewrite() が完了する前まで進める
        viewModel.suggestTags()
        advanceUntilIdle()

        // 【結果検証】: 応答完了前は isSuggestingTags が true であること
        assertTrue(
            "rewrite() 完了前は isSuggestingTags が true であること",
            viewModel.formState.value.isSuggestingTags,
        ) // 【確認内容】: ローディング開始状態の検証 🔵

        // 【実際の処理実行】: rewrite() を完了させ、後続処理を進める
        deferred.complete(LlmRewriteResult.Success("tag"))
        advanceUntilIdle()

        // 【結果検証】: 応答完了後は isSuggestingTags が false に戻ること
        assertFalse(
            "rewrite() 完了後は isSuggestingTags が false に戻ること",
            viewModel.formState.value.isSuggestingTags,
        ) // 【確認内容】: ローディング終了状態の検証 🔵
    }

    // TC-0068-B02: sourceContent が空文字でも suggestTags() はガードされず rewrite が1回呼ばれる 🔵
    @Test
    fun `TC-0068-B02 sourceContent が空文字でも suggestTags がガードされず rewrite が1回呼ばれる`() = runTest(testDispatcher) {
        // 【テスト目的】: EDGE-101 空文字ガード禁止の確認
        // 【境界値の意味】: LLM入力 sourceContent の最小値（空文字）。空チェックによる早期returnを行わない境界
        // 🔵 信頼性レベル: TASK-0068.md テストケース5・EDGE-101 より

        // 【テストデータ準備】: 本文なし共有（タイトルのみ等）でもタグ提案を実行可能とする要件を代表する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("tag")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = ""),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "",
            bodyLlmPrompt = "要約してください",
        )

        // 【実際の処理実行】: suggestTags() を呼び出す（早期returnされないことを確認）
        viewModel.suggestTags()
        advanceUntilIdle()

        // 【結果検証】: 第3引数（content）が空文字で1回だけ呼ばれること
        // 【補足】: 第2引数（prompt）は固定プロンプト。Context 注入方式が本タスク時点で未確定のため any() で照合する
        coVerify(exactly = 1) {
            mockRewrite.rewrite(any(), any(), "")
        } // 【確認内容】: 空文字でもガードされず rewrite が1回呼ばれること 🔵
    }

    // TC-0068-B03（補完）: 成功結果が空文字の場合のマージ挙動 🟡
    @Test
    fun `TC-0068-B03 suggestTags 成功結果が空文字の場合のマージ結果を確認する`() = runTest(testDispatcher) {
        // 【テスト目的】: 空応答成功と失敗種別の混同がないこと・連結式の境界挙動の確認
        // 【境界値の意味】: Success.text が空文字（生成結果の下限境界）
        // 🟡 信頼性レベル: LlmRewriteResult「Success は空文字も許容」・REQ-302 マージ式からの妥当な推測（TASK-0068.md には明示なし）

        // 【テストデータ準備】: 既存タグありの状態で、rewrite() が空文字の Success を返すケースを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )
        viewModel.updateTagsText("private")

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: suggestTags() を呼び出す
        viewModel.suggestTags()
        advanceUntilIdle()

        // 【結果検証】: current.isBlank() が false のため "private, " となること（実装仕様のマージ式に忠実な結果）
        assertEquals(
            "成功結果が空文字の場合はマージ式どおり末尾がカンマ+スペースになること",
            "private, ",
            viewModel.formState.value.tagsText,
        ) // 【確認内容】: 連結式の境界挙動の確認 🟡
        // 【結果検証】: Success は空文字でも成功扱いであり errorEvents には何も発行されないこと
        assertTrue(
            "Success(\"\") の場合 errorEvents には何も発行されないこと",
            received.isEmpty(),
        ) // 【確認内容】: 空応答成功と失敗種別の混同がないこと 🟡
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }
}
