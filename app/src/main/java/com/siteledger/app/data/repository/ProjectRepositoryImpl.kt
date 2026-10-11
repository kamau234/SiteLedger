package com.siteledger.app.data.repository

import androidx.room.withTransaction
import com.siteledger.app.core.common.AppJson
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.IdGenerator
import com.siteledger.app.data.local.dao.ExpenseDao
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.dao.ProjectDao
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.local.entity.ProjectEntity
import com.siteledger.app.data.local.toDomain
import com.siteledger.app.data.remote.dto.toDto
import com.siteledger.app.data.session.ActiveUserStore
import com.siteledger.app.domain.Validation
import com.siteledger.app.domain.model.EntitySyncState
import com.siteledger.app.domain.model.Project
import com.siteledger.app.domain.model.ProjectDraft
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.model.ProjectTotals
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SaveResult
import com.siteledger.app.sync.OutboxWriter
import com.siteledger.app.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

/**
 * Local-first project repository. Every write persists to Room and enqueues the matching
 * outbox entry in one transaction (blueprint section 04, local save protocol).
 */
class ProjectRepositoryImpl(
    private val db: AppDatabase,
    private val projectDao: ProjectDao,
    private val expenseDao: ExpenseDao,
    private val outboxDao: OutboxDao,
    private val outboxWriter: OutboxWriter,
    private val activeUser: ActiveUserStore,
    private val syncTrigger: SyncTrigger,
    private val clock: AppClock,
    private val ids: IdGenerator,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> =
        projectDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeProject(id: String): Flow<Project?> =
        projectDao.observeById(id).map { it?.toDomain() }

    override fun observeProjectTotals(): Flow<List<ProjectTotals>> =
        expenseDao.observeTotalsByProject().map { rows ->
            rows.map { ProjectTotals(projectId = it.projectId, totalKes = it.totalKes, expenseCount = it.expenseCount) }
        }

    override suspend fun getProject(id: String): Project? = projectDao.getById(id)?.toDomain()

    override suspend fun saveProject(draft: ProjectDraft): SaveResult {
        val errors = Validation.validateProject(draft)
        if (errors.isNotEmpty()) return SaveResult.ValidationFailed(errors)

        val now = clock.nowMillis()
        val existing = draft.id?.let { projectDao.getById(it) }
        val entity = ProjectEntity(
            id = draft.id ?: ids.newId(),
            name = draft.name.trim(),
            clientName = draft.clientName.orNullIfBlank(),
            location = draft.location.orNullIfBlank(),
            budgetAmount = draft.budgetAmount,
            startDate = draft.startDate?.toString(),
            expectedEndDate = draft.expectedEndDate?.toString(),
            status = draft.status.dbValue,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            syncState = EntitySyncState.PENDING.dbValue,
            remoteUpdatedAt = existing?.remoteUpdatedAt,
        )
        db.withTransaction {
            projectDao.upsert(entity)
            enqueue(entity, OutboxOperation.UPSERT)
        }
        syncTrigger.requestSync()
        return SaveResult.Success(entity.id)
    }

    override suspend fun archiveProject(id: String): SaveResult {
        val existing = projectDao.getById(id) ?: return SaveResult.Failure("Project not found")
        if (existing.status == ProjectStatus.ARCHIVED.dbValue) return SaveResult.Success(id)
        val entity = existing.copy(
            status = ProjectStatus.ARCHIVED.dbValue,
            updatedAt = clock.nowMillis(),
            syncState = EntitySyncState.PENDING.dbValue,
        )
        db.withTransaction {
            projectDao.upsert(entity)
            enqueue(entity, OutboxOperation.UPSERT)
        }
        syncTrigger.requestSync()
        return SaveResult.Success(id)
    }

    override suspend fun deleteProject(id: String): SaveResult {
        if (projectDao.getById(id) == null) return SaveResult.Failure("Project not found")
        val expenseCount = expenseDao.countForProject(id)
        if (expenseCount > 0) {
            return SaveResult.Failure("This project has $expenseCount recorded expense(s). Archive it instead of deleting.")
        }
        val now = clock.nowMillis()
        db.withTransaction {
            projectDao.applyTombstone(id, deletedAt = now, updatedAt = now, syncState = EntitySyncState.PENDING.dbValue)
            projectDao.getById(id)?.let { enqueue(it, OutboxOperation.DELETE) }
        }
        syncTrigger.requestSync()
        return SaveResult.Success(id)
    }

    private suspend fun enqueue(entity: ProjectEntity, operation: String) {
        val owner = activeUser.current ?: ActiveUserStore.PENDING_OWNER
        outboxWriter.enqueueRowChange(
            outboxDao,
            OutboxEntityType.PROJECT,
            entity.id,
            operation,
            AppJson.encodeToString(entity.toDto(owner)),
        )
    }
}

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
