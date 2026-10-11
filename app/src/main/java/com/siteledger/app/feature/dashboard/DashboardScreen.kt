package com.siteledger.app.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.common.Money
import com.siteledger.app.core.common.label
import com.siteledger.app.core.designsystem.BudgetProgress
import com.siteledger.app.core.designsystem.EmptyState
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.core.designsystem.SectionHeader
import com.siteledger.app.core.designsystem.StatCard
import com.siteledger.app.core.designsystem.SyncBadge
import com.siteledger.app.core.util.Dates
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor

@Composable
fun DashboardScreen(
    container: AppContainer,
    onOpenSync: () -> Unit,
    onAddExpense: () -> Unit,
    onAddProject: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = viewModelFactoryFor {
            DashboardViewModel(
                projectRepository = container.projectRepository,
                expenseRepository = container.expenseRepository,
                outboxDao = container.database.outboxDao(),
                settingsRepository = container.settingsRepository,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "SiteLedger",
        subtitle = state.companyName.ifBlank { null },
        actions = {
            IconButton(onClick = onOpenSync) {
                BadgedBox(
                    badge = {
                        if (state.pendingSyncCount > 0) {
                            Badge { Text(state.pendingSyncCount.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Sync status")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Filled.Add, contentDescription = "Record expense")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (state.projectCount == 0) "Karibu! Let's set up your first project." else "Your site money at a glance",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = "Total budget",
                            value = Money.formatKes(state.totalBudgetKes),
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "Total spent",
                            value = Money.formatKes(state.totalSpentKes),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = "Remaining",
                            value = Money.formatKes(state.totalRemainingKes),
                            modifier = Modifier.weight(1f),
                            emphasis = true,
                        )
                        StatCard(
                            label = "Projects",
                            value = if (state.projectCount == 0) "0" else "${state.activeProjectCount} active / ${state.projectCount}",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenProjects,
                        )
                    }
                }
            }

            item {
                Card(onClick = onOpenSync) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when {
                                    state.pendingSyncCount > 0 ->
                                        "${state.pendingSyncCount} change${if (state.pendingSyncCount == 1) "" else "s"} waiting to upload"
                                    else -> "All queued changes uploaded"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Tap to review sync status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Sync",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (state.watchList.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Budget watch",
                        trailing = {
                            Text(
                                "${state.overBudgetCount} over budget",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.overBudgetCount > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                    )
                }
                items(state.watchList, key = { it.project.id }) { summary ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProject(summary.project.id) },
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        summary.project.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        summary.project.status.label(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (summary.isOverBudget) {
                                    Text(
                                        "Over budget",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            BudgetProgress(
                                spentKes = summary.spentKes,
                                budgetKes = summary.project.budgetAmount,
                                thresholdPercent = state.warningThresholdPercent,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    "Recent expenses",
                    trailing = {
                        TextButton(onClick = onOpenProjects) {
                            Text("Projects")
                        }
                    },
                )
            }
            if (state.recentExpenses.isEmpty()) {
                item {
                    EmptyState(
                        title = "No expenses yet",
                        message = "Record your first site expense and it will show up here.",
                        actionLabel = "Record expense",
                        onAction = onAddExpense,
                    )
                }
            } else {
                items(state.recentExpenses, key = { it.expense.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenExpense(item.expense.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.expense.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${item.projectName} · ${Dates.format(item.expense.expenseDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Money.formatKes(item.expense.amountKes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                SyncBadge(item.expense.syncState)
                            }
                        }
                    }
                }
            }

            if (state.projectCount == 0) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Create your first project",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Projects hold the approved budget; expenses are recorded against them.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onAddProject) { Text("New project") }
                        }
                    }
                }
            }
        }
    }
}
