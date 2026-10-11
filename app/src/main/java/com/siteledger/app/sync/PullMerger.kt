package com.siteledger.app.sync

import androidx.room.withTransaction
import com.siteledger.app.core.common.AppJson
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.Dates
import com.siteledger.app.data.local.dao.CategoryDao
import com.siteledger.app.data.local.dao.ExpenseDao
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.dao.ProjectDao
import com.siteledger.app.data.local.dao.SupplierDao
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.remote.RemoteGateway
import com.siteledger.app.data.remote.dto.ExpenseCategoryDto
import com.siteledger.app.data.remote.dto.toDto
import com.siteledger.app.data.remote.dto.toEntity
import com.siteledger.app.data.session.ActiveUserStore
import com.siteledger.app.domain.model.EntitySyncState
import kotlinx.serialization.encodeToString

/**
 * Pulls remote rows and merges them into Room WITHOUT generating outgoing outbox entries
 * for the pulled rows themselves.
 *
 * Conflict rules (blueprint section 04):
 * - A record with pending local changes is never overwritten by a pull; local wins until
 *   the queued change is uploaded or resolved on the sync screen.
 * - A locally deleted record is never resurrected by a later download.
 * - Only rows whose server revision is newer than the local copy are applied.
 * - A pulled category whose name matches a never-uploaded local row (offline-seeded
 *   defaults) replaces it instead of duplicating it.
 */
class PullMerger(
    private val db: AppDatabase,
    private val gateway: RemoteGateway,
    private val projectDao: ProjectDao,
    private val categoryDao: CategoryDao,
    private val supplierDao: SupplierDao,
    private val expenseDao: ExpenseDao,
    private val outboxDao: OutboxDao,
    private val outboxWriter: OutboxWriter,
    private val activeUser: ActiveUserStore,
    private val clock: AppClock,
) {

    data class MergeStats(val applied: Int, val skippedLocalPending: Int)

    suspend fun mergeAll(sinceMillis: Long?): MergeStats {
        val sinceIso = sinceMillis?.let { Dates.millisToIsoUtc(it) }

        // Network reads first; the database transaction stays short and holds no I/O.
        val categories = gateway.fetchCategories(sinceIso)
        val suppliers = gateway.fetchSuppliers(sinceIso)
        val projects = gateway.fetchProjects(sinceIso)
        val expenses = gateway.fetchExpenses(sinceIso)

        var applied = 0
        var skipped = 0

        db.withTransaction {
            // Parents before children so foreign keys resolve.
            categories.forEach { dto ->
                val local = categoryDao.getById(dto.id)
                when {
                    local == null -> {
                        adoptPendingNamesake(dto)
                        categoryDao.upsert(dto.toEntity(EntitySyncState.SYNCED.dbValue)); applied++
                    }
                    local.deletedAt != null && dto.deletedAt == null -> skipped++
                    outboxDao.hasPendingFor(OutboxEntityType.EXPENSE_CATEGORY, dto.id) -> skipped++
                    remoteIsNewer(dto.updatedAt, local.remoteUpdatedAt) -> {
                        categoryDao.upsert(dto.toEntity(EntitySyncState.SYNCED.dbValue)); applied++
                    }
                }
            }

            suppliers.forEach { dto ->
                val local = supplierDao.getById(dto.id)
                when {
                    local == null -> {
                        supplierDao.upsert(dto.toEntity(EntitySyncState.SYNCED.dbValue)); applied++
                    }
                    local.deletedAt != null && dto.deletedAt == null -> skipped++
                    outboxDao.hasPendingFor(OutboxEntityType.SUPPLIER, dto.id) -> skipped++
                    remoteIsNewer(dto.updatedAt, local.remoteUpdatedAt) -> {
                        supplierDao.upsert(dto.toEntity(EntitySyncState.SYNCED.dbValue)); applied++
                    }
                }
            }

            projects.forEach { dto ->
                val local = projectDao.getById(dto.id)
                when {
                    local == null -> {
                        projectDao.upsert(dto.toEntity(EntitySyncState.SYNCED.dbValue)); applied++
                    }
                    local.deletedAt != null && dto.deletedAt == null -> skipped++
                    outboxDao.hasPendingFor(OutboxEntityType.PROJECT, dto.id) -> skipped++
                    remoteIsNewer(dto.updatedAt, local.remoteUpdatedAt) -> {
                        projectDao.upsert(dto.toEntity(EntitySyncState.SYNCED.dbValue)); applied++
                    }
                }
            }

            expenses.forEach { dto ->
                val local = expenseDao.getById(dto.id)
                when {
                    local == null -> {
                        expenseDao.upsert(dto.toEntity(current = null, syncState = EntitySyncState.SYNCED.dbValue)); applied++
                    }
                    local.deletedAt != null && dto.deletedAt == null -> skipped++
                    outboxDao.hasPendingFor(OutboxEntityType.EXPENSE, dto.id) -> skipped++
                    remoteIsNewer(dto.updatedAt, local.remoteUpdatedAt) -> {
                        expenseDao.upsert(dto.toEntity(current = local, syncState = EntitySyncState.SYNCED.dbValue)); applied++
                    }
                }
            }
        }
        return MergeStats(applied, skipped)
    }

    private fun remoteIsNewer(remoteIso: String, localRemoteUpdatedAt: Long?): Boolean {
        val remote = Dates.parseIsoInstantToMillis(remoteIso) ?: return false
        if (localRemoteUpdatedAt == null) return true
        return remote > localRemoteUpdatedAt
    }

    /**
     * Categories seeded offline carry a fresh local id while the server holds the same
     * logical row under its own id. Adopting the server row (deleting the pending namesake,
     * moving its expenses over, re-queueing them) stops the duplicate from ever uploading.
     */
    private suspend fun adoptPendingNamesake(dto: ExpenseCategoryDto) {
        val namesake = categoryDao.getActiveByName(dto.name).firstOrNull {
            it.id != dto.id && it.syncState == EntitySyncState.PENDING.dbValue
        } ?: return

        outboxDao.deletePendingRowOps(OutboxEntityType.EXPENSE_CATEGORY, namesake.id)
        val affectedIds = expenseDao.getIdsForCategory(namesake.id)
        expenseDao.reparentCategories(
            fromCategoryId = namesake.id,
            toCategoryId = dto.id,
            updatedAt = clock.nowMillis(),
            syncState = EntitySyncState.PENDING.dbValue,
        )
        categoryDao.deleteRow(namesake.id)

        val owner = activeUser.current ?: ActiveUserStore.PENDING_OWNER
        for (expenseId in affectedIds) {
            expenseDao.getById(expenseId)?.let { expense ->
                outboxWriter.enqueueRowChange(
                    outboxDao,
                    OutboxEntityType.EXPENSE,
                    expense.id,
                    OutboxOperation.UPSERT,
                    AppJson.encodeToString(expense.toDto(owner)),
                )
            }
        }
    }
}
