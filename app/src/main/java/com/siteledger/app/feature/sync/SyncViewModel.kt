package com.siteledger.app.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.core.common.ConnectivityObserver
import com.siteledger.app.core.util.Dates
import com.siteledger.app.data.local.dao.AppMetadataDao
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.local.entity.SyncOutboxEntity
import com.siteledger.app.domain.repository.AuthRepository
import com.siteledger.app.domain.repository.SessionInfo
import com.siteledger.app.domain.repository.SessionPhase
import com.siteledger.app.sync.SyncEngine
import com.siteledger.app.sync.SyncOutcome
import com.siteledger.app.sync.SyncPhase
import com.siteledger.app.sync.SyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FailedEntryUi(
    val id: String,
    val label: String,
    val detail: String,
    val lastError: String?,
)

data class SyncUiState(
    val online: Boolean = true,
    val phase: SyncPhase = SyncPhase.IDLE,
    val pendingCount: Int = 0,
    val failed: List<FailedEntryUi> = emptyList(),
    val lastSuccessfulSyncLabel: String = "Never",
    val session: SessionInfo? = null,
    val running: Boolean = false,
    val message: String? = null,
)

class SyncViewModel(
    private val syncEngine: SyncEngine,
    private val outboxDao: OutboxDao,
    metadataDao: AppMetadataDao,
    connectivity: ConnectivityObserver,
    authRepository: AuthRepository,
    private val syncTrigger: SyncTrigger,
) : ViewModel() {

    private val local = MutableStateFlow(SyncUiState())

    private val observed: StateFlow<SyncUiState> = combine(
        connectivity.observe(),
        syncEngine.phase,
        outboxDao.observePendingCount(),
        outboxDao.observeFailed(),
    ) { online, phase, pending, failed ->
        SyncUiState(
            online = online,
            phase = phase,
            pendingCount = pending,
            failed = failed.map { it.toUi() },
        )
    }.combine(metadataDao.observe()) { state, metadata ->
        state.copy(
            lastSuccessfulSyncLabel = metadata?.lastSuccessfulSync
                ?.let { Dates.formatMillisUtc(it) } ?: "Never",
        )
    }.combine(authRepository.session) { state, session ->
        state.copy(session = session)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    val uiState: StateFlow<SyncUiState> = combine(observed, local) { seen, l ->
        seen.copy(running = l.running, message = l.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun syncNow() {
        if (local.value.running) return
        local.update { it.copy(running = true, message = null) }
        viewModelScope.launch {
            val outcome = runCatching { syncEngine.sync() }
            val message = outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is SyncOutcome.Success -> buildString {
                            append("Uploaded ${result.uploaded} · Applied ${result.applied}")
                            if (result.skippedLocalPending > 0) {
                                append(
                                    " · ${result.skippedLocalPending} kept local " +
                                        "(newer edits pending upload)",
                                )
                            }
                        }
                        is SyncOutcome.RetryLater -> result.message
                        is SyncOutcome.Paused -> result.reason
                    }
                },
                onFailure = { it.message ?: "Sync failed. Please try again." },
            )
            local.update { it.copy(running = false, message = message) }
        }
    }

    fun retry(id: String) {
        viewModelScope.launch {
            outboxDao.retry(id)
            syncTrigger.requestSync()
        }
    }

    fun retryKeepingLocal(id: String) {
        viewModelScope.launch {
            outboxDao.retryKeepingLocal(id)
            syncTrigger.requestSync()
        }
    }

    fun retryAll() {
        viewModelScope.launch {
            outboxDao.retryAllFailed()
            syncTrigger.requestSync()
            local.update { it.copy(message = "Retrying all failed changes…") }
        }
    }

    private fun SyncOutboxEntity.toUi(): FailedEntryUi = FailedEntryUi(
        id = id,
        label = entityType.toEntityLabel(),
        detail = operation.toOperationDetail(),
        lastError = lastError,
    )

    private fun String.toEntityLabel(): String = when (this) {
        OutboxEntityType.PROJECT -> "Project"
        OutboxEntityType.EXPENSE -> "Expense"
        OutboxEntityType.SUPPLIER -> "Supplier"
        OutboxEntityType.EXPENSE_CATEGORY -> "Category"
        OutboxEntityType.RECEIPT -> "Receipt photo"
        else -> "Change"
    }

    private fun String.toOperationDetail(): String = when (this) {
        OutboxOperation.UPSERT -> "Save"
        OutboxOperation.DELETE -> "Delete"
        OutboxOperation.UPLOAD_RECEIPT -> "Receipt upload"
        else -> this
    }
}

/** Simplified account line for the sync screen. */
fun SessionInfo.accountLabel(): String = when (phase) {
    SessionPhase.SIGNED_IN -> email ?: "Signed in"
    SessionPhase.LOCAL_ONLY -> "Local only device"
    SessionPhase.SESSION_EXPIRED -> "Session expired — sign in again"
    SessionPhase.SIGNED_OUT -> "Not signed in"
    SessionPhase.INITIALIZING -> "Restoring session…"
}
