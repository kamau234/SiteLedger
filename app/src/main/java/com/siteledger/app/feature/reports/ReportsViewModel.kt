package com.siteledger.app.feature.reports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.domain.model.ReportPreset
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.reports.ReportComposer
import com.siteledger.app.reports.ReportDocument
import com.siteledger.app.reports.ReportExporter
import com.siteledger.app.reports.ReportRequest
import com.siteledger.app.reports.ReportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ExportKind { PDF, XLSX }

data class ReportsUiState(
    val type: ReportType = ReportType.PROJECT_SUMMARY,
    val projectId: String? = null,
    val preset: ReportPreset = ReportPreset.ALL_TIME,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val projects: List<Pair<String, String>> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val pendingExport: ExportKind? = null,
    val suggestedFileName: String? = null,
)

class ReportsViewModel(
    projectRepository: ProjectRepository,
    private val reportComposer: ReportComposer,
    private val reportExporter: ReportExporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState

    private var preparedDocument: ReportDocument? = null

    init {
        viewModelScope.launch {
            projectRepository.observeProjects().collect { projects ->
                _uiState.update { state ->
                    val stillValid = state.projectId == null || projects.any { it.id == state.projectId }
                    state.copy(
                        projects = projects.map { it.id to it.name },
                        projectId = if (stillValid) state.projectId else null,
                    )
                }
            }
        }
    }

    fun onTypeSelected(type: ReportType) = _uiState.update { it.copy(type = type, message = null) }

    fun onProjectSelected(projectId: String?) = _uiState.update { it.copy(projectId = projectId) }

    fun onPresetSelected(preset: ReportPreset) = _uiState.update { it.copy(preset = preset) }

    fun onCustomFromChange(date: LocalDate?) = _uiState.update { it.copy(customFrom = date) }

    fun onCustomToChange(date: LocalDate?) = _uiState.update { it.copy(customTo = date) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun requestExport(kind: ExportKind) {
        val state = _uiState.value
        if (state.busy) return
        if (state.type == ReportType.PROJECT_SUMMARY && state.projectId == null) {
            _uiState.update { it.copy(message = "Select a project for this report") }
            return
        }
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val document = reportComposer.build(
                    ReportRequest(
                        type = state.type,
                        projectId = state.projectId,
                        preset = state.preset,
                        customFrom = state.customFrom,
                        customTo = state.customTo,
                    ),
                )
                preparedDocument = document
                _uiState.update {
                    it.copy(
                        busy = false,
                        pendingExport = kind,
                        suggestedFileName = document.suggestedFileName(
                            if (kind == ExportKind.PDF) "pdf" else "xlsx",
                        ),
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(busy = false, message = t.message ?: "Could not build the report")
                }
            }
        }
    }

    /** Called with the SAF result; a null uri means the user cancelled the save dialog. */
    fun completeExport(uri: Uri?) {
        val kind = _uiState.value.pendingExport ?: return
        val document = preparedDocument
        _uiState.update { it.copy(pendingExport = null, suggestedFileName = null) }
        if (uri == null || document == null) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching {
                when (kind) {
                    ExportKind.PDF -> reportExporter.exportPdf(document, uri)
                    ExportKind.XLSX -> reportExporter.exportXlsx(document, uri)
                }
            }.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(busy = false, message = "Report saved to the chosen location")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(busy = false, message = error.message ?: "Could not save the report")
                    }
                },
            )
        }
    }
}
