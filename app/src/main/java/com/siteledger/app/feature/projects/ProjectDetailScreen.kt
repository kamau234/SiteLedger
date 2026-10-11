package com.siteledger.app.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.common.Money
import com.siteledger.app.core.common.formatPercent
import com.siteledger.app.core.common.label
import com.siteledger.app.core.designsystem.BudgetProgress
import com.siteledger.app.core.designsystem.ConfirmDialog
import com.siteledger.app.core.designsystem.EmptyState
import com.siteledger.app.core.designsystem.LabeledValue
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.core.designsystem.SectionHeader
import com.siteledger.app.core.util.Dates
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor
import com.siteledger.app.domain.model.BudgetMath
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.feature.expenses.ExpenseRowItem

@Composable
fun ProjectDetailScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddExpense: (String) -> Unit,
    onOpenExpense: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: ProjectDetailViewModel = viewModel(
        factory = viewModelFactoryFor {
            ProjectDetailViewModel(
                projectId = projectId,
                projectRepository = container.projectRepository,
                expenseRepository = container.expenseRepository,
                settingsRepository = container.settingsRepository,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val project = state.project

    ScreenScaffold(
        title = project?.name ?: "Project",
        subtitle = project?.clientName,
        onBack = onBack,
        actions = {
            if (project != null) {
                TextButton(onClick = { onEdit(project.id) }) { Text("Edit") }
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        if (project == null) {
            Column(modifier = Modifier.padding(padding)) {
                if (state.notFound) {
                    EmptyState(
                        title = "Project not found",
                        message = "It may have been deleted on this phone.",
                        actionLabel = "Go back",
                        onAction = onBack,
                    )
                }
            }
            return@ScreenScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Budget health",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            StatusChip(project.status.label())
                        }
                        BudgetProgress(
                            spentKes = state.spentKes,
                            budgetKes = project.budgetAmount,
                            thresholdPercent = state.thresholdPercent,
                        )
                        HorizontalDivider()
                        LabeledValue("Approved budget", Money.formatKes(project.budgetAmount))
                        LabeledValue("Recorded spend", Money.formatKes(state.spentKes))
                        LabeledValue("Remaining budget", Money.formatKes(BudgetMath.remaining(project.budgetAmount, state.spentKes)))
                        val percent = BudgetMath.percentUsed(project.budgetAmount, state.spentKes)
                        LabeledValue("Budget used", percent?.let { formatPercent(it) } ?: "No budget set")
                        Text(
                            "Remaining budget is not profit — it is the approved budget less recorded expenses.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Project details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LabeledValue("Client", project.clientName ?: "—")
                        LabeledValue("Location", project.location ?: "—")
                        LabeledValue("Start date", Dates.format(project.startDate))
                        LabeledValue("Expected end", Dates.format(project.expectedEndDate))
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAddExpense(project.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Add expense")
                    }
                    if (project.status == ProjectStatus.ARCHIVED) {
                        TextButton(onClick = viewModel::reactivate) { Text("Restore") }
                    } else {
                        TextButton(onClick = viewModel::archive) { Text("Archive") }
                    }
                    TextButton(onClick = { showDeleteDialog = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                SectionHeader("Expenses (${state.expenseCount})")
            }

            if (state.expenses.isEmpty()) {
                item {
                    EmptyState(
                        title = "No expenses recorded",
                        message = "Expenses you record against this project will be listed here.",
                        actionLabel = "Add expense",
                        onAction = { onAddExpense(project.id) },
                    )
                }
            } else {
                items(state.expenses, key = { it.expense.id }) { expense ->
                    ExpenseRowItem(
                        item = expense,
                        showProject = false,
                        onClick = { onOpenExpense(expense.expense.id) },
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete this project?",
            message = "Projects with recorded expenses cannot be deleted — archive them instead so the financial history stays intact.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete(onDeleted)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
