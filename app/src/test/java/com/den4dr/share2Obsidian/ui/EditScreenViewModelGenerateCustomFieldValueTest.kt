package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.data.llm.LlmRewriteRepository
import com.den4dr.share2Obsidian.data.llm.LlmRewriteResult
import com.den4dr.share2Obsidian.data.llm.LlmSettings
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepository
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TASK-0072: EditScreenViewModel generateCustomFieldValue()・EditScreen UI「生成」ボタン追加
 *
 * 【テストクラス目的】: `EditScreenViewModel` に新規追加する `generateCustomFieldValue(index: Int)` が、
 *   `customFields[index].valueSource == FieldValueSource.LLM` のフィールドについて `llmPrompt` と
 *   `sourceContent` を入力に LLM 呼び出しを行い、該当インデックスの `value` のみを更新すること（他フィールド
 *   には影響しないこと）・失敗時は `errorEvents` を発行して該当フィールドの値を変更しないことを検証する。
 * 【テスト戦略】: Hilt を起動せず MockK でスタブした2リポジトリ（`LlmRewriteRepository` / `LlmSettingsRepository`）
 *   をコンストラクタに直接渡してインスタンス化する（`EditScreenViewModelRewriteBodyTest` の既存パターンを踏襲）。
 * 【コルーチンスケジューラ】: `Dispatchers.Main` と `runTest()` の `TestScope` が同一の `TestCoroutineScheduler` を
 *   共有するように、`testDispatcher`（`StandardTestDispatcher`）を両方に明示的に渡している。`errorEvents`
 *   （`SharedFlow<Int>`）の購読は `backgroundScope.launch` ではなく通常の `launch` を用い、検証後に明示的に
 *   `cancel()` する（`EditScreenViewModelRewriteBodyTest` と同一方針）。
 * 🔴 信頼性レベル: `generateCustomFieldValue()` は本タスクで新規追加するメソッドのため、このテストファイルは
 *   現時点でコンパイルが通らない（Unresolved reference）ことを Red フェーズの失敗として想定する。
 *
 * 実行: mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelGenerateCustomFieldValueTest"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditScreenViewModelGenerateCustomFieldValueTest {

    // ----------------------------------------------------------------
    // ヘルパー
    // ----------------------------------------------------------------

    private val mockRewrite = mockk<LlmRewriteRepository>()
    private val mockSettings = mockk<LlmSettingsRepository>()

    // 【共有スケジューラ】: Dispatchers.Main と runTest() の TestScope が同一の TestCoroutineScheduler を
    // 使用するように、明示的に同じ StandardTestDispatcher インスタンスを両方に渡す（既存パターン踏襲）。
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
     * 【テストヘルパー】: `viewModel.errorEvents` を購読し、受信した messageResId を貯めるリストと、
     *   購読コルーチンの `Job` をまとめて返す（`EditScreenViewModelRewriteBodyTest` と同一実装）
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

    // TC-0072-N01: 対象インデックスの value のみ更新され、他フィールドは全プロパティ不変 🔵
    @Test
    fun `TC-0072-N01 generateCustomFieldValue 成功時に対象インデックスの value のみ生成結果へ更新され他インデックスのフィールドは変化しない`() = runTest(testDispatcher) {
        // 【テスト目的】: generateCustomFieldValue(index) が customFields[index].value のみを LLM 応答テキストで
        //                上書きし、他インデックスの CustomFieldState に一切影響しないことを確認する
        // 【テスト内容】: FIXED フィールドと LLM フィールドを混在させ、LLM フィールドのみを対象に生成を実行する
        // 【期待される動作】: 対象フィールドの value が生成結果に置き換わり、他フィールドは value を含む全プロパティが不変
        // 🔵 信頼性レベル: TASK-0072.md 単体テスト要件テストケース1・完了条件より

        // 【テストデータ準備】: 非LLM(FIXED)フィールドとLLMフィールドを混在させ、生成が対象のみに限定されることを検証する
        // 【初期条件設定】: index=1 のみ valueSource=LLM とし、rewrite は Success を返すようスタブする
        val fieldFixed = CustomFieldState("author", "既存A", FieldValueType.STRING, FieldValueSource.FIXED)
        val fieldLlm = CustomFieldState("summary", "既存S", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("生成結果")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldFixed, fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【実際の処理実行】: generateCustomFieldValue(1) を呼び出し、viewModelScope の非同期処理を進める
        // 【処理内容】: prompt取得 → getSettings().first() → rewrite() → updateCustomField(1, "生成結果")
        viewModel.generateCustomFieldValue(1)
        advanceUntilIdle()

        // 【結果検証】: 対象インデックスの value のみが更新され、非対象インデックスは全プロパティ不変であることを確認する
        // 【期待値確認】: 完了条件「該当インデックスの value のみ更新／他フィールドに影響しない」
        assertEquals(
            "対象インデックス(1)の value が生成結果に更新されること",
            "生成結果",
            viewModel.formState.value.customFields[1].value,
        ) // 【確認内容】: 対象 value が生成結果に更新される 🔵
        assertEquals(
            "非対象インデックス(0)の CustomFieldState が全プロパティ不変であること",
            fieldFixed,
            viewModel.formState.value.customFields[0],
        ) // 【確認内容】: 非対象フィールドが key/value/valueType/valueSource/llmPrompt すべて不変 🔵
    }

    // TC-0072-N02: 入力に sourceContent を使い formState.body を使わない 🔵
    @Test
    fun `TC-0072-N02 generateCustomFieldValue は編集後の formState body ではなく sourceContent を rewrite の入力に渡す`() = runTest(testDispatcher) {
        // 【テスト目的】: ユーザーが updateBody() で本文を編集した後でも、rewrite の content 引数が
        //                初期化時の sourceContent であることを確認する
        // 【テスト内容】: sourceContent と formState.body に確実な差分を作り、入力ソースの独立性（REQ-406）を切り分ける
        // 【期待される動作】: rewrite(settings, prompt, "元コンテンツ") が呼ばれ、編集後 body では呼ばれない
        // 🔵 信頼性レベル: TASK-0072.md 単体テスト要件テストケース3・REQ-002/REQ-406、既存 TC-0063-N04 と同型より

        // 【テストデータ準備】: 元コンテンツで初期化後、ユーザーが本文を編集する
        // 【初期条件設定】: LLM フィールドの llmPrompt = "要約プロンプト" を用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("結果")
        val fieldLlm = CustomFieldState("summary", "既存S", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "元コンテンツ",
        )
        viewModel.updateBody("ユーザーが編集した本文")

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す
        // 【実行タイミング】: advanceUntilIdle() で viewModelScope.launch の完了を待機
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 入力ソースが常に sourceContent であることを検証する
        // 【期待値確認】: REQ-406「元コンテンツをユーザー編集と独立して保持」の中核
        coVerify {
            mockRewrite.rewrite(any(), "要約プロンプト", "元コンテンツ")
        } // 【確認内容】: content 引数が sourceContent かつ prompt 引数が対象 llmPrompt であること 🔵
        coVerify(exactly = 0) {
            mockRewrite.rewrite(any(), any(), "ユーザーが編集した本文")
        } // 【確認内容】: 編集後の formState.body では呼ばれないこと 🔵
    }

    // TC-0072-N03: 対象フィールドの llmPrompt が rewrite の prompt 引数に渡される 🔵
    @Test
    fun `TC-0072-N03 generateCustomFieldValue は customFields index の llmPrompt を LLM 呼び出しの prompt に使用する`() = runTest(testDispatcher) {
        // 【テスト目的】: 複数の LLM フィールドが異なる llmPrompt を持つ場合、対象インデックスの llmPrompt が
        //                選択されることを確認する
        // 【テスト内容】: index=0, index=1 で異なるプロンプトを持つ2つの LLM フィールドを用意する
        // 【期待される動作】: rewrite(settings, "プロンプトB", sourceContent) が呼ばれる
        // 🔵 信頼性レベル: TASK-0072.md 実装詳細（prompt = formState.value.customFields[index].llmPrompt）より

        // 【テストデータ準備】: プロンプト取り違え（index誤り）を検出できるよう、異なるプロンプトを与える
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("結果")
        val fieldA = CustomFieldState("a", "", FieldValueType.STRING, FieldValueSource.LLM, "プロンプトA")
        val fieldB = CustomFieldState("b", "", FieldValueType.STRING, FieldValueSource.LLM, "プロンプトB")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "本文"),
            config = defaultConfig,
            customFields = listOf(fieldA, fieldB),
            sourceContent = "元コンテンツ",
        )

        // 【実際の処理実行】: generateCustomFieldValue(1) を呼び出す（index=1 の fieldB を対象とする）
        viewModel.generateCustomFieldValue(1)
        advanceUntilIdle()

        // 【結果検証】: index=1 の llmPrompt（"プロンプトB"）が rewrite に渡されることを確認する
        // 【期待値確認】: index=0 のプロンプトが誤って使われないこと
        coVerify {
            mockRewrite.rewrite(any(), "プロンプトB", any())
        } // 【確認内容】: index → llmPrompt の対応が正しいこと 🔵
    }

    // TC-0072-N04: 生成中はフィールド単位のローディングが立ち、完了後に解除される 🟡
    @Test
    fun `TC-0072-N04 generateCustomFieldValue 実行中は generatingFieldIndex が対象 index になり完了後は null に戻る`() = runTest(testDispatcher) {
        // 【テスト目的】: EditFormState.generatingFieldIndex による、生成中→完了のローディング状態遷移
        //                （null→index→null）を確認する
        // 【テスト内容】: CompletableDeferred で rewrite() の完了タイミングを外部制御し、完了前後の2時点を観測する
        // 【期待される動作】: 呼び出し中は generatingFieldIndex == index、完了後は null かつ対象 value が更新される
        // 🟡 信頼性レベル: TASK-0072.md UI/UX要件・要件定義書§2「UI状態（オプション）」より妥当な推測（新規UI要素）

        // 【テストデータ準備】: 応答完了前後の2時点を観測するために完了を外部制御する
        val deferred = CompletableDeferred<LlmRewriteResult>()
        coEvery { mockRewrite.rewrite(any(), any(), any()) } coAnswers { deferred.await() }
        val fieldLlm = CustomFieldState("summary", "既存S", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出し、rewrite() が完了する前まで進める
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 応答完了前は generatingFieldIndex が対象 index であること
        assertEquals(
            "rewrite() 完了前は generatingFieldIndex が対象 index であること",
            0,
            viewModel.formState.value.generatingFieldIndex,
        ) // 【確認内容】: ローディング開始状態（対象 index）の検証 🟡

        // 【実際の処理実行】: rewrite() を完了させ、後続処理を進める
        deferred.complete(LlmRewriteResult.Success("結果"))
        advanceUntilIdle()

        // 【結果検証】: 応答完了後は generatingFieldIndex が null に戻り、対象 value が更新されること
        assertNull(
            "rewrite() 完了後は generatingFieldIndex が null に戻ること",
            viewModel.formState.value.generatingFieldIndex,
        ) // 【確認内容】: ローディング終了状態の検証 🟡
        assertEquals(
            "完了後は対象フィールドの value が生成結果に更新されること",
            "結果",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: ローディング解除と value 更新が一貫して行われること 🟡
    }

    // ================================================================
    // 2. 異常系テストケース
    // ================================================================

    // TC-0072-E01: NetworkError 時 value 不変・error_llm_network を emit 🔵
    @Test
    fun `TC-0072-E01 generateCustomFieldValue が NetworkError を返すと対象 value 不変で error_llm_network が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: NetworkError の errorEvents 発行と対象フィールド value 非破壊を確認する
        // 【エラーケースの概要】: ネットワーク未接続・接続失敗（EDGE-001）
        // 【エラー処理の重要性】: 失敗時に既存フィールド値を破壊せず、再試行可能な状態を保つため
        // 🔵 信頼性レベル: TASK-0072.md 単体テスト要件テストケース2・EDGE-001・NFR-201、既存 TC-0063-E01 と同型より

        // 【テストデータ準備】: NetworkError を返すスタブと初期値を持つ LLM フィールドを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.NetworkError(R.string.error_llm_network)
        val fieldLlm = CustomFieldState("summary", "既存値", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 対象 value が不変であり、error_llm_network が1件発行されること
        assertEquals(
            "NetworkError 時に対象フィールドの value が変更されないこと",
            "既存値",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: フィールド値非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_network が1件発行されること",
            listOf(R.string.error_llm_network),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0072-E02: AuthError 時 value 不変・error_llm_auth を emit 🔵
    @Test
    fun `TC-0072-E02 generateCustomFieldValue が AuthError を返すと対象 value 不変で error_llm_auth が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: AuthError の errorEvents 発行を確認する
        // 【エラーケースの概要】: APIキー不正・認証失敗（HTTP 401/403, EDGE-002）
        // 🔵 信頼性レベル: TASK-0072.md テストケース2・EDGE-002・NFR-201 より

        // 【テストデータ準備】: AuthError を返すスタブと初期値を持つ LLM フィールドを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.AuthError(R.string.error_llm_auth)
        val fieldLlm = CustomFieldState("summary", "既存値", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 対象 value が不変であり、error_llm_auth が1件発行されること
        assertEquals(
            "AuthError 時に対象フィールドの value が変更されないこと",
            "既存値",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: フィールド値非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_auth が1件発行されること",
            listOf(R.string.error_llm_auth),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0072-E03: Timeout 時 value 不変・error_llm_timeout を emit 🔵
    @Test
    fun `TC-0072-E03 generateCustomFieldValue が Timeout を返すと対象 value 不変で error_llm_timeout が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: Timeout の errorEvents 発行を確認する
        // 【エラーケースの概要】: 30秒タイムアウト（EDGE-003, NFR-001）
        // 🔵 信頼性レベル: TASK-0072.md テストケース2・EDGE-003・NFR-001 より

        // 【テストデータ準備】: Timeout を返すスタブと初期値を持つ LLM フィールドを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.Timeout(R.string.error_llm_timeout)
        val fieldLlm = CustomFieldState("summary", "既存値", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 対象 value が不変であり、error_llm_timeout が1件発行されること
        assertEquals(
            "Timeout 時に対象フィールドの value が変更されないこと",
            "既存値",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: フィールド値非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_timeout が1件発行されること",
            listOf(R.string.error_llm_timeout),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0072-E04: EmptyOrInvalidResponse 時 value 不変・error_llm_empty_response を emit 🔵
    @Test
    fun `TC-0072-E04 generateCustomFieldValue が EmptyOrInvalidResponse を返すと対象 value 不変で error_llm_empty_response が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: EmptyOrInvalidResponse の errorEvents 発行を確認する
        // 【エラーケースの概要】: 空応答・パース不能なレスポンス（EDGE-004）
        // 🔵 信頼性レベル: TASK-0072.md テストケース2・EDGE-004・NFR-201 より

        // 【テストデータ準備】: EmptyOrInvalidResponse を返すスタブと初期値を持つ LLM フィールドを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.EmptyOrInvalidResponse(R.string.error_llm_empty_response)
        val fieldLlm = CustomFieldState("summary", "既存値", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 対象 value が不変であり、error_llm_empty_response が1件発行されること
        assertEquals(
            "EmptyOrInvalidResponse 時に対象フィールドの value が変更されないこと",
            "既存値",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: フィールド値非破壊の確認 🔵
        assertEquals(
            "errorEvents から error_llm_empty_response が1件発行されること",
            listOf(R.string.error_llm_empty_response),
            received,
        ) // 【確認内容】: エラー種別に対応する messageResId の発行確認 🔵
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0072-E05: Unknown 時 value 不変・error_llm_unknown を emit 🔵
    @Test
    fun `TC-0072-E05 generateCustomFieldValue が Unknown を返すと対象 value 不変で error_llm_unknown が emit される`() = runTest(testDispatcher) {
        // 【テスト目的】: Unknown の errorEvents 発行を確認し、全 Failure 種別の網羅を完成させる
        // 【エラーケースの概要】: 上記以外の予期しないエラー
        // 🔵 信頼性レベル: TASK-0072.md テストケース2・NFR-201 より

        // 【テストデータ準備】: Unknown を返すスタブと初期値を持つ LLM フィールドを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns
            LlmRewriteResult.Failure.Unknown(R.string.error_llm_unknown)
        val fieldLlm = CustomFieldState("summary", "既存値", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 対象 value が不変であり、error_llm_unknown が1件発行されること
        assertEquals(
            "Unknown 時に対象フィールドの value が変更されないこと",
            "既存値",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: フィールド値非破壊の確認 🔵
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

    // TC-0072-B01: index が先頭(0)の LLM フィールドを更新できる 🟡
    @Test
    fun `TC-0072-B01 generateCustomFieldValue 0 で先頭フィールドの value が更新される`() = runTest(testDispatcher) {
        // 【テスト目的】: customFields インデックスの下限（0）でも正しく対象特定・更新されることを確認する
        // 【境界値の意味】: index の下限（0）。updateCustomField の index in indices 判定を通過することを保証する
        // 🟡 信頼性レベル: 完了条件・updateCustomField の index in fields.indices 実装より妥当な推測

        // 【テストデータ準備】: 先頭を LLM、2番目を FIXED としたリストを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("結果0")
        val fieldLlm = CustomFieldState("s", "既存", FieldValueType.STRING, FieldValueSource.LLM, "P")
        val fieldFixed = CustomFieldState("a", "既存A", FieldValueType.STRING, FieldValueSource.FIXED)
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm, fieldFixed),
            sourceContent = "元コンテンツ",
        )

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す（下限境界のインデックス）
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 先頭フィールドの value が更新され、2番目は不変であること
        assertEquals(
            "先頭インデックス(0)の value が生成結果に更新されること",
            "結果0",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: 下限境界での局所更新の正確性 🟡
        assertEquals(
            "2番目のフィールドは不変であること",
            fieldFixed,
            viewModel.formState.value.customFields[1],
        ) // 【確認内容】: 非対象フィールドの不変性 🟡
    }

    // TC-0072-B02: index が末尾の LLM フィールドを更新できる 🟡
    @Test
    fun `TC-0072-B02 generateCustomFieldValue lastIndex で末尾フィールドの value が更新される`() = runTest(testDispatcher) {
        // 【テスト目的】: customFields インデックスの上限（size - 1）でも先頭・中間と同一の局所更新が行われることを確認する
        // 【境界値の意味】: index の上限（size - 1）
        // 🟡 信頼性レベル: 完了条件・updateCustomField 実装より妥当な推測

        // 【テストデータ準備】: 末尾のみ LLM としたリストを用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("結果2")
        val field0 = CustomFieldState("a", "既存A", FieldValueType.STRING, FieldValueSource.FIXED)
        val field1 = CustomFieldState("b", "既存B", FieldValueType.STRING, FieldValueSource.FIXED)
        val fieldLlm = CustomFieldState("s", "既存", FieldValueType.STRING, FieldValueSource.LLM, "P")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "本文"),
            config = defaultConfig,
            customFields = listOf(field0, field1, fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【実際の処理実行】: generateCustomFieldValue(2) を呼び出す（上限境界のインデックス）
        viewModel.generateCustomFieldValue(2)
        advanceUntilIdle()

        // 【結果検証】: 末尾フィールドの value が更新され、他は不変であること
        assertEquals(
            "末尾インデックス(2)の value が生成結果に更新されること",
            "結果2",
            viewModel.formState.value.customFields[2].value,
        ) // 【確認内容】: 上限境界での局所更新の正確性 🟡
        assertEquals("先頭フィールドは不変であること", field0, viewModel.formState.value.customFields[0]) // 【確認内容】: 非対象フィールドの不変性 🟡
        assertEquals("中間フィールドは不変であること", field1, viewModel.formState.value.customFields[1]) // 【確認内容】: 非対象フィールドの不変性 🟡
    }

    // TC-0072-B03: Success("") 空応答で value が空文字に更新され、errorEvents は発行されない 🟡
    @Test
    fun `TC-0072-B03 generateCustomFieldValue が Success 空文字を返すと対象 value が空文字で上書きされエラー扱いにならない`() = runTest(testDispatcher) {
        // 【テスト目的】: 成功だが空応答という下限境界で、対象 value が空文字に更新されエラー扱いされないことを確認する
        // 【境界値の意味】: LlmRewriteResult.Success.text の下限（空文字 ""）
        // 🟡 信頼性レベル: LlmRewriteResult の Success 空文字許容・既存 TC-0063-B04 より妥当な推測

        // 【テストデータ準備】: 成功だが空応答という下限境界を用意する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("")
        val fieldLlm = CustomFieldState("summary", "既存値", FieldValueType.STRING, FieldValueSource.LLM, "要約プロンプト")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = "初期本文"),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "元コンテンツ",
        )

        // 【イベント購読準備】: errorEvents は emit 前に購読を開始する必要がある（Hot Flow）
        val (received, errorEventsJob) = collectErrorEvents(viewModel)

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: Success は空文字でも対象 value に反映され、エラー扱いしないことを確認する
        assertEquals(
            "Success(\"\") の場合 対象フィールドの value が空文字で上書きされること",
            "",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: 空応答成功時の value 上書き確認 🟡
        assertTrue(
            "Success(\"\") の場合 errorEvents には何も発行されないこと",
            received.isEmpty(),
        ) // 【確認内容】: 空応答成功と失敗種別の混同がないこと 🟡
        errorEventsJob.cancel() // 【後片付け】: 通常の launch で購読したジョブを明示的にキャンセルする
    }

    // TC-0072-B04: sourceContent が空文字でもガードされず rewrite が実行される 🟡
    @Test
    fun `TC-0072-B04 sourceContent 空文字でも generateCustomFieldValue はガードされず rewrite を呼ぶ`() = runTest(testDispatcher) {
        // 【テスト目的】: sourceContent が空文字でも早期returnせず rewrite が実行されることを確認する
        // 【境界値の意味】: sourceContent の最小値（空文字 ""）
        // 🟡 信頼性レベル: EDGE-101・既存 TC-0063-B02（rewriteBody 空文字非ガード）より妥当な推測

        // 【テストデータ準備】: 本文なし共有でもカスタムフィールド生成を試みるケースを代表する
        coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("結果")
        val fieldLlm = CustomFieldState("summary", "既存値", FieldValueType.STRING, FieldValueSource.LLM, "P")
        val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
        viewModel.initialize(
            processed = processedContent(body = ""),
            config = defaultConfig,
            customFields = listOf(fieldLlm),
            sourceContent = "",
        )

        // 【実際の処理実行】: generateCustomFieldValue(0) を呼び出す（早期returnされないことを確認）
        viewModel.generateCustomFieldValue(0)
        advanceUntilIdle()

        // 【結果検証】: 第3引数が空文字で実際に呼ばれ、対象 value が更新されること
        coVerify {
            mockRewrite.rewrite(any(), "P", "")
        } // 【確認内容】: 空文字でもガードされず rewrite が呼ばれること 🟡
        assertEquals(
            "sourceContent 空文字でも成功時は対象フィールドの value が更新されること",
            "結果",
            viewModel.formState.value.customFields[0].value,
        ) // 【確認内容】: 空文字ガード禁止時の正常フロー完走確認 🟡
    }
}
