package com.den4dr.share2Obsidian.ui.template

import androidx.lifecycle.SavedStateHandle
import com.den4dr.share2Obsidian.data.repository.TemplateRepository
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.Template
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
}
