package com.siteledger.app.feature.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.common.label
import com.siteledger.app.core.designsystem.DateField
import com.siteledger.app.core.designsystem.PickerField
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.core.designsystem.SectionHeader
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor
import com.siteledger.app.domain.model.ReportPreset
import com.siteledger.app.reports.ReportType

private const val PDF_MIME = "application/pdf"
private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun ReportsScreen(container: AppContainer) {
    val viewModel: ReportsViewModel = viewModel(
        factory = viewModelFactoryFor {
            ReportsViewModel(
                projectRepository = container.projectRepository,
                reportComposer = container.reportComposer,
                reportExporter = container.reportExporter,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PDF_MIME),
    ) { uri -> viewModel.completeExport(uri) }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME),
    ) { uri -> viewModel.completeExport(uri) }

    LaunchedEffect(state.pendingExport, state.suggestedFileName) {
        val kind = state.pendingExport ?: return@LaunchedEffect
        val fileName = state.suggestedFileName ?: return@LaunchedEffect
        when (kind) {
            ExportKind.PDF -> pdfLauncher.launch(fileName)
            ExportKind.XLSX -> xlsxLauncher.launch(fileName)
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    ScreenScaffold(title = "Reports", snackbarHostState = snackbarHostState) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionHeader(title = "Report")
            ReportType.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.type == type,
                            onClick = { viewModel.onTypeSelected(type) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeSelected(type) },
                    )
                    Text(
                        text = type.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (state.type != ReportType.PROJECT_COMPARISON) {
                Spacer(Modifier.height(12.dp))
                val selectedProjectName = state.projects
                    .firstOrNull { it.first == state.projectId }?.second
                PickerField(
                    label = "Project",
                    selectedText = selectedProjectName,
                    options = state.projects,
                    onSelect = { viewModel.onProjectSelected(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (state.type == ReportType.PROJECT_SUMMARY) {
                        "Select a project"
                    } else {
                        "All projects"
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader(title = "Period")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { viewModel.onPresetSelected(preset) },
                        label = { Text(preset.label()) },
                    )
                }
            }
            if (state.preset == ReportPreset.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DateField(
                        label = "From",
                        value = state.customFrom,
                        onChange = { viewModel.onCustomFromChange(it) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    DateField(
                        label = "To",
                        value = state.customTo,
                        onChange = { viewModel.onCustomToChange(it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Export offline as a PDF document or an Excel spreadsheet. " +
                            "The file is written to the location you choose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (state.busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Preparing report…", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.requestExport(ExportKind.PDF) },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Export PDF")
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.requestExport(ExportKind.XLSX) },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Export Excel")
                        }
                    }
                }
            }
        }
    }
}
