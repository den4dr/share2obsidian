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
 * TASK-0063: EditScreenViewModel Hilt化・rewriteBody()実装
 *
 * 【テストクラス目的】: `EditScreenViewModel` の `@HiltViewModel` 化後の
 *   `rewriteBody()` / `initialize()` の rewriteBodyEnabled 算出 / `errorEvents` /
 *   `EditFormState`（`isRewritingBody`, `rewriteBodyEnabled`）を検証する。
 * 【テスト戦略】: Hilt を起動せず MockK でスタブした2リポジトリ
 *   （`LlmRewriteRepository` / `LlmSettingsRepository`）をコンストラクタに直接渡してインスタンス化する
 *   （`SettingsViewModelTest` の既存パターンを踏襲）。
 * 【コルーチンスケジューラ】: `Dispatchers.Main` と `runTest()` の `TestScope` が同一の
 *   `TestCoroutineScheduler` を共有するように、`testDispatcher`（`StandardTestDispatcher`）を
 *   両方に明示的に渡している。`errorEvents`（`SharedFlow<Int>`）の購読は `backgroundScope.launch`
 *   ではなく通常の `launch` を用い、検証後に明示的に `cancel()` する
 *   （`backgroundScope.launch` は `advanceUntilIdle()` 後もコルーチンが再開されないケースを
 *   本プロジェクトの kotlinx-coroutines-test 環境で確認したため。詳細は green-phase.md §5 参照）。
 *
 * 実行: mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelRewriteBodyTest"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditScreenViewModelRewriteBodyTest {

    // ----------------------------------------------------------------
    // ヘルパー
    // ----------------------------------------------------------------

    private val mockRewrite = mockk<LlmRewriteRepository>()
    private val mockSettings = mockk<LlmSettingsRepository>()

    // 【共有スケジューラ】: Dispatchers.Main と runTest() の TestScope が同一の TestCoroutineScheduler を
    // 使用するように、明示的に同じ StandardTestDispatcher インスタンスを両方に渡す。
    // これにより viewModelScope.launch（Main 経由）と backgroundScope.launch（runTest 経由）が
    // 同一キューで確実に順序通り処理され、errorEvents の emit 取りこぼしを防ぐ。
    private val testDispatcher = StandardTestDispatcher()

    /** テスト共通 NoteConfig */
    private val defaultConfig = NoteConfig(
        vault = "testVault",
        folder = "70_clippings",
        defaultTags = listOf("shared"),
    )

    private fun processedContent(body: String, title: String? = null) = ProcessedContent(
        body = body,
        title = title,
        contentType = ContentKind.TEXT,
    )

    /**
     * 【テストヘルパー】: `viewModel.errorEvents` を購読し、受信した messageResId を貯める
     *   リストと、購読コルーチンの `Job` をまとめて返す
     * 【再利用性】: TC-0063-E01〜E05・B04 の6テストで共通する「先行購読→検証→cancel」パターンを集約する
     * 【単一責任】: 購読の開始のみを担当する。収集後のキャンセルは呼び出し側で明示的に行う
     *   （`backgroundScope.launch` は使わない。理由はクラス doc コメント参照）
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

    // TC-0063-N01: rewriteBody() 成功時に formState.body が LLM 応答テキストで上書きされる 🔵
    @Test
    fun `TC-0063-N01 rewriteBody 成功時 formState body が LLM 応答テキストに更新される`() = runTest(testDispatcher) {
        // 【テスト目的】: llmRewriteRepository.rewrite が Success を返した場合に formState.body が即時上書きされることを確認する
        // 【テスト内容】: Success スタブに対して rewriteBody() を呼び出し、advanceUntilIdle() 後の body を検証する
        // 【期待される動作】: formState.value.body が result.text と一致する（REQ-003）
        // 🔵 信頼性レベル: TASK-0063.md テストケース1・REQ-003・requirements.md §2.3 より

        // 【テストデータ準備】: 標準的なプロンプト設定済み・接続設定済みの状態を代表する
        // 【初期条件設定】: 初期本文と成功応答スタブを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("書き換え後テキスト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )

        // 【実際の処理実行】: rewriteBody() を呼び出し、viewModelScope の非同期処理を進める
        // 【処理内容】: isRewritingBody=true → getSettings().first() → rewrite() → body上書き → isRewritingBody=false
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: body が応答テキストで上書きされたことを確認する
        // 【期待値確認】: REQ-003 の即時上書き。result.text が formState.body に反映される
        assertEquals(
            "rewriteBody 成功時に formState.body が LLM 応答テキストへ更新されること",
            "書き換え後テキスト",
            viewModel.formState.value.body,
        ) // 【確認内容】: 成功時の body 上書き 🔵
    }

    // TC-0063-N02: initialize() 時 bodyLlmPrompt が非空なら rewriteBodyEnabled が true になる 🔵
    @Test
    fun `TC-0063-N02 initialize で bodyLlmPrompt 非空の場合 rewriteBodyEnabled が true になる`() {
        // 【テスト目的】: initialize() で非空の bodyLlmPrompt を渡すと rewriteBodyEnabled が true に算出されることを確認する
        // 【テスト内容】: bodyLlmPrompt = "要約してください" で initialize() を呼び出す
        // 【期待される動作】: rewriteBodyEnabled = bodyLlmPrompt.isNotBlank() が true になる（REQ-102）
        // 🔵 信頼性レベル: TASK-0063.md テストケース4・REQ-102 より

        // 【テストデータ準備】: テンプレートに本文用プロンプトが設定済みのケースを代表する
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)

        // 【実際の処理実行】: initialize() を非空の bodyLlmPrompt で呼び出す
        viewModel.initialize(
            processed = processedContent(body = "本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "本文",
            bodyLlmPrompt = "要約してください",
        )

        // 【結果検証】: rewriteBodyEnabled が true であることを確認する
        // 【期待値確認】: REQ-102「プロンプト設定時にボタン活性化」の検証
        assertTrue(
            "bodyLlmPrompt 非空時に rewriteBodyEnabled が true になること",
            viewModel.formState.value.rewriteBodyEnabled,
        ) // 【確認内容】: 活性判定ロジックが bodyLlmPrompt から正しく算出されること 🔵
    }

    // TC-0063-N03: rewriteBody() 実行中は isRewritingBody が true、完了後 false に戻る 🔵
    @Test
    fun `TC-0063-N03 rewriteBody 実行中は isRewritingBody が true になり完了後 false に戻る`() = runTest(testDispatcher) {
        // 【テスト目的】: 非同期処理の開始から完了までのローディング状態遷移（false→true→false）を確認する
        // 【テスト内容】: CompletableDeferred で rewrite() の完了タイミングを外部制御する
        // 【期待される動作】: 呼び出し中は isRewritingBody=true、完了後は false（REQ-201）
        // 🔵 信頼性レベル: TASK-0063.md テストケース6・REQ-201 より

        // 【テストデータ準備】: レスポンス完了前と完了後の2時点を観測するために完了を外部制御する
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

        // 【実際の処理実行】: rewriteBody() を呼び出し、rewrite() が完了する前まで進める
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: 応答完了前は isRewritingBody が true であること
        assertTrue(
            "rewrite() 完了前は isRewritingBody が true であること",
            viewModel.formState.value.isRewritingBody,
        ) // 【確認内容】: ローディング開始状態の検証 🔵

        // 【実際の処理実行】: rewrite() を完了させ、後続処理を進める
        deferred.complete(LlmRewriteResult.Success("結果"))
        advanceUntilIdle()

        // 【結果検証】: 応答完了後は isRewritingBody が false に戻ること
        assertFalse(
            "rewrite() 完了後は isRewritingBody が false に戻ること",
            viewModel.formState.value.isRewritingBody,
        ) // 【確認内容】: ローディング終了状態の検証 🔵
    }

    // TC-0063-N04: rewriteBody() は sourceContent を入力とし formState.body を入力にしない 🔵
    @Test
    fun `TC-0063-N04 rewriteBody は sourceContent を入力とし formState body を入力にしない`() = runTest(testDispatcher) {
        // 【テスト目的】: ユーザーが updateBody() で本文を編集した後に rewriteBody() を呼んでも、
        //                rewrite() の content 引数が sourceContent であることを確認する
        // 【テスト内容】: 編集前後で sourceContent と formState.body に確実な差分を作る
        // 【期待される動作】: rewrite(settings, bodyLlmPrompt, "元コンテンツ") が呼ばれ、編集後 body では呼ばれない（REQ-002, REQ-406）
        // 🔵 信頼性レベル: TASK-0063.md テストケース7・REQ-002・REQ-406 より（本タスクの最重要検証）

        // 【テストデータ準備】: 元コンテンツで初期化後、ユーザーが本文を編集する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("結果")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )
        viewModel.updateBody("ユーザーが編集した本文")

        // 【実際の処理実行】: rewriteBody() を呼び出す
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: 入力ソースが常に sourceContent であることを検証する
        // 【期待値確認】: REQ-406「元コンテンツをユーザー編集と独立して保持」の中核
        coVerify {
            mockRewrite.rewrite(any(), "要約してください", "元コンテンツ")
        } // 【確認内容】: 第3引数が sourceContent であること 🔵
        coVerify(exactly = 0) {
            mockRewrite.rewrite(any(), any(), "ユーザーが編集した本文")
        } // 【確認内容】: 編集後の formState.body では呼ばれないこと 🔵
    }

    // TC-0063-N05: MockK でスタブした2リポジトリをコンストラクタ注入して生成できる（Hilt互換） 🟡
    @Test
    fun `TC-0063-N05 コンストラクタ注入で EditScreenViewModel を生成でき既存 formState 初期化が回帰しない`() {
        // 【テスト目的】: @Inject constructor 化後もコンストラクタで依存を渡して生成でき、
        //                既存の initialize() が従来通り動作することを確認する
        // 【テスト内容】: EditScreenViewModel(mockRewrite, mockSettings) を生成し initialize() を呼ぶ
        // 【期待される動作】: formState 初期値（title/body/vault）が従来通り
        // 🟡 信頼性レベル: 完了条件・requirements.md §3「既存テスト非破壊」より妥当な推測

        // 【テストデータ準備】: Hilt化がコンストラクタシグネチャを変えても既存挙動を壊さないことの回帰確認
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)

        // 【実際の処理実行】: コンストラクタ注入版で initialize() を呼び出す
        viewModel.initialize(
            processed = processedContent(body = "本文", title = "T"),
            config = defaultConfig,
            customFields = emptyList(),
        )

        // 【結果検証】: 既存 API が回帰しないことを確認する
        assertEquals(
            "コンストラクタ注入後も formState.body が従来通り初期化されること",
            "本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: 既存フォーム初期化ロジックの回帰確認 🟡
        assertEquals(
            "コンストラクタ注入後も formState.title が従来通り初期化されること",
            "T",
            viewModel.formState.value.title,
        ) // 【確認内容】: 既存フォーム初期化ロジックの回帰確認 🟡
        assertEquals(
            "コンストラクタ注入後も formState.vault が従来通り初期化されること",
            defaultConfig.vault,
            viewModel.formState.value.vault,
        ) // 【確認内容】: 既存フォーム初期化ロジックの回帰確認 🟡
    }

    // ================================================================
    // 2. 異常系テストケース
    // ================================================================

    // TC-0063-E01: NetworkError 時 body 不変・errorEvents に error_llm_network を発行 🔵
    @Test
    fun `TC-0063-E01 rewriteBody が NetworkError を返すと body 不変で error_llm_network が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: NetworkError の errorEvents 発行と body 非破壊を確認する
        // 【エラーケースの概要】: ネットワーク未接続・接続失敗（EDGE-001）
        // 【エラー処理の重要性】: 失敗時に本文を破壊せず、ユーザーに再試行可能な状態を保つため
        // 🔵 信頼性レベル: TASK-0063.md テストケース2・EDGE-001・NFR-201 より

        // 【テストデータ準備】: NetworkError を返すスタブと初期本文を用意する
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

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: rewriteBody() を呼び出す
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: body が不変であり、error_llm_network が1件発行されること
        assertEquals(
            "NetworkError 時に formState.body が変更されないこと",
            "初期本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: 本文非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_network が1件発行されること",
            listOf(R.string.error_llm_network),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0063-E02: AuthError 時 body 不変・errorEvents に error_llm_auth を発行 🔵
    @Test
    fun `TC-0063-E02 rewriteBody が AuthError を返すと body 不変で error_llm_auth が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: AuthError の errorEvents 発行を確認する
        // 【エラーケースの概要】: APIキー不正・認証失敗（HTTP 401/403）（EDGE-002）
        // 【エラー処理の重要性】: 認証失敗を明示し、設定見直しを促すため
        // 🔵 信頼性レベル: TASK-0063.md テストケース2・EDGE-002・NFR-201 より

        // 【テストデータ準備】: AuthError を返すスタブと初期本文を用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.AuthError(R.string.error_llm_auth)
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: rewriteBody() を呼び出す
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: body が不変であり、error_llm_auth が1件発行されること
        assertEquals(
            "AuthError 時に formState.body が変更されないこと",
            "初期本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: 本文非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_auth が1件発行されること",
            listOf(R.string.error_llm_auth),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0063-E03: Timeout 時 body 不変・errorEvents に error_llm_timeout を発行 🔵
    @Test
    fun `TC-0063-E03 rewriteBody が Timeout を返すと body 不変で error_llm_timeout が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: Timeout の errorEvents 発行を確認する
        // 【エラーケースの概要】: 30秒タイムアウト（EDGE-003, NFR-001）
        // 【エラー処理の重要性】: 応答遅延時に無限待機させず、明示的にエラー化するため
        // 🔵 信頼性レベル: TASK-0063.md テストケース2・EDGE-003・REQ-202・NFR-001 より

        // 【テストデータ準備】: Timeout を返すスタブと初期本文を用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.Timeout(R.string.error_llm_timeout)
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: rewriteBody() を呼び出す
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: body が不変であり、error_llm_timeout が1件発行されること
        assertEquals(
            "Timeout 時に formState.body が変更されないこと",
            "初期本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: 本文非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_timeout が1件発行されること",
            listOf(R.string.error_llm_timeout),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0063-E04: EmptyOrInvalidResponse 時 body 不変・errorEvents に error_llm_empty_response を発行 🔵
    @Test
    fun `TC-0063-E04 rewriteBody が EmptyOrInvalidResponse を返すと body 不変で error_llm_empty_response が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: EmptyOrInvalidResponse の errorEvents 発行を確認する
        // 【エラーケースの概要】: 空応答・パース不能なレスポンス（EDGE-004）
        // 【エラー処理の重要性】: 不正レスポンスで本文を空にしたり壊したりしないため
        // 🔵 信頼性レベル: TASK-0063.md テストケース2・EDGE-004・NFR-201 より

        // 【テストデータ準備】: EmptyOrInvalidResponse を返すスタブと初期本文を用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.EmptyOrInvalidResponse(R.string.error_llm_empty_response)
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: rewriteBody() を呼び出す
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: body が不変であり、error_llm_empty_response が1件発行されること
        assertEquals(
            "EmptyOrInvalidResponse 時に formState.body が変更されないこと",
            "初期本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: 本文非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_empty_response が1件発行されること",
            listOf(R.string.error_llm_empty_response),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0063-E05: Unknown 時 body 不変・errorEvents に error_llm_unknown を発行 🔵
    @Test
    fun `TC-0063-E05 rewriteBody が Unknown を返すと body 不変で error_llm_unknown が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: Unknown の errorEvents 発行を確認し、全 Failure 種別の網羅を完成させる
        // 【エラーケースの概要】: 上記以外の予期しないエラー
        // 【エラー処理の重要性】: 想定外例外でもクラッシュせず汎用メッセージで通知するため
        // 🔵 信頼性レベル: TASK-0063.md テストケース2・NFR-201 より

        // 【テストデータ準備】: Unknown を返すスタブと初期本文を用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.Unknown(R.string.error_llm_unknown)
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: rewriteBody() を呼び出す
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: body が不変であり、error_llm_unknown が1件発行されること
        assertEquals(
            "Unknown 時に formState.body が変更されないこと",
            "初期本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: 本文非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_unknown が1件発行されること",
            listOf(R.string.error_llm_unknown),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    // TC-0063-B01: initialize() 時 bodyLlmPrompt が空文字なら rewriteBodyEnabled が false 🔵
    @Test
    fun `TC-0063-B01 initialize で bodyLlmPrompt 空文字の場合 rewriteBodyEnabled が false`() {
        // 【テスト目的】: プロンプト未設定時のボタン非活性化境界を確認する
        // 【境界値の意味】: bodyLlmPrompt.isNotBlank() の境界（空文字 ""）
        // 🔵 信頼性レベル: TASK-0063.md テストケース3・REQ-102 より

        // 【テストデータ準備】: 初回インストール直後・プロンプト未入力時の代表境界
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)

        // 【実際の処理実行】: bodyLlmPrompt = "" で initialize() を呼び出す
        viewModel.initialize(
            processed = processedContent(body = "本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "本文",
            bodyLlmPrompt = "",
        )

        // 【結果検証】: rewriteBodyEnabled が false であることを確認する
        // 【堅牢性の確認】: 空文字入力で誤って活性化しないこと
        assertFalse(
            "bodyLlmPrompt 空文字時に rewriteBodyEnabled が false になること",
            viewModel.formState.value.rewriteBodyEnabled,
        ) // 【確認内容】: 空文字境界での非活性化確認 🔵
    }

    // TC-0063-B02: sourceContent が空文字でも rewriteBody() はガードせず rewrite を呼ぶ（EDGE-101） 🔵
    @Test
    fun `TC-0063-B02 sourceContent 空文字でも rewriteBody がガードされず rewrite が実行される`() = runTest(testDispatcher) {
        // 【テスト目的】: 空文字ガード禁止（EDGE-101）を確認する
        // 【境界値の意味】: sourceContent の最小値（空文字 ""）
        // 🔵 信頼性レベル: TASK-0063.md テストケース5・EDGE-101・LlmRewriteRepository.kt より

        // 【テストデータ準備】: 本文なし共有でも bodyLlmPrompt があればボタンは活性であるケース
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("結果")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = ""),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "",
            bodyLlmPrompt = "要約してください",
        )

        // 【実際の処理実行】: rewriteBody() を呼び出す（早期returnされないことを確認）
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: 第3引数が空文字で実際に呼ばれ、body が更新されること
        coVerify {
            mockRewrite.rewrite(any(), "要約してください", "")
        } // 【確認内容】: 空文字でもガードされず rewrite が呼ばれること 🔵
        assertEquals(
            "sourceContent 空文字でも成功時は formState.body が更新されること",
            "結果",
            viewModel.formState.value.body,
        ) // 【確認内容】: 空文字ガード禁止時の正常フロー完走確認 🔵
    }

    // TC-0063-B03: initialize() 2回目呼び出しは既存 sourceContent/bodyLlmPrompt/formState を上書きしない（EDGE-101） 🔵
    @Test
    fun `TC-0063-B03 画面回転想定の再 initialize で sourceContent bodyLlmPrompt formState が上書きされない`() {
        // 【テスト目的】: 重複初期化防止が Hilt化・引数追加後も維持されることを確認する
        // 【境界値の意味】: initialized フラグの境界（1回目→2回目）。画面回転時の重複初期化防止
        // 🔵 信頼性レベル: EDGE-101・requirements.md §3「重複初期化防止」・既存 EditScreenViewModel.kt より

        // 【テストデータ準備】: 画面回転で Activity 再作成 → ViewModel 生存 → initialize 再呼び出しのケース
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )

        // 【実際の処理実行】: 別引数で initialize() を再度呼び出す
        viewModel.initialize(
            processed = processedContent(body = "別本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "別コンテンツ",
            bodyLlmPrompt = "別プロンプト",
        )

        // 【結果検証】: 2回目の引数が反映されないことを確認する
        // 【堅牢性の確認】: 回転時にユーザー編集内容・入力ソースが失われないこと
        assertEquals(
            "2回目の initialize で sourceContent が上書きされないこと",
            "元コンテンツ",
            viewModel.sourceContent,
        ) // 【確認内容】: sourceContent の重複初期化防止 🔵
        assertEquals(
            "2回目の initialize で bodyLlmPrompt が上書きされないこと",
            "要約してください",
            viewModel.bodyLlmPrompt,
        ) // 【確認内容】: bodyLlmPrompt の重複初期化防止 🔵
        assertTrue(
            "2回目の initialize でも1回目の rewriteBodyEnabled が維持されること",
            viewModel.formState.value.rewriteBodyEnabled,
        ) // 【確認内容】: rewriteBodyEnabled の重複初期化防止 🔵
        assertEquals(
            "2回目の initialize で formState.body が上書きされないこと",
            "初期本文",
            viewModel.formState.value.body,
        ) // 【確認内容】: formState 全体の重複初期化防止（既存挙動の維持） 🔵
    }

    // TC-0063-B04: rewriteBody() 成功結果が空文字の場合でも formState.body が空文字で上書きされる 🟡
    @Test
    fun `TC-0063-B04 rewriteBody 成功結果が空文字なら body が空文字で上書きされる`() = runTest(testDispatcher) {
        // 【テスト目的】: 成功・空文字応答の扱い（body上書き・非エラー）を確認する
        // 【境界値の意味】: LlmRewriteResult.Success の text が空文字 ""（下限境界）
        // 🟡 信頼性レベル: LlmRewriteResult.kt「Success は空文字も許容」・REQ-003 より妥当な推測

        // 【テストデータ準備】: 成功だが空応答という下限境界を用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = emptyList(),
            sourceContent = "元コンテンツ",
            bodyLlmPrompt = "要約してください",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: rewriteBody() を呼び出す
        viewModel.rewriteBody()
        advanceUntilIdle()

        // 【結果検証】: Success は空文字でも body へ反映され、エラー扱いしないことを確認する
        assertEquals(
            "Success(\"\") の場合 formState.body が空文字で上書きされること",
            "",
            viewModel.formState.value.body,
        ) // 【確認内容】: 空応答成功時の body 上書き確認 🟡
        assertTrue(
            "Success(\"\") の場合 errorEvents には何も発行されないこと",
            received.isEmpty(),
        ) // 【確認内容】: 空応答成功と失敗種別の混同がないこと 🟡
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }
}
