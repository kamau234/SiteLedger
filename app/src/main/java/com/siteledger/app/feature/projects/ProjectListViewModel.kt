package com.siteledger.app.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.domain.model.ProjectSummary
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProjectsUiState(
    val items: List<ProjectSummary> = emptyList(),
    val query: String = "",
    val totalCount: Int = 0,
)

class ProjectListViewModel(
    projectRepository: ProjectRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<ProjectsUiState> = combine(
        projectRepository.observeProjects(),
        projectRepository.observeProjectTotals(),
        query,
        settingsRepository.warningThresholdPercent,
    ) { projects, totals, searchQuery, threshold ->
        val totalsById = totals.associateBy { it.projectId }
        val summaries = projects.map { project ->
            val projectTotals = totalsById[project.id]
            ProjectSummary(
                project = project,
                spentKes = projectTotals?.totalKes ?: 0L,
                expenseCount = projectTotals?.expenseCount ?: 0,
                warningThresholdPercent = threshold,
            )
        }
        val filtered = if (searchQuery.isBlank()) {
            summaries
        } else {
            summaries.filter { summary ->
                summary.project.name.contains(searchQuery, ignoreCase = true) ||
                    summary.project.clientName?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        ProjectsUiState(items = filtered, query = searchQuery, totalCount = summaries.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }
}
