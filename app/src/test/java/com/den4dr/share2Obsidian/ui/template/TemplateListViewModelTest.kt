package com.den4dr.share2Obsidian.ui.template

import com.den4dr.share2Obsidian.data.repository.TemplateRepository
import com.den4dr.share2Obsidian.domain.model.Template
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: TemplateRepository = mockk()
    private lateinit var viewModel: TemplateListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllTemplates() } returns flowOf(emptyList())
        viewModel = TemplateListViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TC-1: uiState がテンプレートリストを反映
    @Test
    fun uiState_reflectsTemplateList() = runTest(testDispatcher) {
        val templates = listOf(
            Template(id = 1L, name = "テスト", vault = "v", folder = "f", isDefault = false, fields = emptyList())
        )
        every { repository.getAllTemplates() } returns flowOf(templates)
        val vm = TemplateListViewModel(repository)

        val states = mutableListOf<TemplateListUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()
        job.cancel()

        assertTrue("uiState にテンプレートが反映されること", states.any { it.templates == templates })
    }

    // TC-2: deleteTemplate が repository.deleteTemplate を呼ぶ
    @Test
    fun deleteTemplate_callsRepository() {
        val template = Template(
            id = 1L, name = "テスト", vault = "v", folder = "f", isDefault = false, fields = emptyList()
        )
        coEvery { repository.deleteTemplate(template) } just Runs

        viewModel.deleteTemplate(template)
        // Dispatchers.IO で起動したコルーチンが完了するまで待機
        Thread.sleep(200)

        coVerify { repository.deleteTemplate(template) }
    }
}
