package com.siteledger.app.data.repository

import androidx.room.withTransaction
import com.siteledger.app.core.common.AppJson
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.IdGenerator
import com.siteledger.app.data.local.dao.CategoryDao
import com.siteledger.app.data.local.dao.ExpenseDao
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.entity.ExpenseCategoryEntity
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.local.toDomain
import com.siteledger.app.data.remote.dto.toDto
import com.siteledger.app.data.session.ActiveUserStore
import com.siteledger.app.domain.FieldError
import com.siteledger.app.domain.model.EntitySyncState
import com.siteledger.app.domain.model.ExpenseCategory
import com.siteledger.app.domain.repository.CategoryRepository
import com.siteledger.app.domain.repository.SaveResult
import com.siteledger.app.sync.OutboxWriter
import com.siteledger.app.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

class CategoryRepositoryImpl(
    private val db: AppDatabase,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val outboxDao: OutboxDao,
    private val outboxWriter: OutboxWriter,
    private val activeUser: ActiveUserStore,
    private val syncTrigger: SyncTrigger,
    private val clock: AppClock,
    private val ids: IdGenerator,
) : CategoryRepository {

    override fun observeCategories(): Flow<List<ExpenseCategory>> =
        categoryDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getCategory(id: String): ExpenseCategory? = categoryDao.getById(id)?.toDomain()

    override suspend fun ensureDefaults() {
        val now = clock.nowMillis()
        var changed = false
        db.withTransaction {
            changed = collapseDuplicateNames(now)
            // Names includes tombstoned rows: a deliberately deleted default is not recreated.
            val knownNames = categoryDao.getAll().map { it.name.lowercase() }.toSet()
            val missing = DEFAULT_CATEGORIES.filter { it.lowercase() !in knownNames }
            for (name in missing) {
                val entity = ExpenseCategoryEntity(
                    id = ids.newId(),
                    name = name,
                    isDefault = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    syncState = EntitySyncState.PENDING.dbValue,
                )
                categoryDao.upsert(entity)
                enqueue(entity, OutboxOperation.UPSERT)
                changed = true
            }
        }
        if (changed) syncTrigger.requestSync()
    }

    /**
     * Heals duplicates of the same logical category (offline seeds meeting their uploaded
     * counterparts under different ids). The server-accepted or oldest row is kept; the
     * rest are tombstoned, their expenses moved to the keeper, and a delete is queued so
     * the server copy disappears too.
     */
    private suspend fun collapseDuplicateNames(now: Long): Boolean {
        var changed = false
        val duplicateGroups = categoryDao.getActive()
            .groupBy { it.name.lowercase() }
            .filterValues { it.size > 1 }
        for ((_, rows) in duplicateGroups) {
            val keeper = rows.minWithOrNull(
                compareBy(
                    { it.syncState != EntitySyncState.SYNCED.dbValue },
                    { it.createdAt },
                    { it.id },
                ),
            ) ?: continue
            for (dup in rows) {
                if (dup.id == keeper.id) continue
                val affectedIds = expenseDao.getIdsForCategory(dup.id)
                expenseDao.reparentCategories(dup.id, keeper.id, now, EntitySyncState.PENDING.dbValue)
                categoryDao.applyTombstone(dup.id, deletedAt = now, updatedAt = now, syncState = EntitySyncState.PENDING.dbValue)
                categoryDao.getById(dup.id)?.let { enqueue(it, OutboxOperation.DELETE) }
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
                changed = true
            }
        }
        return changed
    }

    override suspend fun addCategory(name: String): SaveResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return SaveResult.ValidationFailed(listOf(FieldError("name", "Category name is required")))
        }
        val duplicate = categoryDao.getAll().any { it.deletedAt == null && it.name.equals(trimmed, ignoreCase = true) }
        if (duplicate) return SaveResult.Failure("A category named \"$trimmed\" already exists")

        val now = clock.nowMillis()
        val entity = ExpenseCategoryEntity(
            id = ids.newId(),
            name = trimmed,
            isDefault = false,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncState = EntitySyncState.PENDING.dbValue,
        )
        db.withTransaction {
            categoryDao.upsert(entity)
            enqueue(entity, OutboxOperation.UPSERT)
        }
        syncTrigger.requestSync()
        return SaveResult.Success(entity.id)
    }

    override suspend fun deleteCategory(id: String): SaveResult {
        val existing = categoryDao.getById(id) ?: return SaveResult.Failure("Category not found")
        val inUse = expenseDao.countForCategory(id)
        if (inUse > 0) {
            return SaveResult.Failure("\"${existing.name}\" is used by $inUse expense(s) and cannot be deleted")
        }
        val remaining = categoryDao.getActive().count { it.id != id }
        if (remaining == 0) return SaveResult.Failure("At least one expense category is required")

        val now = clock.nowMillis()
        db.withTransaction {
            categoryDao.applyTombstone(id, deletedAt = now, updatedAt = now, syncState = EntitySyncState.PENDING.dbValue)
            categoryDao.getById(id)?.let { enqueue(it, OutboxOperation.DELETE) }
        }
        syncTrigger.requestSync()
        return SaveResult.Success(id)
    }

    private suspend fun enqueue(entity: ExpenseCategoryEntity, operation: String) {
        val owner = activeUser.current ?: ActiveUserStore.PENDING_OWNER
        outboxWriter.enqueueRowChange(
            outboxDao,
            OutboxEntityType.EXPENSE_CATEGORY,
            entity.id,
            operation,
            AppJson.encodeToString(entity.toDto(owner)),
        )
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf("Materials", "Labour", "Transport", "Equipment", "Permits", "Other")
    }
}
