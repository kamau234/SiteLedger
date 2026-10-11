package com.siteledger.app.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.domain.model.BudgetMath
import com.siteledger.app.domain.model.ExpenseFilter
import com.siteledger.app.domain.model.ExpenseWithContext
import com.siteledger.app.domain.model.Project
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.repository.ExpenseRepository
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SaveResult
import com.siteledger.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val project: Project? = null,
    val spentKes: Long = 0,
    val expenseCount: Int = 0,
    val thresholdPercent: Int = BudgetMath.DEFAULT_WARNING_THRESHOLD_PERCENT,
    val expenses: List<ExpenseWithContext> = emptyList(),
    val notFound: Boolean = false,
)

class ProjectDetailViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    expenseRepository: ExpenseRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        projectRepository.observeProject(projectId),
        projectRepository.observeProjectTotals(),
        expenseRepository.observeExpenses(ExpenseFilter(projectId = projectId)),
        settingsRepository.warningThresholdPercent,
    ) { project, totals, expenses, threshold ->
        val projectTotals = totals.firstOrNull { it.projectId == projectId }
        ProjectDetailUiState(
            project = project,
            spentKes = projectTotals?.totalKes ?: 0L,
            expenseCount = projectTotals?.expenseCount ?: 0,
            thresholdPercent = threshold,
            expenses = expenses,
            notFound = project == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun archive() {
        viewModelScope.launch {
            when (val result = projectRepository.archiveProject(projectId)) {
                is SaveResult.Success -> _message.value = "Project archived. You can still view its expenses."
                is SaveResult.ValidationFailed -> _message.value = result.errors.joinToString("; ") { it.message }
                is SaveResult.Failure -> _message.value = result.message
            }
        }
    }

    fun reactivate() {
        viewModelScope.launch {
            val project = projectRepository.getProject(projectId) ?: return@launch
            val result = projectRepository.saveProject(
                com.siteledger.app.domain.model.ProjectDraft(
                    id = project.id,
                    name = project.name,
                    clientName = project.clientName,
                    location = project.location,
                    budgetAmount = project.budgetAmount,
                    startDate = project.startDate,
                    expectedEndDate = project.expectedEndDate,
                    status = ProjectStatus.ACTIVE,
                ),
            )
            _message.value = when (result) {
                is SaveResult.Success -> "Project restored to active."
                is SaveResult.ValidationFailed -> result.errors.joinToString("; ") { it.message }
                is SaveResult.Failure -> result.message
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (val result = projectRepository.deleteProject(projectId)) {
                is SaveResult.Success -> onDeleted()
                is SaveResult.ValidationFailed -> _message.value = result.errors.joinToString("; ") { it.message }
                is SaveResult.Failure -> _message.value = result.message
            }
        }
    }
}
