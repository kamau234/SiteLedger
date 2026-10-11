package com.siteledger.app.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.core.common.Money
import com.siteledger.app.domain.Validation
import com.siteledger.app.domain.model.ProjectDraft
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProjectFormUiState(
    val isEdit: Boolean = false,
    val name: String = "",
    val clientName: String = "",
    val location: String = "",
    val budgetText: String = "",
    val startDate: LocalDate? = null,
    val expectedEndDate: LocalDate? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val loading: Boolean = false,
    val done: Boolean = false,
    val message: String? = null,
)

class ProjectFormViewModel(
    private val projectId: String?,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProjectFormUiState(isEdit = projectId != null, loading = projectId != null),
    )
    val uiState: StateFlow<ProjectFormUiState> = _uiState

    init {
        if (projectId != null) {
            viewModelScope.launch {
                val project = projectRepository.getProject(projectId)
                if (project == null) {
                    _uiState.update { it.copy(loading = false, message = "That project no longer exists") }
                } else {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            name = project.name,
                            clientName = project.clientName.orEmpty(),
                            location = project.location.orEmpty(),
                            budgetText = if (project.budgetAmount == 0L) "" else project.budgetAmount.toString(),
                            startDate = project.startDate,
                            expectedEndDate = project.expectedEndDate,
                            status = project.status,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errors = it.errors - Validation.ProjectField.NAME) }

    fun onClientChange(value: String) = _uiState.update { it.copy(clientName = value) }

    fun onLocationChange(value: String) = _uiState.update { it.copy(location = value) }

    fun onBudgetChange(value: String) = _uiState.update { it.copy(budgetText = value, errors = it.errors - Validation.ProjectField.BUDGET) }

    fun onStartDateChange(value: LocalDate?) = _uiState.update { it.copy(startDate = value, errors = it.errors - Validation.ProjectField.DATES) }

    fun onEndDateChange(value: LocalDate?) = _uiState.update { it.copy(expectedEndDate = value, errors = it.errors - Validation.ProjectField.DATES) }

    fun onStatusChange(value: ProjectStatus) = _uiState.update { it.copy(status = value) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun save() {
        val state = _uiState.value
        if (state.saving) return

        val budget = if (state.budgetText.isBlank()) 0L else Money.parseKes(state.budgetText)
        if (budget == null) {
            _uiState.update {
                it.copy(errors = it.errors + (Validation.ProjectField.BUDGET to "Enter a valid budget in whole shillings"))
            }
            return
        }

        val draft = ProjectDraft(
            id = projectId,
            name = state.name.trim(),
            clientName = state.clientName.trim().ifBlank { null },
            location = state.location.trim().ifBlank { null },
            budgetAmount = budget,
            startDate = state.startDate,
            expectedEndDate = state.expectedEndDate,
            status = state.status,
        )

        val validationErrors = Validation.validateProject(draft)
        if (validationErrors.isNotEmpty()) {
            _uiState.update { it.copy(errors = validationErrors.associate { error -> error.field to error.message }) }
            return
        }

        _uiState.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            when (val result = projectRepository.saveProject(draft)) {
                is SaveResult.Success -> _uiState.update { it.copy(saving = false, done = true) }
                is SaveResult.ValidationFailed -> _uiState.update {
                    it.copy(saving = false, errors = result.errors.associate { error -> error.field to error.message })
                }
                is SaveResult.Failure -> _uiState.update { it.copy(saving = false, message = result.message) }
            }
        }
    }
}
