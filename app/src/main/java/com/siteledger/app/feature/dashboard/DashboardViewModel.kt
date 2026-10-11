package com.siteledger.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.domain.model.BudgetMath
import com.siteledger.app.domain.model.ExpenseWithContext
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.model.ProjectSummary
import com.siteledger.app.domain.repository.ExpenseRepository
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val companyName: String = "",
    val totalBudgetKes: Long = 0,
    val totalSpentKes: Long = 0,
    val totalRemainingKes: Long = 0,
    val projectCount: Int = 0,
    val activeProjectCount: Int = 0,
    val pendingSyncCount: Int = 0,
    val warningThresholdPercent: Int = BudgetMath.DEFAULT_WARNING_THRESHOLD_PERCENT,
    val recentExpenses: List<ExpenseWithContext> = emptyList(),
    val watchList: List<ProjectSummary> = emptyList(),
    val overBudgetCount: Int = 0,
)

class DashboardViewModel(
    projectRepository: ProjectRepository,
    expenseRepository: ExpenseRepository,
    outboxDao: OutboxDao,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val baseState = combine(
        projectRepository.observeProjects(),
        projectRepository.observeProjectTotals(),
        expenseRepository.observeRecentExpenses(5),
        outboxDao.observePendingCount(),
        settingsRepository.warningThresholdPercent,
    ) { projects, totals, recent, pending, threshold ->
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
        DashboardUiState(
            totalBudgetKes = summaries.sumOf { it.project.budgetAmount },
            totalSpentKes = summaries.sumOf { it.spentKes },
            totalRemainingKes = summaries.sumOf { it.remainingKes },
            projectCount = summaries.size,
            activeProjectCount = summaries.count { it.project.status == ProjectStatus.ACTIVE },
            pendingSyncCount = pending,
            warningThresholdPercent = threshold,
            recentExpenses = recent,
            watchList = summaries
                .filter { it.isAtThreshold }
                .sortedByDescending { it.percentUsed ?: 0.0 },
            overBudgetCount = summaries.count { it.isOverBudget },
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        baseState,
        settingsRepository.companyName,
    ) { state, company ->
        state.copy(companyName = company)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
