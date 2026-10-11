package com.siteledger.app.feature.expenses

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.common.Money
import com.siteledger.app.core.common.label
import com.siteledger.app.core.designsystem.EmptyState
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.core.designsystem.PickerField
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor
import com.siteledger.app.domain.model.ReportPreset

@Composable
fun ExpensesScreen(
    container: AppContainer,
    onAddExpense: () -> Unit,
    onOpenExpense: (String) -> Unit,
) {
    val viewModel: ExpensesViewModel = viewModel(
        factory = viewModelFactoryFor {
            ExpensesViewModel(
                expenseRepository = container.expenseRepository,
                projectRepository = container.projectRepository,
                categoryRepository = container.categoryRepository,
                clock = container.clock,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Expenses",
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Filled.Add, contentDescription = "Record expense")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FormTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::onQueryChange,
                    label = "Search expenses",
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Search,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField(
                        label = "Project",
                        selectedText = state.filter.projectId?.let { id ->
                            state.projects.firstOrNull { it.first == id }?.second
                        } ?: "All projects",
                        options = listOf("" to "All projects") + state.projects,
                        onSelect = { viewModel.onProjectSelected(it.ifBlank { null }) },
                        modifier = Modifier.weight(1f),
                    )
                    PickerField(
                        label = "Category",
                        selectedText = state.filter.categoryId?.let { id ->
                            state.categories.firstOrNull { it.first == id }?.second
                        } ?: "All categories",
                        options = listOf("" to "All categories") + state.categories,
                        onSelect = { viewModel.onCategorySelected(it.ifBlank { null }) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        ReportPreset.ALL_TIME,
                        ReportPreset.TODAY,
                        ReportPreset.THIS_WEEK,
                        ReportPreset.THIS_MONTH,
                        ReportPreset.LAST_MONTH,
                    ).forEach { preset ->
                        FilterChip(
                            selected = state.filter.preset == preset,
                            onClick = { viewModel.onPresetSelected(preset) },
                            label = { Text(preset.label()) },
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${state.items.size} transaction${if (state.items.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            Money.formatKes(state.totalKes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (state.items.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.hasAnyData) "No matching expenses" else "No expenses yet",
                        message = if (state.hasAnyData) {
                            "Adjust the filters or search term."
                        } else {
                            "Record an expense with the + button; it works fully offline."
                        },
                        actionLabel = if (state.hasAnyData) null else "Record expense",
                        onAction = if (state.hasAnyData) null else onAddExpense,
                    )
                }
            } else {
                items(state.items, key = { it.expense.id }) { item ->
                    ExpenseRowItem(
                        item = item,
                        onClick = { onOpenExpense(item.expense.id) },
                    )
                }
            }
        }
    }
}
