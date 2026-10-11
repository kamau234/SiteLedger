package com.siteledger.app.sync

import androidx.room.withTransaction
import com.siteledger.app.core.common.AppJson
import com.siteledger.app.core.common.ConnectivityObserver
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.Dates
import com.siteledger.app.data.local.dao.AppMetadataDao
import com.siteledger.app.data.local.dao.CategoryDao
import com.siteledger.app.data.local.dao.ExpenseDao
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.dao.ProjectDao
import com.siteledger.app.data.local.dao.SupplierDao
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.local.entity.SyncOutboxEntity
import com.siteledger.app.data.receipt.ReceiptStore
import com.siteledger.app.data.remote.RemoteGateway
import com.siteledger.app.data.remote.dto.ExpenseCategoryDto
import com.siteledger.app.data.remote.dto.ExpenseDto
import com.siteledger.app.data.remote.dto.ProjectDto
import com.siteledger.app.data.remote.dto.SupplierDto
import com.siteledger.app.data.remote.dto.toDto
import com.siteledger.app.data.session.ActiveUserStore
import com.siteledger.app.domain.model.EntitySyncState
import com.siteledger.app.domain.model.ReceiptUploadState
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

enum class SyncPhase { IDLE, UPLOADING, DOWNLOADING }

sealed interface SyncOutcome {
    data class Success(val uploaded: Int, val applied: Int, val skippedLocalPending: Int) : SyncOutcome

    /** Transient problem (network/server); the queue stays intact for the next attempt. */
    data class RetryLater(val message: String) : SyncOutcome

    /** Nothing attempted: cloud not configured, signed out, offline or session expired. */
    data class Paused(val reason: String) : SyncOutcome
}

@Serializable
data class ReceiptUploadPayload(
    val expenseId: String,
    val localPath: String,
    val remotePath: String,
)

