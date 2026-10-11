package com.siteledger.app.data.repository

import androidx.room.withTransaction
import com.siteledger.app.core.common.AppJson
import com.siteledger.app.core.common.Money
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.Dates
import com.siteledger.app.core.util.IdGenerator
import com.siteledger.app.data.local.dao.ExpenseDao
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.entity.ExpenseEntity
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.local.toDomain
import com.siteledger.app.data.receipt.ReceiptStore
import com.siteledger.app.data.remote.RemoteGateway
import com.siteledger.app.data.remote.dto.toDto
import com.siteledger.app.data.session.ActiveUserStore
import com.siteledger.app.domain.Validation
import com.siteledger.app.domain.model.CategoryTotal
import com.siteledger.app.domain.model.EntitySyncState
import com.siteledger.app.domain.model.Expense
import com.siteledger.app.domain.model.ExpenseDraft
import com.siteledger.app.domain.model.ExpenseFilter
import com.siteledger.app.domain.model.ExpenseWithContext
import com.siteledger.app.domain.model.LedgerRow
import com.siteledger.app.domain.model.PaymentMethod
import com.siteledger.app.domain.model.ProjectComparisonRow
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.model.ReceiptUploadState
import com.siteledger.app.domain.model.ReportPeriod
import com.siteledger.app.domain.repository.ExpenseRepository
import com.siteledger.app.domain.repository.SaveResult
import com.siteledger.app.sync.OutboxWriter
import com.siteledger.app.sync.ReceiptUploadPayload
import com.siteledger.app.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.time.LocalDate

/**
 * Local-first expense repository. Receipt files are persisted to app-private storage at
 * save time; the upload itself travels as its own outbox operation so a failed file
 * upload never duplicates or blocks the expense row (blueprint section 03).
 */
