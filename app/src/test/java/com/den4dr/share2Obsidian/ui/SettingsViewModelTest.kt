package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.data.datastore.NoteSettings
import com.den4dr.share2Obsidian.data.datastore.NoteSettingsRepository
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
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // uiState が DataStore の vault/folder を反映する（REQ-021）
    @Test
    fun uiState_reflectsRepositorySettings() = runTest {
        val repo = mockk<NoteSettingsRepository>()
        every { repo.getSettings() } returns flowOf(NoteSettings(vault = "MyVault", folder = "Notes"))
        val viewModel = SettingsViewModel(repo)

        val collected = mutableListOf<SettingsUiState>()
        val job = launch(dispatcher) { viewModel.uiState.collect { collected.add(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals("MyVault", collected.last().vault)
        assertEquals("Notes", collected.last().folder)
    }

    // updateVault が repository.saveVault を呼ぶ
    @Test
    fun updateVault_savesToRepository() = runTest {
        val repo = mockk<NoteSettingsRepository>(relaxed = true)
        every { repo.getSettings() } returns flowOf(NoteSettings())
        val viewModel = SettingsViewModel(repo)

        viewModel.updateVault("X")
        advanceUntilIdle()

        coVerify(timeout = 2000) { repo.saveVault("X") }
    }
}