/**
 * Durable outbox processor. Uploads parent entities before children, retries transient
 * failures with backoff (WorkManager), marks permanent failures for user attention, then
 * pulls remote changes and reports sync status. See blueprint section 04.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val outboxDao: OutboxDao,
    private val projectDao: ProjectDao,
    private val categoryDao: CategoryDao,
    private val supplierDao: SupplierDao,
    private val expenseDao: ExpenseDao,
    private val metadataDao: AppMetadataDao,
    private val gateway: RemoteGateway,
    private val activeUser: ActiveUserStore,
    private val connectivity: ConnectivityObserver,
    private val receiptStore: ReceiptStore,
    private val pullMerger: PullMerger,
    private val outboxWriter: OutboxWriter,
    private val clock: AppClock,
) {

    private val _phase = MutableStateFlow(SyncPhase.IDLE)
    val phase: StateFlow<SyncPhase> = _phase

    private val mutex = Mutex()

    suspend fun sync(): SyncOutcome = mutex.withLock {
        if (!gateway.isEnabled()) {
            return@withLock SyncOutcome.Paused("Cloud is not configured — records stay on this phone")
        }
        val userId = activeUser.current
            ?: return@withLock SyncOutcome.Paused("Not signed in — cloud sync is paused")
        if (!connectivity.isOnline()) {
            return@withLock SyncOutcome.Paused("Offline — queued changes upload when connectivity returns")
        }

        // Recover entries left in-flight by process death before this run.
        outboxDao.resetInFlight()
        enqueueOrphanReceiptUploads(userId)

        _phase.value = SyncPhase.UPLOADING
        var uploaded = 0
        while (true) {
            val batch = outboxDao.nextPending(BATCH_SIZE)
            if (batch.isEmpty()) break
            var transientMessage: String? = null
            for (entry in batch) {
                when (val result = pushEntry(entry, userId)) {
                    is PushResult.Ack -> {
                        outboxDao.delete(entry.id)
                        uploaded++
                    }
                    is PushResult.Permanent -> {
                        outboxDao.markFailed(entry.id, result.message)
                        markEntityFailed(entry)
                    }
                    is PushResult.Transient -> {
                        outboxDao.recordAttempt(entry.id, result.message)
                        transientMessage = result.message
                        break
                    }
                    PushResult.SessionExpired -> {
                        _phase.value = SyncPhase.IDLE
                        return@withLock SyncOutcome.Paused("Session expired — sign in again to resume uploads")
                    }
                }
            }
            if (transientMessage != null) {
                _phase.value = SyncPhase.IDLE
                return@withLock SyncOutcome.RetryLater(transientMessage)
            }
        }

        _phase.value = SyncPhase.DOWNLOADING
        val watermark = metadataDao.get()?.lastSuccessfulSync?.let { it - PULL_SAFETY_MARGIN_MILLIS }
        val stats = try {
            pullMerger.mergeAll(watermark)
        } catch (t: Throwable) {
            _phase.value = SyncPhase.IDLE
            return@withLock classifyAsRetry(t)
        }

        metadataDao.setLastSuccessfulSync(clock.nowMillis())
        metadataDao.setInitialSyncComplete(true)
        _phase.value = SyncPhase.IDLE
        SyncOutcome.Success(uploaded = uploaded, applied = stats.applied, skippedLocalPending = stats.skippedLocalPending)
    }

    private sealed interface PushResult {
        data object Ack : PushResult
        data class Permanent(val message: String) : PushResult
        data class Transient(val message: String) : PushResult
        data object SessionExpired : PushResult
    }

    private suspend fun pushEntry(entry: SyncOutboxEntity, userId: String): PushResult =
        try {
            outboxDao.markInFlight(entry.id)
            when (entry.entityType) {
                OutboxEntityType.RECEIPT -> pushReceipt(entry)
                else -> pushRowChange(entry, userId)
            }
            PushResult.Ack
        } catch (t: Throwable) {
            classifyFailure(t)
        }

    private suspend fun pushRowChange(entry: SyncOutboxEntity, userId: String) {
        val table = tableFor(entry.entityType)

        // Server revision pre-flight: never silently overwrite a newer server row.
        if (!entry.forceOverwrite) {
            val lastKnownRevision = lastKnownServerRevision(entry)
            if (lastKnownRevision != null) {
                val remoteRevision = fetchServerRevision(table, entry.entityId)
                if (remoteRevision != null && remoteRevision > lastKnownRevision) {
                    throw ConflictException(
                        "This record changed on the server after your last sync. " +
                            "Use \"Keep local\" on the sync screen to overwrite it deliberately.",
                    )
                }
            }
        }

        // Stamp the authenticated user onto the payload; entries queued while signed out
        // carry a placeholder owner that must never reach the server.
        when (entry.entityType) {
            OutboxEntityType.PROJECT ->
                gateway.upsertProject(AppJson.decodeFromString<ProjectDto>(entry.payloadJson).copy(ownerId = userId))
            OutboxEntityType.EXPENSE_CATEGORY ->
                gateway.upsertCategory(AppJson.decodeFromString<ExpenseCategoryDto>(entry.payloadJson).copy(ownerId = userId))
            OutboxEntityType.SUPPLIER ->
                gateway.upsertSupplier(AppJson.decodeFromString<SupplierDto>(entry.payloadJson).copy(ownerId = userId))
            OutboxEntityType.EXPENSE ->
                gateway.upsertExpense(AppJson.decodeFromString<ExpenseDto>(entry.payloadJson).copy(ownerId = userId))
        }

        // Record the server's new revision (server clock only — never the device clock).
        val revision = fetchServerRevision(table, entry.entityId)
        markEntitySynced(entry, revision)

        if (entry.operation == OutboxOperation.DELETE && entry.entityType == OutboxEntityType.EXPENSE) {
            expenseDao.getById(entry.entityId)?.let { receiptStore.delete(it.receiptLocalPath) }
        }
    }

    private suspend fun fetchServerRevision(table: String, entityId: String): Long? =
        gateway.remoteUpdatedAt(table, entityId)?.let(Dates::parseIsoInstantToMillis)

    private suspend fun lastKnownServerRevision(entry: SyncOutboxEntity): Long? = when (entry.entityType) {
        OutboxEntityType.PROJECT -> projectDao.getById(entry.entityId)?.remoteUpdatedAt
        OutboxEntityType.EXPENSE_CATEGORY -> categoryDao.getById(entry.entityId)?.remoteUpdatedAt
        OutboxEntityType.SUPPLIER -> supplierDao.getById(entry.entityId)?.remoteUpdatedAt
        OutboxEntityType.EXPENSE -> expenseDao.getById(entry.entityId)?.remoteUpdatedAt
        else -> null
    }

    private suspend fun pushReceipt(entry: SyncOutboxEntity) {
        val payload = AppJson.decodeFromString<ReceiptUploadPayload>(entry.payloadJson)
        val bytes = receiptStore.readBytes(payload.localPath)
            ?: throw ReceiptFileMissingException("Local receipt file is no longer available")
        gateway.uploadReceipt(payload.remotePath, bytes)
        db.withTransaction {
            expenseDao.setReceiptUpload(payload.expenseId, payload.remotePath, ReceiptUploadState.UPLOADED.dbValue)
        }
    }

    /**
     * Receipts attached while no cloud user was active have no remote path yet. Once a user
     * signs in, derive the path from the blueprint convention and queue the file plus the
     * expense row (which then carries receipt_path) for upload. Idempotent: rows stop
     * matching the query once a remote path is set.
     */
    private suspend fun enqueueOrphanReceiptUploads(userId: String) {
        val orphans = expenseDao.receiptUploadsMissingRemotePath()
        if (orphans.isEmpty()) return
        db.withTransaction {
            for (expense in orphans) {
                val localPath = expense.receiptLocalPath ?: continue
                val remotePath = RemoteGateway.receiptPath(userId, expense.projectId, expense.id)
                val updated = expense.copy(
                    receiptRemotePath = remotePath,
                    syncState = EntitySyncState.PENDING.dbValue,
                )
                expenseDao.upsert(updated)
                outboxWriter.enqueueReceiptUpload(
                    outboxDao,
                    expense.id,
                    AppJson.encodeToString(ReceiptUploadPayload(expense.id, localPath, remotePath)),
                )
                outboxWriter.enqueueRowChange(
                    outboxDao,
                    OutboxEntityType.EXPENSE,
                    expense.id,
                    OutboxOperation.UPSERT,
                    AppJson.encodeToString(updated.toDto(userId)),
                )
            }
        }
    }

    private fun tableFor(entityType: String): String = when (entityType) {
        OutboxEntityType.PROJECT -> RemoteGateway.TABLE_PROJECTS
        OutboxEntityType.EXPENSE_CATEGORY -> RemoteGateway.TABLE_CATEGORIES
        OutboxEntityType.SUPPLIER -> RemoteGateway.TABLE_SUPPLIERS
        OutboxEntityType.EXPENSE -> RemoteGateway.TABLE_EXPENSES
        else -> throw IllegalArgumentException("Unsupported entity type $entityType")
    }

    private suspend fun markEntitySynced(entry: SyncOutboxEntity, serverRevision: Long?) {
        when (entry.entityType) {
            OutboxEntityType.PROJECT -> {
                projectDao.setSyncState(entry.entityId, EntitySyncState.SYNCED.dbValue)
                if (serverRevision != null) projectDao.setRemoteUpdatedAt(entry.entityId, serverRevision)
            }
            OutboxEntityType.EXPENSE_CATEGORY -> {
                categoryDao.setSyncState(entry.entityId, EntitySyncState.SYNCED.dbValue)
                if (serverRevision != null) categoryDao.setRemoteUpdatedAt(entry.entityId, serverRevision)
            }
            OutboxEntityType.SUPPLIER -> {
                supplierDao.setSyncState(entry.entityId, EntitySyncState.SYNCED.dbValue)
                if (serverRevision != null) supplierDao.setRemoteUpdatedAt(entry.entityId, serverRevision)
            }
            OutboxEntityType.EXPENSE -> {
                expenseDao.setSyncState(entry.entityId, EntitySyncState.SYNCED.dbValue)
                if (serverRevision != null) expenseDao.setRemoteUpdatedAt(entry.entityId, serverRevision)
            }
        }
    }

    private suspend fun markEntityFailed(entry: SyncOutboxEntity) {
        when (entry.entityType) {
            OutboxEntityType.PROJECT -> projectDao.markSyncFailed(entry.entityId)
            OutboxEntityType.EXPENSE_CATEGORY -> categoryDao.markSyncFailed(entry.entityId)
            OutboxEntityType.SUPPLIER -> supplierDao.markSyncFailed(entry.entityId)
            OutboxEntityType.EXPENSE -> expenseDao.markSyncFailed(entry.entityId)
            OutboxEntityType.RECEIPT -> expenseDao.getById(entry.entityId)?.let { expense ->
                expenseDao.setReceiptUpload(entry.entityId, expense.receiptRemotePath, ReceiptUploadState.FAILED.dbValue)
            }
            else -> Unit
        }
    }

    private fun classifyFailure(t: Throwable): PushResult = when (t) {
        is ConflictException, is ReceiptFileMissingException ->
            PushResult.Permanent(t.message ?: "Change cannot be uploaded")
        is RestException -> when {
            t.statusCode == 401 || t.statusCode == 403 -> PushResult.SessionExpired
            t.statusCode in 400..499 && t.statusCode != 408 && t.statusCode != 429 ->
                PushResult.Permanent("Rejected by server (${t.statusCode}): ${t.message}")
            else -> PushResult.Transient("Server unavailable (${t.statusCode}) — retrying")
        }
        is SerializationException ->
            PushResult.Permanent("Queued change payload is invalid: ${t.message}")
        else -> PushResult.Transient(t.message ?: "Network unavailable — retrying")
    }

    private fun classifyAsRetry(t: Throwable): SyncOutcome = when (t) {
        is RestException -> if (t.statusCode == 401 || t.statusCode == 403) {
            SyncOutcome.Paused("Session expired — sign in again to resume sync")
        } else {
            SyncOutcome.RetryLater("Download failed (${t.statusCode}) — retrying")
        }
        else -> SyncOutcome.RetryLater(t.message ?: "Download failed — retrying")
    }

    /**
     * Pulls the whole dataset for a fresh sign-in. Called by the auth repository before
     * marking initial sync as complete.
     */
    suspend fun runInitialBootstrap(): Result<Unit> = mutex.withLock {
        if (!gateway.isEnabled()) return@withLock Result.success(Unit)
        if (!connectivity.isOnline()) {
            return@withLock Result.failure(IllegalStateException("Initial download requires connectivity"))
        }
        return@withLock try {
            pullMerger.mergeAll(sinceMillis = null)
            metadataDao.setLastSuccessfulSync(clock.nowMillis())
            metadataDao.setInitialSyncComplete(true)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    companion object {
        const val BATCH_SIZE = 25
        const val PULL_SAFETY_MARGIN_MILLIS = 5 * 60 * 1000L
    }
}

class ConflictException(message: String) : Exception(message)

class ReceiptFileMissingException(message: String) : Exception(message)
