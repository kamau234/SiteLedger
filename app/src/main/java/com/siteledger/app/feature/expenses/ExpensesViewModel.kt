package com.siteledger.app.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.domain.model.ExpenseFilter
import com.siteledger.app.domain.model.ExpenseWithContext
import com.siteledger.app.domain.model.ReportPreset
import com.siteledger.app.domain.repository.CategoryRepository
import com.siteledger.app.domain.repository.ExpenseRepository
import com.siteledger.app.domain.repository.ProjectRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ExpensesFilter(
    val query: String = "",
    val projectId: String? = null,
    val categoryId: String? = null,
    val preset: ReportPreset = ReportPreset.ALL_TIME,
)

data class ExpensesUiState(
    val items: List<ExpenseWithContext> = emptyList(),
    val totalKes: Long = 0,
    val projects: List<Pair<String, String>> = emptyList(),
    val categories: List<Pair<String, String>> = emptyList(),
    val filter: ExpensesFilter = ExpensesFilter(),
    val hasAnyData: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val expenseRepository: ExpenseRepository,
    projectRepository: ProjectRepository,
    categoryRepository: CategoryRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val filter = MutableStateFlow(ExpensesFilter())

    private val rowsFlow = filter.flatMapLatest { current ->
        val period = current.preset.let { preset ->
            com.siteledger.app.domain.model.ReportPeriod.resolve(preset, clock.today())
        }
        expenseRepository.observeExpenses(
            ExpenseFilter(
                projectId = current.projectId,
                categoryId = current.categoryId,
                from = period.from,
                to = period.to,
            ),
        )
    }

    val uiState: StateFlow<ExpensesUiState> = combine(
        rowsFlow,
        projectRepository.observeProjects(),
        categoryRepository.observeCategories(),
        filter,
    ) { rows, projects, categories, current ->
        val searched = if (current.query.isBlank()) {
            rows
        } else {
            rows.filter { item ->
                item.expense.description.contains(current.query, ignoreCase = true) ||
                    item.categoryName.contains(current.query, ignoreCase = true) ||
                    item.supplierName?.contains(current.query, ignoreCase = true) == true
            }
        }
        ExpensesUiState(
            items = searched,
            totalKes = searched.sumOf { it.expense.amountKes },
            projects = projects.map { it.id to it.name },
            categories = categories.map { it.id to it.name },
            filter = current,
            hasAnyData = rows.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    fun onQueryChange(value: String) = filter.update { it.copy(query = value) }

    fun onProjectSelected(projectId: String?) = filter.update { it.copy(projectId = projectId) }

    fun onCategorySelected(categoryId: String?) = filter.update { it.copy(categoryId = categoryId) }

    fun onPresetSelected(preset: ReportPreset) = filter.update { it.copy(preset = preset) }
}
