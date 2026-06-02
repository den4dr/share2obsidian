package com.den4dr.share2Obsidian.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.den4dr.share2Obsidian.data.repository.TemplateRepository
import com.den4dr.share2Obsidian.domain.model.Template
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemplateListUiState(
    val templates: List<Template> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class TemplateListViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
) : ViewModel() {

    val uiState: StateFlow<TemplateListUiState> = templateRepository
        .getAllTemplates()
        .map { templates -> TemplateListUiState(templates = templates) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplateListUiState())

    fun deleteTemplate(template: Template) {
        viewModelScope.launch(Dispatchers.IO) {
            templateRepository.deleteTemplate(template)
        }
    }
}
