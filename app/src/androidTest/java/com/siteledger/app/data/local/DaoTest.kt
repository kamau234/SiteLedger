package com.siteledger.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.data.local.entity.ExpenseCategoryEntity
import com.siteledger.app.data.local.entity.ExpenseEntity
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.local.entity.OutboxStatus
import com.siteledger.app.data.local.entity.ProjectEntity
import com.siteledger.app.data.local.entity.SupplierEntity
import com.siteledger.app.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun category(id: String, name: String) = ExpenseCategoryEntity(
        id = id,
        name = name,
        isDefault = false,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncState = "synced",
    )

    private fun project(id: String) = ProjectEntity(
        id = id,
        name = "Riverside",
        clientName = null,
        location = null,
        budgetAmount = 100_000,
        startDate = null,
        expectedEndDate = null,
        status = "active",
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncState = "synced",
    )

    private fun expense(
        id: String,
        categoryId: String,
        amount: Long,
        date: String,
        deletedAt: Long? = null,
    ) = ExpenseEntity(
        id = id,
        projectId = "p1",
        categoryId = categoryId,
        supplierId = null,
        description = "Item $id",
        quantityMilli = null,
        unitPriceKes = null,
        amountKes = amount,
        paymentMethod = "cash",
        expenseDate = date,
        notes = null,
        receiptLocalPath = null,
        receiptRemotePath = null,
        receiptUploadState = "none",
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = deletedAt,
        syncState = "synced",
    )

    private fun outbox(
        id: String,
        entityType: String,
        operation: String = OutboxOperation.UPSERT,
        createdAt: Long,
        status: String = OutboxStatus.PENDING,
    ) = SyncOutboxEntity(
        id = id,
        entityType = entityType,
        entityId = id,
        operation = operation,
        payloadJson = "{}",
        createdAt = createdAt,
        status = status,
    )

    @Test
    fun outbox_uploads_parents_before_children() = runTest {
        val dao = db.outboxDao()
        dao.insert(outbox("receipt-1", OutboxEntityType.RECEIPT, createdAt = 1))
        dao.insert(outbox("expense-1", OutboxEntityType.EXPENSE, createdAt = 2))
        dao.insert(outbox("project-1", OutboxEntityType.PROJECT, createdAt = 3))
        dao.insert(outbox("supplier-1", OutboxEntityType.SUPPLIER, createdAt = 4))
        dao.insert(outbox("category-1", OutboxEntityType.EXPENSE_CATEGORY, createdAt = 5))

        val order = dao.nextPending(10).map { it.entityType }
        assertEquals(
            listOf(
                OutboxEntityType.EXPENSE_CATEGORY,
                OutboxEntityType.SUPPLIER,
                OutboxEntityType.PROJECT,
                OutboxEntityType.EXPENSE,
                OutboxEntityType.RECEIPT,
            ),
            order,
        )
    }

    @Test
    fun outbox_failed_entries_support_retry_and_keep_local() = runTest {
        val dao = db.outboxDao()
        dao.insert(outbox("o1", OutboxEntityType.PROJECT, createdAt = 1, status = OutboxStatus.FAILED))
        assertEquals(1, dao.observeFailedCount().first())

        dao.retryKeepingLocal("o1")
        val retried = dao.getById("o1")!!
        assertEquals(OutboxStatus.PENDING, retried.status)
        assertEquals(0, retried.attemptCount)
        assertTrue(retried.forceOverwrite)

        dao.markFailed("o1", "boom")
        dao.retry("o1")
        val plainRetry = dao.getById("o1")!!
        assertEquals(OutboxStatus.PENDING, plainRetry.status)
        assertFalse(plainRetry.forceOverwrite)
        assertEquals(0, dao.observeFailedCount().first())
    }

    @Test
    fun outbox_recovers_in_flight_entries_on_startup() = runTest {
        val dao = db.outboxDao()
        dao.insert(outbox("o1", OutboxEntityType.EXPENSE, createdAt = 1, status = OutboxStatus.IN_FLIGHT))
        val recovered = dao.resetInFlight()
        assertEquals(1, recovered)
        assertEquals(1, dao.observePendingCount().first())
    }

    @Test
    fun expense_queries_hide_tombstones_and_apply_period_filters() = runTest {
        db.categoryDao().upsert(category("c1", "Materials"))
        db.projectDao().upsert(project("p1"))
        db.expenseDao().upsertAll(
            listOf(
                expense("e1", "c1", 1_000, "2026-10-01"),
                expense("e2", "c1", 5_000, "2026-10-02", deletedAt = 99L),
                expense("e3", "c1", 2_000, "2026-09-15"),
            ),
        )

        val all = db.expenseDao().observeFiltered(null, null, null, null, null).first()
        assertEquals(listOf("e1", "e3"), all.map { it.expense.id })

        val october = db.expenseDao().ledger(null, null, null, "2026-10-01", "2026-10-31")
        assertEquals(listOf("e1"), october.map { it.expense.id })

        assertEquals(2, db.expenseDao().countForProject("p1"))
    }

    @Test
    fun category_breakdown_includes_categories_with_no_spend() = runTest {
        db.categoryDao().upsertAll(
            listOf(category("c1", "Materials"), category("c2", "Labour")),
        )
        db.projectDao().upsert(project("p1"))
        db.expenseDao().upsert(expense("e1", "c1", 2_500, "2026-10-01"))

        val rows = db.expenseDao().categoryBreakdown("p1", null, null)
        assertEquals(listOf("Materials", "Labour"), rows.map { it.categoryName })
        assertEquals(2_500L, rows.first { it.categoryId == "c1" }.totalKes)
        assertEquals(0L, rows.first { it.categoryId == "c2" }.totalKes)
        assertEquals(1, rows.first { it.categoryId == "c1" }.expenseCount)
        assertEquals(0, rows.first { it.categoryId == "c2" }.expenseCount)
    }

    @Test
    fun project_comparison_reports_zero_spend_for_empty_projects() = runTest {
        db.projectDao().upsert(project("p1"))
        db.categoryDao().upsert(category("c1", "Materials"))
        db.expenseDao().upsert(expense("e1", "c1", 3_000, "2026-10-01"))

        val rows = db.expenseDao().projectComparison()
        assertEquals(1, rows.size)
        assertEquals(3_000L, rows[0].spentKes)
        assertEquals(1, rows[0].expenseCount)
        assertEquals(100_000L, rows[0].budgetKes)
    }

    @Test
    fun metadata_round_trips_active_user_and_sync_flags() = runTest {
        val dao = db.appMetadataDao()
        dao.setActiveUser("user-1")
        dao.setLastSuccessfulSync(123L)
        dao.setInitialSyncComplete(true)

        val stored = dao.get()!!
        assertEquals("user-1", stored.activeUserId)
        assertEquals(123L, stored.lastSuccessfulSync)
        assertTrue(stored.initialSyncComplete)
    }

    @Test
    fun supplier_tombstone_sets_inactive_state() = runTest {
        val dao = db.supplierDao()
        dao.upsert(
            SupplierEntity(
                id = "s1",
                name = "Njoroge Hardware",
                phone = null,
                location = null,
                notes = null,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
                syncState = "synced",
            ),
        )
        dao.applyTombstone("s1", deletedAt = 50L, updatedAt = 60L, syncState = "pending")
        val stored = db.supplierDao().getAll().first { it.id == "s1" }
        assertEquals(50L, stored.deletedAt)
        assertEquals("pending", stored.syncState)
    }
}
