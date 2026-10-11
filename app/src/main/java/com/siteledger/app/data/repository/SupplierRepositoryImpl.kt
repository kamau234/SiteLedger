package com.siteledger.app.data.repository

import androidx.room.withTransaction
import com.siteledger.app.core.common.AppJson
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.IdGenerator
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.dao.SupplierDao
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.local.entity.SupplierEntity
import com.siteledger.app.data.local.toDomain
import com.siteledger.app.data.remote.dto.toDto
import com.siteledger.app.data.session.ActiveUserStore
import com.siteledger.app.domain.Validation
import com.siteledger.app.domain.model.EntitySyncState
import com.siteledger.app.domain.model.Supplier
import com.siteledger.app.domain.model.SupplierDraft
import com.siteledger.app.domain.repository.SaveResult
import com.siteledger.app.domain.repository.SupplierRepository
import com.siteledger.app.sync.OutboxWriter
import com.siteledger.app.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

class SupplierRepositoryImpl(
    private val db: AppDatabase,
    private val supplierDao: SupplierDao,
    private val outboxDao: OutboxDao,
    private val outboxWriter: OutboxWriter,
    private val activeUser: ActiveUserStore,
    private val syncTrigger: SyncTrigger,
    private val clock: AppClock,
    private val ids: IdGenerator,
) : SupplierRepository {

    override fun observeSuppliers(): Flow<List<Supplier>> =
        supplierDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getSupplier(id: String): Supplier? = supplierDao.getById(id)?.toDomain()

    override suspend fun saveSupplier(draft: SupplierDraft): SaveResult {
        val errors = Validation.validateSupplier(draft)
        if (errors.isNotEmpty()) return SaveResult.ValidationFailed(errors)

        val now = clock.nowMillis()
        val existing = draft.id?.let { supplierDao.getById(it) }
        val entity = SupplierEntity(
            id = draft.id ?: ids.newId(),
            name = draft.name.trim(),
            phone = draft.phone?.trim()?.takeIf { it.isNotEmpty() },
            location = draft.location?.trim()?.takeIf { it.isNotEmpty() },
            notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            syncState = EntitySyncState.PENDING.dbValue,
            remoteUpdatedAt = existing?.remoteUpdatedAt,
        )
        db.withTransaction {
            supplierDao.upsert(entity)
            enqueue(entity, OutboxOperation.UPSERT)
        }
        syncTrigger.requestSync()
        return SaveResult.Success(entity.id)
    }

    override suspend fun deleteSupplier(id: String): SaveResult {
        if (supplierDao.getById(id) == null) return SaveResult.Failure("Supplier not found")
        val now = clock.nowMillis()
        db.withTransaction {
            supplierDao.applyTombstone(id, deletedAt = now, updatedAt = now, syncState = EntitySyncState.PENDING.dbValue)
            supplierDao.getById(id)?.let { enqueue(it, OutboxOperation.DELETE) }
        }
        syncTrigger.requestSync()
        return SaveResult.Success(id)
    }

    private suspend fun enqueue(entity: SupplierEntity, operation: String) {
        val owner = activeUser.current ?: ActiveUserStore.PENDING_OWNER
        outboxWriter.enqueueRowChange(
            outboxDao,
            OutboxEntityType.SUPPLIER,
            entity.id,
            operation,
            AppJson.encodeToString(entity.toDto(owner)),
        )
    }
}
