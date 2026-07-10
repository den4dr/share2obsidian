package com.den4dr.share2Obsidian.ui.template

import androidx.lifecycle.SavedStateHandle
import com.den4dr.share2Obsidian.data.repository.TemplateRepository
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateEditViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: TemplateRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        templateId: Long = TemplateEditViewModel.NEW_TEMPLATE_ID,
    ): TemplateEditViewModel = TemplateEditViewModel(
        templateRepository = repository,
        savedStateHandle = SavedStateHandle(mapOf("templateId" to templateId)),
    )

    // TC-1: updateName が uiState.name を更新
    @Test
    fun updateName_updatesUiState() {
        val viewModel = createViewModel()
        viewModel.updateName("Web記事")
        assertEquals("Web記事", viewModel.uiState.value.name)
    }

    // TC-2: addField がフィールドをリストに追加
    @Test
    fun addField_addsToFieldsList() {
        val viewModel = createViewModel()
        val field = TemplateFieldEditState(key = "source", valueSource = FieldValueSource.URL)
        viewModel.addField(field)
        assertEquals(1, viewModel.uiState.value.fields.size)
        assertEquals("source", viewModel.uiState.value.fields[0].key)
    }

    // TC-3: save() で repository.saveTemplate が呼ばれ isSaved=true になる
    @Test
    fun save_callsRepositoryAndSetsisSaved() {
        val viewModel = createViewModel()
        viewModel.updateName("テスト")
        coEvery { repository.saveTemplate(any()) } returns 1L

        viewModel.save()
        Thread.sleep(200)

        coVerify { repository.saveTemplate(any()) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    // TC-4: 既存テンプレート編集時に loadTemplate でフィールドが設定される（body 含む）
    @Test
    fun loadTemplate_setsUiStateFromRepository() {
        val template = Template(
            id = 1L,
            name = "既存テンプレート",
            body = "## 記事\n{{content}}",
            isDefault = true,
            fields = emptyList(),
        )
        coEvery { repository.getTemplateById(1L) } returns template

        val viewModel = createViewModel(templateId = 1L)
        Thread.sleep(200)

        assertEquals("既存テンプレート", viewModel.uiState.value.name)
        assertEquals("## 記事\n{{content}}", viewModel.uiState.value.body)
        assertTrue(viewModel.uiState.value.isDefault)
    }

    // TC-5: updateBody が uiState.body を更新する
    @Test
    fun updateBody_updatesUiState() {
        val viewModel = createViewModel()
        viewModel.updateBody("## メモ\n{{content}}")
        assertEquals("## メモ\n{{content}}", viewModel.uiState.value.body)
    }

    // TC-6: save() が body を含む Template を保存する
    @Test
    fun save_persistsBody() {
        val viewModel = createViewModel()
        viewModel.updateName("テスト")
        viewModel.updateBody("固定テキスト")
        val slot = mutableListOf<Template>()
        coEvery { repository.saveTemplate(capture(slot)) } returns 1L

        viewModel.save()
        Thread.sleep(200)

        assertEquals("固定テキスト", slot.last().body)
    }

    // ==================== TASK-0071: LLM UI追加 Redフェーズ追加テスト ====================

    // TC-N-01: updateBodyLlmPrompt が uiState.bodyLlmPrompt を更新する
    @Test
    fun updateBodyLlmPrompt_updatesUiState() {
        // 【テスト目的】: updateBodyLlmPrompt(prompt) 呼び出しで uiState.bodyLlmPrompt が渡した値になることを確認する
        // 【テスト内容】: 本文用LLMプロンプトの状態更新メソッドを呼び出し、uiState への反映を検証する
        // 【期待される動作】: _uiState.update { it.copy(bodyLlmPrompt = prompt) } により同期的に状態が更新される
        // 🔵 信頼性レベル: 要件定義書 6.1-1・note.md テストケース1・既存 updateBody_updatesUiState() 踏襲

        // 【テストデータ準備】: 本文リライト用の代表的なプロンプト文字列を用意する
        // 【初期条件設定】: 新規テンプレート状態の ViewModel を作成
        val viewModel = createViewModel()

        // 【実際の処理実行】: まだ実装されていない本文用LLMプロンプトの状態更新メソッドを呼び出す
        // 【処理内容】: updateBodyLlmPrompt() の呼び出し
        viewModel.updateBodyLlmPrompt("要約してください")

        // 【結果検証】: uiState.bodyLlmPrompt が渡した値になっていることを確認する
        // 【期待値確認】: 既存 updateBody() と同じ不変更新パターンに従うべきだから
        assertEquals("要約してください", viewModel.uiState.value.bodyLlmPrompt) // 【確認内容】: 本文用LLMプロンプトが状態に反映されること
    }

    // TC-N-02: save() で bodyLlmPrompt と各フィールドの llmPrompt が Template/TemplateField に反映される
    @Test
    fun save_reflectsBodyLlmPromptAndFieldLlmPrompt() {
        // 【テスト目的】: save() が構築する Template.bodyLlmPrompt と TemplateField.llmPrompt に状態値が正しく載ることを確認する
        // 【テスト内容】: bodyLlmPrompt と LLM生成フィールドの llmPrompt を設定し保存、渡された引数を検証する
        // 【期待される動作】: templateRepository.saveTemplate() へ渡される Template が両プロンプト値を保持する
        // 🔵 信頼性レベル: 要件定義書 6.1-2・note.md テストケース2

        // 【テストデータ準備】: 本文用とフィールド用の双方向マッピングを1回の保存で同時検証する最小構成
        // 【初期条件設定】: 新規テンプレート状態の ViewModel
        val viewModel = createViewModel()
        viewModel.updateName("テスト")
        viewModel.updateBodyLlmPrompt("本文用プロンプト")
        viewModel.addField(
            TemplateFieldEditState(
                key = "category",
                valueSource = FieldValueSource.LLM,
                llmPrompt = "フィールド用プロンプト",
            )
        )
        val slot = mutableListOf<Template>()
        coEvery { repository.saveTemplate(capture(slot)) } returns 1L

        // 【実際の処理実行】: save() を呼び出し viewModelScope.launch(IO) の完了を待機する
        // 【処理内容】: state から Template/TemplateField を構築してリポジトリへ保存する
        viewModel.save()
        Thread.sleep(200)

        // 【結果検証】: capture した Template の本文用・フィールド用プロンプトを確認する
        // 【期待値確認】: save() の Template/TemplateField 構築時に両プロンプトが反映される実装が求められるため
        assertEquals("本文用プロンプト", slot.last().bodyLlmPrompt) // 【確認内容】: 本文用プロンプトが Template に載ること
        assertEquals("フィールド用プロンプト", slot.last().fields[0].llmPrompt) // 【確認内容】: フィールド用プロンプトが TemplateField に載ること
        assertEquals(FieldValueSource.LLM, slot.last().fields[0].valueSource) // 【確認内容】: 値取得方法が LLM で保持されること
    }

    // TC-N-03: loadTemplate() で既存テンプレートの bodyLlmPrompt/llmPrompt が復元される
    @Test
    fun loadTemplate_restoresBodyLlmPromptAndFieldLlmPrompt() {
        // 【テスト目的】: リポジトリが返す Template.bodyLlmPrompt と TemplateField.llmPrompt が uiState に反映されることを確認する
        // 【テスト内容】: 保存済みデータからの復元（往復）を検証する代表ケース
        // 【期待される動作】: getTemplateById() の結果から uiState.bodyLlmPrompt と fields[i].llmPrompt が設定される
        // 🔵 信頼性レベル: 要件定義書 6.1-3・note.md テストケース3・既存 loadTemplate_setsUiStateFromRepository() 踏襲

        // 【テストデータ準備】: 保存済み本文プロンプトとフィールドプロンプトを持つテンプレートを用意する
        // 【初期条件設定】: リポジトリが返却するテンプレートをモックする
        val template = Template(
            id = 1L,
            name = "既存テンプレート",
            body = "## 記事\n{{content}}",
            bodyLlmPrompt = "保存済み本文プロンプト",
            isDefault = true,
            fields = listOf(
                TemplateField(
                    id = 1L,
                    templateId = 1L,
                    key = "category",
                    valueSource = FieldValueSource.LLM,
                    valueType = FieldValueType.STRING,
                    llmPrompt = "保存済みフィールドプロンプト",
                )
            ),
        )
        coEvery { repository.getTemplateById(1L) } returns template

        // 【実際の処理実行】: templateId=1L で ViewModel を生成し init の loadTemplate 完了を待機する
        // 【処理内容】: getTemplateById() の結果を uiState にマッピングする
        val viewModel = createViewModel(templateId = 1L)
        Thread.sleep(200)

        // 【結果検証】: uiState に両プロンプトが復元されていることを確認する
        // 【期待値確認】: loadTemplate() のマッピングに両プロンプトの復元を追加する実装が求められるため
        assertEquals("保存済み本文プロンプト", viewModel.uiState.value.bodyLlmPrompt) // 【確認内容】: 本文用プロンプトが復元されること
        assertEquals("保存済みフィールドプロンプト", viewModel.uiState.value.fields[0].llmPrompt) // 【確認内容】: フィールド用プロンプトが復元されること
        assertEquals(FieldValueSource.LLM, viewModel.uiState.value.fields[0].valueSource) // 【確認内容】: 値取得方法が LLM で保持されること
    }

    // TC-E-01: 存在しないテンプレートID読込時、LLMプロンプトも含めて例外なく初期値のまま維持される
    @Test
    fun loadTemplate_missingTemplate_bodyLlmPromptStaysDefault() {
        // 【テスト目的】: getTemplateById(id) が null を返す場合、loadTemplate は return@launch で復元をスキップし例外を出さないことを確認する
        // 【テスト内容】: LLMプロンプトマッピング追加後も既存の null 安全挙動が退行していないかを検証する
        // 【期待される動作】: 例外が発生せず uiState.bodyLlmPrompt は初期値の空文字のまま
        // 🔵 信頼性レベル: 要件定義書 4.4・既存 loadTemplate() の ?: return@launch 実装

        // 【テストデータ準備】: 削除済み等で存在しないIDが渡される想定
        // 【初期条件設定】: getTemplateById が null を返すようモックする
        coEvery { repository.getTemplateById(99L) } returns null

        // 【実際の処理実行】: templateId=99L で ViewModel を生成し loadTemplate の完了を待機する
        // 【処理内容】: null 応答時は uiState を初期値のまま維持する
        val viewModel = createViewModel(templateId = 99L)
        Thread.sleep(200)

        // 【結果検証】: 例外が発生せず uiState が初期値のままであることを確認する
        // 【期待値確認】: LLMマッピング追加が既存の防御的分岐を壊していないこと
        assertEquals("", viewModel.uiState.value.bodyLlmPrompt) // 【確認内容】: bodyLlmPrompt が初期値の空文字であること
        assertTrue(viewModel.uiState.value.fields.isEmpty()) // 【確認内容】: fields が空のままであること
    }

    // TC-E-03: LLMプロンプト追加後も保存フローの状態遷移(isSaving/isSaved)が維持される（退行確認）
    @Test
    fun save_withBodyLlmPrompt_stillSetsIsSavedRegression() {
        // 【テスト目的】: save() 改修により既存の isSaving/isSaved 遷移が壊れないことを保証する
        // 【テスト内容】: bodyLlmPrompt を設定した状態で save() を呼び出し、既存の保存完了状態遷移を検証する
        // 【期待される動作】: 保存中UI制御（多重保存防止）を退行させない
        // 🔵 信頼性レベル: 既存 save_callsRepositoryAndSetsisSaved()・完了条件

        // 【テストデータ準備】: 通常保存操作を想定した最小構成
        // 【初期条件設定】: 新規テンプレート状態の ViewModel
        val viewModel = createViewModel()
        viewModel.updateName("テスト")
        viewModel.updateBodyLlmPrompt("p")
        coEvery { repository.saveTemplate(any()) } returns 1L

        // 【実際の処理実行】: save() を呼び出し viewModelScope.launch(IO) の完了を待機する
        // 【処理内容】: LLMマッピング追加後の保存処理を実行する
        viewModel.save()
        Thread.sleep(200)

        // 【結果検証】: 保存完了状態が確定していることを確認する
        // 【期待値確認】: 完了条件「既存の単体テスト・統合テストがすべて通る」の担保
        coVerify { repository.saveTemplate(any()) } // 【確認内容】: saveTemplate が呼ばれること
        assertTrue(viewModel.uiState.value.isSaved) // 【確認内容】: isSaved が true になること
    }

    // TC-B-01: bodyLlmPrompt 未入力（空文字）でも例外なく保存される（境界値）
    @Test
    fun save_bodyLlmPromptEmptyByDefault_savesEmptyString() {
        // 【テスト目的】: 空文字は「未設定」を表す仕様上の重要な境界であることを確認する（REQ-102）
        // 【テスト内容】: updateBodyLlmPrompt を呼ばずに保存し、Template.bodyLlmPrompt が空文字のまま保存されることを検証する
        // 【期待される動作】: 未入力でも保存が成功し bodyLlmPrompt == "" が保持される
        // 🔵 信頼性レベル: 要件定義書 3（後方互換制約）・4.3 EDGE・REQ-102

        // 【テストデータ準備】: デフォルト空文字。既存テンプレート（LLM未対応時代のデータ）互換の下限を想定する
        // 【初期条件設定】: 新規テンプレート状態の ViewModel（bodyLlmPrompt は未設定のまま）
        val viewModel = createViewModel()
        viewModel.updateName("テスト")
        val slot = mutableListOf<Template>()
        coEvery { repository.saveTemplate(capture(slot)) } returns 1L

        // 【実際の処理実行】: save() を呼び出す
        // 【処理内容】: bodyLlmPrompt を変更せず保存処理を実行する
        viewModel.save()
        Thread.sleep(200)

        // 【結果検証】: 空文字がそのまま保存されることを確認する
        // 【期待値確認】: 空文字/非空文字で保存経路が分岐しないこと
        assertEquals("", slot.last().bodyLlmPrompt) // 【確認内容】: bodyLlmPrompt が空文字のまま保存されること
        assertTrue(viewModel.uiState.value.isSaved) // 【確認内容】: 保存フローが破綻しないこと
    }

    // TC-B-02: LLM選択かつプロンプト空のフィールドも保存・復元できる（境界値）
    @Test
    fun save_llmFieldWithEmptyPrompt_preservesEmptyLlmPrompt() {
        // 【テスト目的】: valueSource == LLM かつ llmPrompt == "" の組み合わせ境界を検証する
        // 【テスト内容】: LLM選択直後（未入力）のフィールドを追加し保存、空文字が破棄も改変もされず保持されることを確認する
        // 【期待される動作】: 空プロンプトのLLMフィールドも保存・復元で往復できる（生成挙動は本タスク対象外）
        // 🟡 信頼性レベル: 要件定義書 4.3 EDGE（保存・復元部分は🔵、生成挙動は対象外）

        // 【テストデータ準備】: フィールド追加後にプロンプト入力を保留したケースを想定する
        // 【初期条件設定】: 新規テンプレート状態の ViewModel
        val viewModel = createViewModel()
        viewModel.updateName("テスト")
        viewModel.addField(
            TemplateFieldEditState(key = "k", valueSource = FieldValueSource.LLM, llmPrompt = "")
        )
        val slot = mutableListOf<Template>()
        coEvery { repository.saveTemplate(capture(slot)) } returns 1L

        // 【実際の処理実行】: save() を呼び出す
        // 【処理内容】: LLM選択かつ空プロンプトのフィールドをそのまま保存する
        viewModel.save()
        Thread.sleep(200)

        // 【結果検証】: 空プロンプトが破棄も改変もされず保持されることを確認する
        // 【期待値確認】: 生成ロジック未実装でも保存層が安定していること
        assertEquals(FieldValueSource.LLM, slot.last().fields[0].valueSource) // 【確認内容】: 値取得方法が LLM のまま保持されること
        assertEquals("", slot.last().fields[0].llmPrompt) // 【確認内容】: 空プロンプトがそのまま保持されること
    }

    // TC-B-03: 複数フィールド（LLM含む混在）で sortOrder とマッピングが崩れない（境界値）
    @Test
    fun save_multipleFieldsWithLlmMixed_preservesLlmPromptMapping() {
        // 【テスト目的】: save() の mapIndexed で各フィールドの llmPrompt が正しいインデックスに対応することを確認する
        // 【テスト内容】: 非LLMフィールドとLLMフィールドを順に追加して保存し、インデックスずれが発生しないことを検証する
        // 【期待される動作】: 各フィールドが自身のプロンプトを保持し、追加順が保存順に一致する
        // 🟡 信頼性レベル: 既存 save() の mapIndexed 実装＋完了条件からの妥当推測

        // 【テストデータ準備】: 単一フィールドでは検出できないインデックスずれを検出するための複数件構成
        // 【初期条件設定】: 新規テンプレート状態の ViewModel
        val viewModel = createViewModel()
        viewModel.updateName("テスト")
        viewModel.addField(TemplateFieldEditState(key = "src", valueSource = FieldValueSource.URL))
        viewModel.addField(
            TemplateFieldEditState(key = "cat", valueSource = FieldValueSource.LLM, llmPrompt = "推測して")
        )
        val slot = mutableListOf<Template>()
        coEvery { repository.saveTemplate(capture(slot)) } returns 1L

        // 【実際の処理実行】: save() を呼び出す
        // 【処理内容】: 複数フィールドを持つ状態を保存する
        viewModel.save()
        Thread.sleep(200)

        // 【結果検証】: 各フィールドが自身のプロンプトを保持していることを確認する
        // 【期待値確認】: LLMマッピング追加が既存の mapIndexed(sortOrder=index) を壊していないこと
        assertEquals("src", slot.last().fields[0].key) // 【確認内容】: 1件目のフィールドキーが順序通りであること
        assertEquals("", slot.last().fields[0].llmPrompt) // 【確認内容】: 非LLMフィールドの llmPrompt は空文字であること
        assertEquals("cat", slot.last().fields[1].key) // 【確認内容】: 2件目のフィールドキーが順序通りであること
        assertEquals("推測して", slot.last().fields[1].llmPrompt) // 【確認内容】: LLMフィールドの llmPrompt が保持されること
        assertEquals(FieldValueSource.LLM, slot.last().fields[1].valueSource) // 【確認内容】: 値取得方法が LLM で保持されること
        assertEquals(0, slot.last().fields[0].sortOrder) // 【確認内容】: sortOrder が index と一致すること
        assertEquals(1, slot.last().fields[1].sortOrder) // 【確認内容】: sortOrder が index と一致すること
    }
}
