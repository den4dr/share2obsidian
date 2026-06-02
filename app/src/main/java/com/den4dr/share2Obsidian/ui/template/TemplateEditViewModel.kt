package com.den4dr.share2Obsidian.ui.template

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.den4dr.share2Obsidian.data.repository.TemplateRepository
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemplateEditUiState(
    val templateId: Long? = null,
    val name: String = "",
    val vault: String = "",
    val folder: String = "",
    val isDefault: Boolean = false,
    val fields: List<TemplateFieldEditState> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
)

data class TemplateFieldEditState(
    val id: Long = 0,
    val key: String = "",
    val valueSource: FieldValueSource = FieldValueSource.EMPTY,
    val valueType: FieldValueType = FieldValueType.STRING,
    val defaultValue: String = "",
    val metaKey: HtmlMetaKey? = null,
    val sortOrder: Int = 0,
)

@HiltViewModel
class TemplateEditViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        const val NEW_TEMPLATE_ID = -1L
    }

    private val templateId: Long? = savedStateHandle.get<Long>("templateId")
        ?.takeIf { it != NEW_TEMPLATE_ID }

    private val _uiState = MutableStateFlow(TemplateEditUiState(templateId = templateId))
    val uiState: StateFlow<TemplateEditUiState> = _uiState.asStateFlow()

    init {
        templateId?.let { loadTemplate(it) }
    }

    private fun loadTemplate(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val template = templateRepository.getTemplateById(id) ?: return@launch
            _uiState.update {
                TemplateEditUiState(
                    templateId = template.id,
                    name = template.name,
                    vault = template.vault,
                    folder = template.folder,
                    isDefault = template.isDefault,
                    fields = template.fields.map { field ->
                        TemplateFieldEditState(
                            id = field.id,
                            key = field.key,
                            valueSource = field.valueSource,
                            valueType = field.valueType,
                            defaultValue = field.defaultValue,
                            metaKey = field.metaKey,
                            sortOrder = field.sortOrder,
                        )
                    },
                )
            }
        }
    }

    fun loadTemplateById(id: Long) {
        _uiState.value = TemplateEditUiState(templateId = id)
        loadTemplate(id)
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateVault(vault: String) = _uiState.update { it.copy(vault = vault) }
    fun updateFolder(folder: String) = _uiState.update { it.copy(folder = folder) }
    fun updateIsDefault(isDefault: Boolean) = _uiState.update { it.copy(isDefault = isDefault) }

    fun addField(field: TemplateFieldEditState) = _uiState.update {
        it.copy(fields = it.fields + field)
    }

    fun removeField(index: Int) = _uiState.update {
        val list = it.fields.toMutableList().also { l -> l.removeAt(index) }
        it.copy(fields = list)
    }

    fun updateField(index: Int, field: TemplateFieldEditState) = _uiState.update {
        val list = it.fields.toMutableList().also { l -> l[index] = field }
        it.copy(fields = list)
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value
            val template = Template(
                id = state.templateId ?: 0L,
                name = state.name,
                vault = state.vault,
                folder = state.folder,
                isDefault = state.isDefault,
                fields = state.fields.mapIndexed { index, field ->
                    TemplateField(
                        id = field.id,
                        templateId = state.templateId ?: 0L,
                        key = field.key,
                        valueSource = field.valueSource,
                        valueType = field.valueType,
                        defaultValue = field.defaultValue,
                        metaKey = field.metaKey,
                        sortOrder = index,
                    )
                },
            )
            templateRepository.saveTemplate(template)
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}
