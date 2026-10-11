package com.siteledger.app.reports

import com.siteledger.app.core.util.AppClock
import com.siteledger.app.domain.model.CategoryTotal
import com.siteledger.app.domain.model.Expense
import com.siteledger.app.domain.model.ExpenseDraft
import com.siteledger.app.domain.model.ExpenseFilter
import com.siteledger.app.domain.model.ExpenseWithContext
import com.siteledger.app.domain.model.LedgerRow
import com.siteledger.app.domain.model.PaymentMethod
import com.siteledger.app.domain.model.Project
import com.siteledger.app.domain.model.ProjectComparisonRow
import com.siteledger.app.domain.model.ProjectDraft
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.model.ProjectTotals
import com.siteledger.app.domain.model.ReportPeriod
import com.siteledger.app.domain.model.ReportPreset
import com.siteledger.app.domain.repository.ExpenseRepository
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SaveResult
import com.siteledger.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportComposerTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 11)

    private class FixedClock(private val today: LocalDate, private val millis: Long) : AppClock {
        override fun nowMillis(): Long = millis
        override fun today(): LocalDate = today
    }

    private class FakeSettingsRepository(private val name: String = "Mwangaza Builders") : SettingsRepository {
        override val companyName: Flow<String> = flowOf(name)
        override val warningThresholdPercent: Flow<Int> = flowOf(80)
        override suspend fun setCompanyName(name: String) = Unit
        override suspend fun setWarningThresholdPercent(percent: Int) = Unit
    }

    private class FakeProjectRepository(private val projects: Map<String, Project>) : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = flowOf(projects.values.toList())
        override fun observeProject(id: String): Flow<Project?> = flowOf(projects[id])
        override fun observeProjectTotals(): Flow<List<ProjectTotals>> = flowOf(emptyList())
        override suspend fun getProject(id: String): Project? = projects[id]
        override suspend fun saveProject(draft: ProjectDraft): SaveResult = SaveResult.Failure("unused")
        override suspend fun archiveProject(id: String): SaveResult = SaveResult.Failure("unused")
        override suspend fun deleteProject(id: String): SaveResult = SaveResult.Failure("unused")
    }

    private class FakeExpenseRepository(
        private val ledgerRows: List<LedgerRow> = emptyList(),
        private val categoryTotals: List<CategoryTotal> = emptyList(),
        private val comparisonRows: List<ProjectComparisonRow> = emptyList(),
    ) : ExpenseRepository {
        override fun observeExpenses(filter: ExpenseFilter): Flow<List<ExpenseWithContext>> = flowOf(emptyList())
        override fun observeRecentExpenses(limit: Int): Flow<List<ExpenseWithContext>> = flowOf(emptyList())
        override fun observeExpense(id: String): Flow<Expense?> = flowOf(null)
        override suspend fun getExpense(id: String): Expense? = null
        override suspend fun saveExpense(draft: ExpenseDraft): SaveResult = SaveResult.Failure("unused")
        override suspend fun deleteExpense(id: String): SaveResult = SaveResult.Failure("unused")
        override suspend fun countForProject(projectId: String): Int = 0
        override suspend fun ledger(filter: ExpenseFilter): List<LedgerRow> = ledgerRows
        override suspend fun categoryBreakdown(projectId: String?, from: ReportPeriod): List<CategoryTotal> =
            categoryTotals

        override suspend fun comparison(): List<ProjectComparisonRow> = comparisonRows
    }

    private fun project(id: String = "p1", budget: Long = 500_000) = Project(
        id = id,
        name = "Riverside Villas",
        clientName = "Achieng Properties",
        location = "Kiambu Road",
        budgetAmount = budget,
        startDate = LocalDate.of(2026, 1, 5),
        expectedEndDate = LocalDate.of(2026, 12, 20),
        status = ProjectStatus.ACTIVE,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncState = com.siteledger.app.domain.model.EntitySyncState.SYNCED,
    )

    private fun ledgerRow(id: String, amount: Long, date: LocalDate = LocalDate.of(2026, 10, 2)) = LedgerRow(
        expenseId = id,
        date = date,
        description = "Cement bags",
        categoryName = "Materials",
        supplierName = null,
        paymentMethod = PaymentMethod.CASH,
        amountKes = amount,
        quantityText = null,
    )

    private fun composer(
        projects: Map<String, Project> = mapOf("p1" to project()),
        expenses: FakeExpenseRepository = FakeExpenseRepository(),
    ) = ReportComposer(
        expenseRepository = expenses,
        projectRepository = FakeProjectRepository(projects),
        settingsRepository = FakeSettingsRepository(),
        clock = FixedClock(today, 1_760_000_000_000L),
    )

    @Test
    fun `project summary requires an explicit project`() = runTest {
        val error = runCatching {
            composer().build(ReportRequest(type = ReportType.PROJECT_SUMMARY))
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("Select a project before exporting this report.", error?.message)
    }

    @Test
    fun `project summary shows a defined state for a zero budget project`() = runTest {
        val expenses = FakeExpenseRepository(ledgerRows = listOf(ledgerRow("e1", 500)))
        val doc = composer(
            projects = mapOf("p1" to project(budget = 0)),
            expenses = expenses,
        ).build(ReportRequest(type = ReportType.PROJECT_SUMMARY, projectId = "p1"))

        assertEquals("Mwangaza Builders", doc.companyName)
        assertEquals("Riverside Villas", doc.subtitle)
        assertEquals("All time", doc.periodLabel)
        val summary = doc.summaryRows.toMap()
        assertEquals("KSh 0", summary["Approved budget"])
        assertEquals("KSh 500", summary["Total recorded expense (all time)"])
        assertEquals("KSh -500", summary["Remaining budget"])
        assertEquals("No budget set", summary["Budget used"])
        assertEquals(listOf(listOf("No expenses recorded for this period", "", "")), doc.tableRows)
        assertNull(doc.totalsRow)
    }

    @Test
    fun `project summary computes percent used and category totals`() = runTest {
        val expenses = FakeExpenseRepository(
            ledgerRows = listOf(ledgerRow("e1", 400_000)),
            categoryTotals = listOf(
                CategoryTotal("c1", "Materials", 300_000, 2),
                CategoryTotal("c2", "Labour", 100_000, 1),
            ),
        )
        val doc = composer(expenses = expenses)
            .build(ReportRequest(type = ReportType.PROJECT_SUMMARY, projectId = "p1"))

        assertEquals("80.0%", doc.summaryRows.toMap()["Budget used"])
        assertEquals(
            listOf("Materials", "2", "300,000"),
            doc.tableRows[0],
        )
        assertEquals(listOf("Total", "3", "400,000"), doc.totalsRow)
    }

    @Test
    fun `expense ledger groups amounts and totals the period`() = runTest {
        val expenses = FakeExpenseRepository(
            ledgerRows = listOf(
                ledgerRow("e1", 1_200, LocalDate.of(2026, 10, 1)),
                ledgerRow("e2", 300, LocalDate.of(2026, 10, 3)),
            ),
        )
        val doc = composer(expenses = expenses).build(
            ReportRequest(type = ReportType.EXPENSE_LEDGER, preset = ReportPreset.ALL_TIME),
        )

        assertNull(doc.subtitle)
        assertEquals("All time", doc.periodLabel)
        assertEquals("1,200", doc.tableRows[0].last())
        assertEquals("—", doc.tableRows[0][3])
        assertEquals(listOf("Total", "", "", "", "", "1,500"), doc.totalsRow)
        assertEquals("Transactions", doc.summaryRows.toMap().keys.first())
    }

    @Test
    fun `today preset narrows the period label`() = runTest {
        val doc = composer().build(
            ReportRequest(type = ReportType.EXPENSE_LEDGER, preset = ReportPreset.TODAY),
        )
        assertEquals("11 Oct 2026 – 11 Oct 2026", doc.periodLabel)
    }

    @Test
    fun `empty ledger falls back to a friendly row and no totals`() = runTest {
        val doc = composer().build(ReportRequest(type = ReportType.EXPENSE_LEDGER))
        assertEquals(listOf(listOf("No expenses recorded for this period", "", "", "", "", "")), doc.tableRows)
        assertNull(doc.totalsRow)
    }

    @Test
    fun `comparison covers all projects and handles zero budgets`() = runTest {
        val expenses = FakeExpenseRepository(
            comparisonRows = listOf(
                ProjectComparisonRow(
                    projectId = "p1",
                    name = "Riverside Villas",
                    status = ProjectStatus.ACTIVE,
                    budgetKes = 1_000,
                    spentKes = 800,
                    expenseCount = 2,
                ),
                ProjectComparisonRow(
                    projectId = "p2",
                    name = "Lakeview Apartments",
                    status = ProjectStatus.PLANNED,
                    budgetKes = 0,
                    spentKes = 150,
                    expenseCount = 1,
                ),
            ),
        )
        val doc = composer(expenses = expenses).build(ReportRequest(type = ReportType.PROJECT_COMPARISON))

        assertEquals("All time", doc.periodLabel)
        assertEquals(listOf("80.0%", "—"), doc.tableRows.map { it.last() })
        assertEquals(listOf("Total", "", "1,000", "950", "50", ""), doc.totalsRow)
    }

    @Test
    fun `suggested file name carries slug and stamp`() = runTest {
        val doc = composer().build(ReportRequest(type = ReportType.PROJECT_COMPARISON))
        assertTrue(doc.suggestedFileName("pdf").startsWith("SiteLedger_Project_Comparison_"))
        assertTrue(doc.suggestedFileName("xlsx").endsWith(".xlsx"))
    }
}
