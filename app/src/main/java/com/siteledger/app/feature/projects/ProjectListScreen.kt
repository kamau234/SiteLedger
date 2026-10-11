package com.siteledger.app.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.siteledger.app.core.designsystem.BudgetProgress
import com.siteledger.app.core.designsystem.EmptyState
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor

@Composable
fun ProjectListScreen(
    container: AppContainer,
    onAddProject: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val viewModel: ProjectListViewModel = viewModel(
        factory = viewModelFactoryFor {
            ProjectListViewModel(container.projectRepository, container.settingsRepository)
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Projects",
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProject) {
                Icon(Icons.Filled.Add, contentDescription = "New project")
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
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = "Search projects",
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Search,
                )
            }
            if (state.items.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.totalCount == 0) "No projects yet" else "No matches",
                        message = if (state.totalCount == 0) {
                            "Create a project to hold an approved budget; expenses are recorded against it."
                        } else {
                            "Try a different search term."
                        },
                        actionLabel = if (state.totalCount == 0) "New project" else null,
                        onAction = if (state.totalCount == 0) onAddProject else null,
                    )
                }
            } else {
                items(state.items, key = { it.project.id }) { summary ->
                    Card(
                        onClick = { onOpenProject(summary.project.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        summary.project.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    val subtitle = listOfNotNull(
                                        summary.project.clientName,
                                        summary.project.location,
                                    ).joinToString(" · ")
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                StatusChip(summary.project.status.label())
                            }
                            BudgetProgress(
                                spentKes = summary.spentKes,
                                budgetKes = summary.project.budgetAmount,
                                thresholdPercent = summary.warningThresholdPercent,
                            )
                            Text(
                                "${summary.expenseCount} expense${if (summary.expenseCount == 1) "" else "s"} · " +
                                    "Remaining ${Money.formatKes(summary.remainingKes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