class ExpenseRepositoryImpl(
    private val db: AppDatabase,
    private val expenseDao: ExpenseDao,
    private val outboxDao: OutboxDao,
    private val outboxWriter: OutboxWriter,
    private val receiptStore: ReceiptStore,
    private val activeUser: ActiveUserStore,
    private val syncTrigger: SyncTrigger,
    private val clock: AppClock,
    private val ids: IdGenerator,
) : ExpenseRepository {

    override fun observeExpenses(filter: ExpenseFilter): Flow<List<ExpenseWithContext>> =
        expenseDao.observeFiltered(
            projectId = filter.projectId,
            categoryId = filter.categoryId,
            paymentMethod = filter.paymentMethod?.dbValue,
            fromDate = filter.from?.let(Dates::toIso),
            toDate = filter.to?.let(Dates::toIso),
        ).map { list -> list.map { it.toDomain() } }

    override fun observeRecentExpenses(limit: Int): Flow<List<ExpenseWithContext>> =
        expenseDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeExpense(id: String): Flow<Expense?> =
        expenseDao.observeById(id).map { it?.toDomain() }

    override suspend fun getExpense(id: String): Expense? = expenseDao.getById(id)?.toDomain()

    override suspend fun countForProject(projectId: String): Int = expenseDao.countForProject(projectId)

    override suspend fun saveExpense(draft: ExpenseDraft): SaveResult {
        val errors = Validation.validateExpense(draft)
        if (errors.isNotEmpty()) return SaveResult.ValidationFailed(errors)
        val projectId = requireNotNull(draft.projectId)
        val categoryId = requireNotNull(draft.categoryId)

        val now = clock.nowMillis()
        val expenseId = draft.id ?: ids.newId()
        val existing = draft.id?.let { expenseDao.getById(it) }
        val userId = activeUser.current

        val receiptLocalPath: String?
        val receiptRemotePath: String?
        val receiptState: ReceiptUploadState
        if (draft.receiptReplaced) {
            val newSource = draft.receiptLocalPath
            if (newSource == null) {
                receiptLocalPath = null
                receiptRemotePath = null
                receiptState = ReceiptUploadState.NONE
            } else {
                receiptLocalPath = receiptStore.persistReceipt(newSource, expenseId) ?: newSource
                receiptRemotePath = userId?.let { RemoteGateway.receiptPath(it, projectId, expenseId) }
                receiptState = ReceiptUploadState.PENDING
            }
        } else {
            receiptLocalPath = existing?.receiptLocalPath
            receiptRemotePath = existing?.receiptRemotePath
            receiptState = ReceiptUploadState.fromDb(existing?.receiptUploadState ?: ReceiptUploadState.NONE.dbValue)
        }

        val entity = ExpenseEntity(
            id = expenseId,
            projectId = projectId,
            categoryId = categoryId,
            supplierId = draft.supplierId?.takeIf { it.isNotBlank() },
            description = draft.description.trim(),
            quantityMilli = draft.quantityMilli,
            unitPriceKes = draft.unitPriceKes,
            amountKes = draft.amountKes,
            paymentMethod = draft.paymentMethod.dbValue,
            expenseDate = Dates.toIso(draft.expenseDate),
            notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            receiptLocalPath = receiptLocalPath,
            receiptRemotePath = receiptRemotePath,
            receiptUploadState = receiptState.dbValue,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            syncState = EntitySyncState.PENDING.dbValue,
            remoteUpdatedAt = existing?.remoteUpdatedAt,
        )

        db.withTransaction {
            expenseDao.upsert(entity)
            enqueue(entity, OutboxOperation.UPSERT)
            if (receiptState == ReceiptUploadState.PENDING && receiptLocalPath != null && receiptRemotePath != null) {
                outboxWriter.enqueueReceiptUpload(
                    outboxDao,
                    entity.id,
                    AppJson.encodeToString(ReceiptUploadPayload(entity.id, receiptLocalPath, receiptRemotePath)),
                )
            }
        }
        syncTrigger.requestSync()
        return SaveResult.Success(expenseId)
    }

    override suspend fun deleteExpense(id: String): SaveResult {
        if (expenseDao.getById(id) == null) return SaveResult.Failure("Expense not found")
        val now = clock.nowMillis()
        db.withTransaction {
            expenseDao.applyTombstone(id, deletedAt = now, updatedAt = now, syncState = EntitySyncState.PENDING.dbValue)
            expenseDao.getById(id)?.let { enqueue(it, OutboxOperation.DELETE) }
            // A queued receipt upload for a deleted expense is pointless; the local file is
            // removed after the server acknowledges the delete.
            outboxDao.deletePendingOps(OutboxEntityType.RECEIPT, id, OutboxOperation.UPLOAD_RECEIPT)
        }
        syncTrigger.requestSync()
        return SaveResult.Success(id)
    }

    override suspend fun ledger(filter: ExpenseFilter): List<LedgerRow> =
        expenseDao.ledger(
            projectId = filter.projectId,
            categoryId = filter.categoryId,
            paymentMethod = filter.paymentMethod?.dbValue,
            fromDate = filter.from?.let(Dates::toIso),
            toDate = filter.to?.let(Dates::toIso),
        ).map { row ->
            LedgerRow(
                expenseId = row.expense.id,
                date = Dates.parseIso(row.expense.expenseDate) ?: LocalDate.MIN,
                description = row.expense.description,
                categoryName = row.categoryName,
                supplierName = row.supplierName,
                paymentMethod = PaymentMethod.fromDb(row.expense.paymentMethod),
                amountKes = row.expense.amountKes,
                quantityText = row.expense.quantityMilli?.let { Money.formatQuantity(it) },
            )
        }

    override suspend fun categoryBreakdown(projectId: String?, from: ReportPeriod): List<CategoryTotal> =
        expenseDao.categoryBreakdown(
            projectId = projectId,
            fromDate = from.from?.let(Dates::toIso),
            toDate = from.to?.let(Dates::toIso),
        ).map { CategoryTotal(it.categoryId, it.categoryName, it.totalKes, it.expenseCount) }

    override suspend fun comparison(): List<ProjectComparisonRow> =
        expenseDao.projectComparison().map {
            ProjectComparisonRow(
                projectId = it.projectId,
                name = it.name,
                status = ProjectStatus.fromDb(it.status),
                budgetKes = it.budgetKes,
                spentKes = it.spentKes,
                expenseCount = it.expenseCount,
            )
        }

    private suspend fun enqueue(entity: ExpenseEntity, operation: String) {
        val owner = activeUser.current ?: ActiveUserStore.PENDING_OWNER
        outboxWriter.enqueueRowChange(
            outboxDao,
            OutboxEntityType.EXPENSE,
            entity.id,
            operation,
            AppJson.encodeToString(entity.toDto(owner)),
        )
    }
}
