package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.data.datastore.NoteSettings
import com.den4dr.share2Obsidian.data.datastore.NoteSettingsRepository
import com.den4dr.share2Obsidian.data.llm.LlmSettings
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // 【テスト前準備】: viewModelScope の Main ディスパッチャをテスト用に差し替える
        // 【環境初期化】: UnconfinedTestDispatcher で launch を即時実行可能にする
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        // 【テスト後処理】: Main ディスパッチャを元に戻す
        // 【状態復元】: 次テストへの影響とメモリリークを防ぐ（必須）
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------
    // 既存テスト（TC-E-02: 2引数コンストラクタへの移行・回帰防止）
    //
    // 【テスト目的】: SettingsViewModel のコンストラクタに llmSettingsRepository を追加した後も、
    //                既存の vault/folder に関するテストが引き続き成功することを確認する
    // 【期待される動作】: 2引数コンストラクタ (noteRepo, llmRepo) で既存機能が壊れないこと
    // 🟡 信頼性レベル: testcases.md TC-E-02・要件 §6 補足より妥当な推測
    //
    // 現時点では SettingsViewModel のコンストラクタが1引数のみのため、
    // 以下2ケースは「2引数を渡す」時点でコンパイルエラーとなり失敗する（Redフェーズの想定通り）。
    // ------------------------------------------------------------------

    // uiState が DataStore の vault/folder を反映する（REQ-021）
    @Test
    fun uiState_reflectsRepositorySettings() = runTest {
        val repo = mockk<NoteSettingsRepository>()
        every { repo.getSettings() } returns flowOf(NoteSettings(vault = "MyVault", folder = "Notes"))
        val llmRepo = mockk<LlmSettingsRepository>()
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(repo, llmRepo)

        val collected = mutableListOf<SettingsUiState>()
        val job = launch(dispatcher) { viewModel.uiState.collect { collected.add(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals("MyVault", collected.last().vault) // 【確認内容】: 既存のvault反映機能が壊れていないことを確認
        assertEquals("Notes", collected.last().folder) // 【確認内容】: 既存のfolder反映機能が壊れていないことを確認
    }

    // updateVault が repository.saveVault を呼ぶ
    @Test
    fun updateVault_savesToRepository() = runTest {
        val repo = mockk<NoteSettingsRepository>(relaxed = true)
        every { repo.getSettings() } returns flowOf(NoteSettings())
        val llmRepo = mockk<LlmSettingsRepository>(relaxed = true)
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(repo, llmRepo)

        viewModel.updateVault("X")
        advanceUntilIdle()

        coVerify(timeout = 2000) { repo.saveVault("X") } // 【確認内容】: 既存のupdateVault機能が壊れていないことを確認
    }

    // ------------------------------------------------------------------
    // TC-N-01: updateLlmEndpointUrl() が saveEndpointUrl() を呼び出す
    // ------------------------------------------------------------------
    @Test
    fun updateLlmEndpointUrl_savesToRepository() = runTest {
        // 【テスト目的】: updateLlmEndpointUrl が saveEndpointUrl へ引数をそのまま委譲することを確認する
        // 【テスト内容】: URL を1つ渡し、Repository の save が同一引数で1回呼ばれるか検証する
        // 【期待される動作】: llmSettingsRepository.saveEndpointUrl(url) が exactly=1 で呼び出される
        // 🔵 信頼性レベル: TASK-0066.md TC-1・testcases.md TC-N-01・既存 updateVault テストパターンより

        // 【テストデータ準備】: note/llm 双方をモック化。getSettings はデフォルト値を返す
        // 【初期条件設定】: save 系は relaxed で副作用を無効化
        val noteRepo = mockk<NoteSettingsRepository>(relaxed = true)
        every { noteRepo.getSettings() } returns flowOf(NoteSettings())
        val llmRepo = mockk<LlmSettingsRepository>(relaxed = true)
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(noteRepo, llmRepo)

        // 【実際の処理実行】: update系関数を呼び出す
        // 【処理内容】: viewModelScope.launch(Dispatchers.IO) で save 系が実行される
        viewModel.updateLlmEndpointUrl("https://example.com/v1/chat/completions")
        advanceUntilIdle()

        // 【結果検証】: save が正しい引数で1回呼ばれたか確認
        // 【期待値確認】: 引数一致・回数1回
        coVerify(exactly = 1, timeout = 2000) {
            llmRepo.saveEndpointUrl("https://example.com/v1/chat/completions")
        } // 【確認内容】: updateLlmEndpointUrl の引数がそのまま saveEndpointUrl に委譲されることを確認
    }

    // ------------------------------------------------------------------
    // TC-N-02: updateLlmApiKey() が saveApiKey() を呼び出す
    // ------------------------------------------------------------------
    @Test
    fun updateLlmApiKey_savesToRepository() = runTest {
        // 【テスト目的】: updateLlmApiKey が saveApiKey へ引数をそのまま委譲することを確認する
        // 【テスト内容】: APIキー文字列を1つ渡し、Repository の save が同一引数で1回呼ばれるか検証する
        // 【期待される動作】: ViewModel が APIキーを恒久保持せず、そのまま save 系へ委譲する
        // 🔵 信頼性レベル: TASK-0066.md TC-2・testcases.md TC-N-02・REQ-401より

        // 【テストデータ準備】: note/llm 双方をモック化
        // 【初期条件設定】: save系はrelaxedで副作用を無効化
        val noteRepo = mockk<NoteSettingsRepository>(relaxed = true)
        every { noteRepo.getSettings() } returns flowOf(NoteSettings())
        val llmRepo = mockk<LlmSettingsRepository>(relaxed = true)
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(noteRepo, llmRepo)

        // 【実際の処理実行】: updateLlmApiKey を呼び出す
        // 【処理内容】: viewModelScope.launch(Dispatchers.IO) で saveApiKey が実行される
        viewModel.updateLlmApiKey("sk-xxxx")
        advanceUntilIdle()

        // 【結果検証】: saveApiKey が正しい引数で1回呼ばれたか確認
        // 【期待値確認】: 機微情報が改変されずそのまま委譲されること
        coVerify(exactly = 1, timeout = 2000) {
            llmRepo.saveApiKey("sk-xxxx")
        } // 【確認内容】: updateLlmApiKey の引数がそのまま saveApiKey に委譲されることを確認
    }

    // ------------------------------------------------------------------
    // TC-N-03: updateLlmModel() が saveModel() を呼び出す
    // ------------------------------------------------------------------
    @Test
    fun updateLlmModel_savesToRepository() = runTest {
        // 【テスト目的】: updateLlmModel が saveModel へ引数をそのまま委譲することを確認する
        // 【テスト内容】: モデル名を1つ渡し、Repository の save が同一引数で1回呼ばれるか検証する
        // 【期待される動作】: llmSettingsRepository.saveModel(model) が exactly=1 で呼び出される
        // 🔵 信頼性レベル: TASK-0066.md TC-3・testcases.md TC-N-03より

        // 【テストデータ準備】: note/llm 双方をモック化
        // 【初期条件設定】: save系はrelaxedで副作用を無効化
        val noteRepo = mockk<NoteSettingsRepository>(relaxed = true)
        every { noteRepo.getSettings() } returns flowOf(NoteSettings())
        val llmRepo = mockk<LlmSettingsRepository>(relaxed = true)
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(noteRepo, llmRepo)

        // 【実際の処理実行】: updateLlmModel を呼び出す
        // 【処理内容】: viewModelScope.launch(Dispatchers.IO) で saveModel が実行される
        viewModel.updateLlmModel("gpt-4o-mini")
        advanceUntilIdle()

        // 【結果検証】: saveModel が正しい引数で1回呼ばれたか確認
        // 【期待値確認】: 引数一致・回数1回
        coVerify(exactly = 1, timeout = 2000) {
            llmRepo.saveModel("gpt-4o-mini")
        } // 【確認内容】: updateLlmModel の引数がそのまま saveModel に委譲されることを確認
    }

    // ------------------------------------------------------------------
    // TC-N-04: uiState が note/llm 両 Repository の値を反映して構築される
    // ------------------------------------------------------------------
    @Test
    fun uiState_reflectsBothNoteAndLlmRepositorySettings() = runTest {
        // 【テスト目的】: combine(note.getSettings(), llm.getSettings()) が単一の SettingsUiState に
        //                両Repositoryの値を正しく集約することを確認する
        // 【テスト内容】: note/llm 双方に異なる値を設定し、uiState の最終値を構造的等価性で検証する
        // 【期待される動作】: 5フィールド全てが正しいソースにマッピングされる
        // 🔵 信頼性レベル: TASK-0066.md TC-4・testcases.md TC-N-04・要件2.5データフローより

        // 【テストデータ準備】: 2つの独立したFlowが異なる値を持つ状態を用意する
        // 【初期条件設定】: フィールド取り違え（例: model と endpointUrl の逆マッピング）を検出できる代表値を使用
        val noteRepo = mockk<NoteSettingsRepository>()
        every { noteRepo.getSettings() } returns flowOf(NoteSettings(vault = "MyVault", folder = "Inbox"))
        val llmRepo = mockk<LlmSettingsRepository>()
        every { llmRepo.getSettings() } returns flowOf(
            LlmSettings(endpointUrl = "https://example.com", apiKey = "key", model = "gpt-4o"),
        )
        val viewModel = SettingsViewModel(noteRepo, llmRepo)

        // 【実際の処理実行】: uiState を収集する
        // 【処理内容】: combine された StateFlow から最新値を取得する
        val collected = mutableListOf<SettingsUiState>()
        val job = launch(dispatcher) { viewModel.uiState.collect { collected.add(it) } }
        advanceUntilIdle()
        job.cancel()

        // 【結果検証】: combine による集約後の最終状態を data class 一括比較で検証
        // 【期待値確認】: 5フィールドすべてが正しいソースにマッピングされている
        assertEquals(
            SettingsUiState(
                vault = "MyVault",
                folder = "Inbox",
                llmEndpointUrl = "https://example.com",
                llmApiKey = "key",
                llmModel = "gpt-4o",
            ),
            collected.last(),
        ) // 【確認内容】: SettingsUiState 全体の構造的等価性を確認
    }

    // ------------------------------------------------------------------
    // TC-N-05: 既存 vault/folder 設定が引き続き uiState に反映される（後方互換）
    // ------------------------------------------------------------------
    @Test
    fun uiState_reflectsNoteSettingsWhenLlmSettingsAreDefault() = runTest {
        // 【テスト目的】: llmSettingsRepository 追加後も vault/folder の読み取り機能が破壊されていないことを確認する
        // 【テスト内容】: LLM 設定が未設定（デフォルト値）の状態で、Note設定のみが正しく反映されるか検証する
        // 【期待される動作】: 既存機能（REQ-021）が維持される
        // 🟡 信頼性レベル: testcases.md TC-N-05・要件§3後方互換制約より妥当な推測

        // 【テストデータ準備】: LLM未設定でもNote設定が独立して反映されることを確認する組み合わせ
        // 【初期条件設定】: llmは全デフォルト値（""）
        val noteRepo = mockk<NoteSettingsRepository>()
        every { noteRepo.getSettings() } returns flowOf(NoteSettings(vault = "V1", folder = "F1"))
        val llmRepo = mockk<LlmSettingsRepository>()
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(noteRepo, llmRepo)

        // 【実際の処理実行】: uiState を収集する
        // 【処理内容】: combine された StateFlow から最新値を取得する
        val collected = mutableListOf<SettingsUiState>()
        val job = launch(dispatcher) { viewModel.uiState.collect { collected.add(it) } }
        advanceUntilIdle()
        job.cancel()

        // 【結果検証】: vault/folderがLLM設定に影響されず正しく反映されることを確認
        // 【期待値確認】: LLM3項目は初期値の""のまま
        assertEquals("V1", collected.last().vault) // 【確認内容】: 既存のvault反映が壊れていないことを確認
        assertEquals("F1", collected.last().folder) // 【確認内容】: 既存のfolder反映が壊れていないことを確認
        assertEquals("", collected.last().llmEndpointUrl) // 【確認内容】: LLM未設定時にendpointUrlが空文字であることを確認
        assertEquals("", collected.last().llmApiKey) // 【確認内容】: LLM未設定時にapiKeyが空文字であることを確認
        assertEquals("", collected.last().llmModel) // 【確認内容】: LLM未設定時にmodelが空文字であることを確認
    }

    // ------------------------------------------------------------------
    // TC-B-01: 空文字入力を変換せずそのまま保存する
    // ------------------------------------------------------------------
    @Test
    fun updateLlmApiKey_savesEmptyStringAsIs() = runTest {
        // 【テスト目的】: 空文字による「未設定・クリア操作」がバリデーションで弾かれず、そのままsave系に委譲されることを確認する
        // 【テスト内容】: updateLlmApiKey("") を呼び出し、saveApiKey("") が呼ばれるか検証する
        // 【期待される動作】: 空文字が非空文字と同じ委譲経路を通る（分岐処理なし）
        // 🟡 信頼性レベル: testcases.md TC-B-01・要件§4.3空文字入力エッジケースより妥当な推測

        // 【テストデータ準備】: note/llm 双方をモック化
        // 【初期条件設定】: save系はrelaxedで副作用を無効化
        val noteRepo = mockk<NoteSettingsRepository>(relaxed = true)
        every { noteRepo.getSettings() } returns flowOf(NoteSettings())
        val llmRepo = mockk<LlmSettingsRepository>(relaxed = true)
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(noteRepo, llmRepo)

        // 【実際の処理実行】: 空文字でupdateLlmApiKeyを呼び出す
        // 【処理内容】: クリア操作を想定した空文字の委譲を検証する
        viewModel.updateLlmApiKey("")
        advanceUntilIdle()

        // 【結果検証】: 空文字がそのままsaveApiKeyに渡されたか確認
        // 【期待値確認】: 例外・スキップが発生せず1回呼ばれること
        coVerify(exactly = 1, timeout = 2000) {
            llmRepo.saveApiKey("")
        } // 【確認内容】: 空文字入力でもバリデーションで弾かれずそのまま委譲されることを確認
    }

    // ------------------------------------------------------------------
    // TC-B-02: 初期未保存状態では uiState の LLM 3項目が "" になる
    // ------------------------------------------------------------------
    @Test
    fun uiState_isAllEmptyWhenBothRepositoriesReturnDefaults() = runTest {
        // 【テスト目的】: 両Repositoryがデフォルト値を返す（アプリ初回起動時等）場合、uiStateが全項目空文字になることを確認する
        // 【テスト内容】: note/llm 双方をデフォルト値で構成し、uiStateの最終値を検証する
        // 【期待される動作】: 初期値 SettingsUiState() と実emit後の値が一致する
        // 🟡 信頼性レベル: testcases.md TC-B-02・要件§4.3初期状態エッジケースより妥当な推測

        // 【テストデータ準備】: note/llm 双方をデフォルト値（全""）で用意する
        // 【初期条件設定】: インストール直後・未設定の状態を再現する
        val noteRepo = mockk<NoteSettingsRepository>()
        every { noteRepo.getSettings() } returns flowOf(NoteSettings())
        val llmRepo = mockk<LlmSettingsRepository>()
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(noteRepo, llmRepo)

        // 【実際の処理実行】: uiState を収集する
        // 【処理内容】: combine された StateFlow から最新値を取得する
        val collected = mutableListOf<SettingsUiState>()
        val job = launch(dispatcher) { viewModel.uiState.collect { collected.add(it) } }
        advanceUntilIdle()
        job.cancel()

        // 【結果検証】: 全フィールドが空文字のデフォルト状態と一致することを確認
        // 【期待値確認】: combineが空値を欠損なくマッピングすること
        assertEquals(SettingsUiState(), collected.last()) // 【確認内容】: 未設定状態の安全な既定挙動（null非使用）を確認
    }

    // ------------------------------------------------------------------
    // TC-B-03: 同一項目の連続更新で save が呼び出し回数分だけ実行される
    // ------------------------------------------------------------------
    @Test
    fun updateLlmModel_calledTwice_savesTwice() = runTest {
        // 【テスト目的】: 短時間の連続更新（デバウンスなし）で、呼び出し回数とsave実行回数が一致することを確認する
        // 【テスト内容】: updateLlmModelを2回連続で呼び出し、saveModelが2回呼ばれ、最後の引数が正しいか検証する
        // 【期待される動作】: 呼び出しごとに独立したlaunch(Dispatchers.IO)が発行され、間引き・重複が起きない
        // 🟡 信頼性レベル: testcases.md TC-B-03・要件§4.3連続更新エッジケースより妥当な推測

        // 【テストデータ準備】: note/llm 双方をモック化
        // 【初期条件設定】: save系はrelaxedで副作用を無効化
        val noteRepo = mockk<NoteSettingsRepository>(relaxed = true)
        every { noteRepo.getSettings() } returns flowOf(NoteSettings())
        val llmRepo = mockk<LlmSettingsRepository>(relaxed = true)
        every { llmRepo.getSettings() } returns flowOf(LlmSettings())
        val viewModel = SettingsViewModel(noteRepo, llmRepo)

        // 【実際の処理実行】: updateLlmModelを2回連続で呼び出す（ユーザーが素早く打ち替える操作を想定）
        // 【処理内容】: 各呼び出しが独立にlaunch(Dispatchers.IO)で保存される
        viewModel.updateLlmModel("gpt-4o")
        viewModel.updateLlmModel("gpt-4o-mini")
        advanceUntilIdle()

        // 【結果検証】: saveModelが2回呼ばれ、最後の引数が正しいことを確認
        // 【期待値確認】: 呼び出し回数とsave実行回数が一致すること
        coVerify(exactly = 2, timeout = 2000) { llmRepo.saveModel(any()) } // 【確認内容】: 呼び出し回数分だけsaveが実行されることを確認
        coVerify(timeout = 2000) { llmRepo.saveModel("gpt-4o-mini") } // 【確認内容】: 最後に渡した引数が保存されていることを確認
    }
}
