package com.siteledger.app.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.common.label
import com.siteledger.app.core.designsystem.DateField
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor
import com.siteledger.app.domain.model.ProjectStatus

@Composable
fun ProjectFormScreen(
    container: AppContainer,
    projectId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: ProjectFormViewModel = viewModel(
        factory = viewModelFactoryFor { ProjectFormViewModel(projectId, container.projectRepository) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.done) {
        if (state.done) onSaved()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    ScreenScaffold(
        title = if (state.isEdit) "Edit project" else "New project",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Project name *",
                modifier = Modifier.fillMaxWidth(),
                isError = state.errors.containsKey("name"),
                supportingText = state.errors["name"],
                enabled = !state.loading,
            )
            FormTextField(
                value = state.clientName,
                onValueChange = viewModel::onClientChange,
                label = "Client name",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            )
            FormTextField(
                value = state.location,
                onValueChange = viewModel::onLocationChange,
                label = "Location / site",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            )
            FormTextField(
                value = state.budgetText,
                onValueChange = viewModel::onBudgetChange,
                label = "Approved budget (KES)",
                modifier = Modifier.fillMaxWidth(),
                isError = state.errors.containsKey("budget"),
                supportingText = state.errors["budget"] ?: "Whole shillings. Leave blank for no budget yet.",
                keyboardType = KeyboardType.Number,
                enabled = !state.loading,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    label = "Start date",
                    value = state.startDate,
                    onChange = viewModel::onStartDateChange,
                    modifier = Modifier.weight(1f),
                    enabled = !state.loading,
                )
                DateField(
                    label = "Expected end",
                    value = state.expectedEndDate,
                    onChange = viewModel::onEndDateChange,
                    modifier = Modifier.weight(1f),
                    enabled = !state.loading,
                )
            }
            state.errors["dates"]?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProjectStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { viewModel.onStatusChange(status) },
                            label = { Text(status.label(), maxLines = 1) },
                            enabled = !state.loading,
                        )
                    }
                }
            }
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving && !state.loading,
            ) {
                Text(if (state.saving) "Saving…" else "Save project")
            }
        }
    }
}
