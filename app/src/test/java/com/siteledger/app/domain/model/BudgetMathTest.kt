package com.siteledger.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetMathTest {

    private fun expense(
        id: String,
        amountKes: Long,
        deletedAt: Long? = null,
    ) = Expense(
        id = id,
        projectId = "p1",
        categoryId = "c1",
        supplierId = null,
        description = "Test expense",
        quantityMilli = null,
        unitPriceKes = null,
        amountKes = amountKes,
        paymentMethod = PaymentMethod.CASH,
        expenseDate = LocalDate.of(2026, 10, 1),
        notes = null,
        receiptLocalPath = null,
        receiptRemotePath = null,
        receiptUploadState = ReceiptUploadState.NONE,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = deletedAt,
        syncState = EntitySyncState.SYNCED,
    )

    @Test
    fun `percentUsed is null for a zero or negative budget`() {
        assertNull(BudgetMath.percentUsed(0, 500))
        assertNull(BudgetMath.percentUsed(-100, 500))
    }

    @Test
    fun `percentUsed computes the used share of the budget`() {
        assertEquals(80.0, BudgetMath.percentUsed(1000, 800)!!, 0.0001)
        assertEquals(100.0, BudgetMath.percentUsed(1000, 1000)!!, 0.0001)
        assertEquals(100.1, BudgetMath.percentUsed(1000, 1001)!!, 0.0001)
    }

    @Test
    fun `threshold check triggers at or above the configured percent`() {
        assertTrue(BudgetMath.isAtOrAboveThreshold(80.0, 80))
        assertFalse(BudgetMath.isAtOrAboveThreshold(79.9, 80))
        assertFalse(BudgetMath.isAtOrAboveThreshold(null))
    }

    @Test
    fun `over budget requires strictly more than one hundred percent`() {
        assertFalse(BudgetMath.isOverBudget(100.0))
        assertTrue(BudgetMath.isOverBudget(100.01))
        assertFalse(BudgetMath.isOverBudget(null))
    }

    @Test
    fun `remaining budget can go negative`() {
        assertEquals(600L, BudgetMath.remaining(1000, 400))
        assertEquals(-200L, BudgetMath.remaining(1000, 1200))
    }

    @Test
    fun `spent ignores deleted expenses`() {
        val expenses = listOf(
            expense("e1", 500),
            expense("e2", 250),
            expense("e3", 999, deletedAt = 123L),
        )
        assertEquals(750L, BudgetMath.spent(expenses))
    }

    @Test
    fun `project summary exposes derived values`() {
        val project = Project(
            id = "p1",
            name = "Riverside",
            clientName = null,
            location = null,
            budgetAmount = 1000,
            startDate = null,
            expectedEndDate = null,
            status = ProjectStatus.ACTIVE,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
            syncState = EntitySyncState.SYNCED,
        )
        val summary = ProjectSummary(project = project, spentKes = 800, expenseCount = 2)
        assertEquals(200L, summary.remainingKes)
        assertEquals(80.0, summary.percentUsed!!, 0.0001)
        assertTrue(summary.isAtThreshold)
        assertFalse(summary.isOverBudget)
    }
}
