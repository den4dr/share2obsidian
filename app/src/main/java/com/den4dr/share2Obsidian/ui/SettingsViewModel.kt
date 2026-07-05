package com.den4dr.share2Obsidian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.den4dr.share2Obsidian.data.datastore.NoteSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SettingsScreen で表示する vault/folder の状態。
 */
data class SettingsUiState(
    val vault: String = "",
    val folder: String = "",
)

/**
 * vault/folder のグローバル設定を DataStore 経由で読み書きする ViewModel（REQ-021）。
 * 入力変更時に即時保存する（保存ボタンなし）。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val noteSettingsRepository: NoteSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = noteSettingsRepository.getSettings()
        .map { SettingsUiState(vault = it.vault, folder = it.folder) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateVault(vault: String) {
        viewModelScope.launch(Dispatchers.IO) {
            noteSettingsRepository.saveVault(vault)
        }
    }

    fun updateFolder(folder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            noteSettingsRepository.saveFolder(folder)
        }
    }
}
