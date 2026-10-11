package com.siteledger.app.reports

import com.siteledger.app.core.common.Money
import com.siteledger.app.core.common.label
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.Dates
import com.siteledger.app.domain.model.BudgetMath
import com.siteledger.app.domain.model.ExpenseFilter
import com.siteledger.app.domain.model.ReportPeriod
import com.siteledger.app.domain.repository.ExpenseRepository
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds report documents from local Room data only, so exports work offline. */
class ReportComposer(
    private val expenseRepository: ExpenseRepository,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
) {

    suspend fun build(request: ReportRequest): ReportDocument {
        val today = clock.today()
        val period = request.resolvePeriod(today)
        val company = settingsRepository.companyName.first()
        val generatedAt = Dates.formatMillisUtc(clock.nowMillis())
        val fileStamp = fileStampLabel(clock.nowMillis())

        return when (request.type) {
            ReportType.PROJECT_SUMMARY ->
                projectSummary(request, period, company, generatedAt, fileStamp)
            ReportType.EXPENSE_LEDGER ->
                ledgerReport(request, period, company, generatedAt, fileStamp, showPeriodSubtitle = false)
            ReportType.PERIOD ->
                ledgerReport(request, period, company, generatedAt, fileStamp, showPeriodSubtitle = true)
            ReportType.CATEGORY_BREAKDOWN ->
                categoryBreakdown(request, period, company, generatedAt, fileStamp)
            ReportType.PROJECT_COMPARISON ->
                comparison(company, generatedAt, fileStamp)
        }
    }

    private suspend fun projectSummary(
        request: ReportRequest,
        period: ReportPeriod,
        company: String,
        generatedAt: String,
        fileStamp: String,
    ): ReportDocument {
        val projectId = request.projectId
            ?: error("Select a project before exporting this report.")
        val project = projectRepository.getProject(projectId)
            ?: error("That project no longer exists.")
        val periodLabel = periodLabel(period)

        val allTimeSpend = expenseRepository.ledger(ExpenseFilter(projectId = projectId)).sumOf { it.amountKes }
        val remaining = BudgetMath.remaining(project.budgetAmount, allTimeSpend)
        val percent = BudgetMath.percentUsed(project.budgetAmount, allTimeSpend)
        val breakdown = expenseRepository.categoryBreakdown(projectId, period)
        val breakdownTotal = breakdown.sumOf { it.totalKes }
        val breakdownCount = breakdown.sumOf { it.expenseCount }

        val summaryRows = buildList {
            add("Client" to (project.clientName ?: "—"))
            add("Location" to (project.location ?: "—"))
            add("Status" to project.status.label())
            add("Approved budget" to Money.formatKes(project.budgetAmount))
            add("Total recorded expense (all time)" to Money.formatKes(allTimeSpend))
            add("Remaining budget" to Money.formatKes(remaining))
            add("Budget used" to (percent?.let { formatPercent(it) } ?: "No budget set"))
        }

        return ReportDocument(
            title = ReportType.PROJECT_SUMMARY.title,
            fileSlug = ReportType.PROJECT_SUMMARY.fileSlug,
            subtitle = project.name,
            periodLabel = periodLabel,
            generatedAtLabel = generatedAt,
            fileStamp = fileStamp,
            companyName = company,
            summaryRows = summaryRows,
            tableColumns = listOf(
                ReportColumn("Category", 3f),
                ReportColumn("Expenses", 1.2f, alignEnd = true, numeric = true),
                ReportColumn("Total (KES)", 2f, alignEnd = true, numeric = true),
            ),
            tableRows = if (breakdown.isEmpty()) {
                listOf(listOf("No expenses recorded for this period", "", ""))
            } else {
                breakdown.map { row ->
                    listOf(row.categoryName, row.expenseCount.toString(), Money.grouped(row.totalKes))
                }
            },
            totalsRow = if (breakdown.isEmpty()) null else listOf(
                "Total",
                breakdownCount.toString(),
                Money.grouped(breakdownTotal),
            ),
            notes = listOf(
                "Remaining budget is not profit — it is the approved budget less recorded expenses.",
                "Summary figures cover all time; the table covers $periodLabel.",
            ),
        )
    }

    private suspend fun ledgerReport(
        request: ReportRequest,
        period: ReportPeriod,
        company: String,
        generatedAt: String,
        fileStamp: String,
        showPeriodSubtitle: Boolean,
    ): ReportDocument {
        val projectName = request.projectId?.let { projectRepository.getProject(it)?.name }
        val periodLabel = periodLabel(period)
        val rows = expenseRepository.ledger(
            ExpenseFilter(projectId = request.projectId, from = period.from, to = period.to),
        )
        val total = rows.sumOf { it.amountKes }
        val type = if (showPeriodSubtitle) ReportType.PERIOD else ReportType.EXPENSE_LEDGER

        val summaryRows = buildList {
            if (projectName != null) add("Project" to projectName)
            add("Transactions" to rows.size.toString())
            add("Total recorded expense" to Money.formatKes(total))
        }

        return ReportDocument(
            title = type.title,
            fileSlug = type.fileSlug,
            subtitle = if (showPeriodSubtitle) "Expenses recorded in the selected period" else null,
            periodLabel = periodLabel,
            generatedAtLabel = generatedAt,
            fileStamp = fileStamp,
            companyName = company,
            summaryRows = summaryRows,
            tableColumns = listOf(
                ReportColumn("Date", 1.1f),
                ReportColumn("Description", 2.6f),
                ReportColumn("Category", 1.4f),
                ReportColumn("Supplier", 1.6f),
                ReportColumn("Payment", 1.2f),
                ReportColumn("Amount (KES)", 1.2f, alignEnd = true, numeric = true),
            ),
            tableRows = if (rows.isEmpty()) {
                listOf(listOf("No expenses recorded for this period", "", "", "", "", ""))
            } else {
                rows.map { row ->
                    listOf(
                        Dates.format(row.date),
                        row.description,
                        row.categoryName,
                        row.supplierName ?: "—",
                        row.paymentMethod.label(),
                        Money.grouped(row.amountKes),
                    )
                }
            },
            totalsRow = if (rows.isEmpty()) null else listOf(
                "Total", "", "", "", "", Money.grouped(total),
            ),
            notes = listOf("All amounts are in Kenyan Shillings (KES)."),
        )
    }

    private suspend fun categoryBreakdown(
        request: ReportRequest,
        period: ReportPeriod,
        company: String,
        generatedAt: String,
        fileStamp: String,
    ): ReportDocument {
        val periodLabel = periodLabel(period)
        val projectName = request.projectId?.let { projectRepository.getProject(it)?.name }
        val breakdown = expenseRepository.categoryBreakdown(request.projectId, period)
        val total = breakdown.sumOf { it.totalKes }
        val count = breakdown.sumOf { it.expenseCount }

        val summaryRows = buildList {
            if (projectName != null) add("Project" to projectName)
            add("Categories used" to breakdown.size.toString())
            add("Total recorded expense" to Money.formatKes(total))
        }

        return ReportDocument(
            title = ReportType.CATEGORY_BREAKDOWN.title,
            fileSlug = ReportType.CATEGORY_BREAKDOWN.fileSlug,
            subtitle = projectName?.let { "Project: $it" },
            periodLabel = periodLabel,
            generatedAtLabel = generatedAt,
            fileStamp = fileStamp,
            companyName = company,
            summaryRows = summaryRows,
            tableColumns = listOf(
                ReportColumn("Category", 3f),
                ReportColumn("Expenses", 1.2f, alignEnd = true, numeric = true),
                ReportColumn("Total (KES)", 2f, alignEnd = true, numeric = true),
            ),
            tableRows = if (breakdown.isEmpty()) {
                listOf(listOf("No expenses recorded for this period", "", ""))
            } else {
                breakdown.map { row ->
                    listOf(row.categoryName, row.expenseCount.toString(), Money.grouped(row.totalKes))
                }
            },
            totalsRow = if (breakdown.isEmpty()) null else listOf(
                "Total", count.toString(), Money.grouped(total),
            ),
            notes = listOf("All amounts are in Kenyan Shillings (KES)."),
        )
    }

    private suspend fun comparison(
        company: String,
        generatedAt: String,
        fileStamp: String,
    ): ReportDocument {
        val rows = expenseRepository.comparison()
        val totalBudget = rows.sumOf { it.budgetKes }
        val totalSpent = rows.sumOf { it.spentKes }
        val totalRemaining = rows.sumOf { it.remainingKes }

        return ReportDocument(
            title = ReportType.PROJECT_COMPARISON.title,
            fileSlug = ReportType.PROJECT_COMPARISON.fileSlug,
            subtitle = null,
            periodLabel = "All time",
            generatedAtLabel = generatedAt,
            fileStamp = fileStamp,
            companyName = company,
            summaryRows = listOf(
                "Projects" to rows.size.toString(),
                "Total approved budget" to Money.formatKes(totalBudget),
                "Total recorded expense" to Money.formatKes(totalSpent),
            ),
            tableColumns = listOf(
                ReportColumn("Project", 3f),
                ReportColumn("Status", 1.3f),
                ReportColumn("Budget (KES)", 1.6f, alignEnd = true, numeric = true),
                ReportColumn("Spent (KES)", 1.6f, alignEnd = true, numeric = true),
                ReportColumn("Remaining (KES)", 1.7f, alignEnd = true, numeric = true),
                ReportColumn("Used", 1.2f, alignEnd = true),
            ),
            tableRows = if (rows.isEmpty()) {
                listOf(listOf("No projects yet", "", "", "", "", ""))
            } else {
                rows.map { row ->
                    listOf(
                        row.name,
                        row.status.label(),
                        Money.grouped(row.budgetKes),
                        Money.grouped(row.spentKes),
                        Money.grouped(row.remainingKes),
                        row.percentUsed?.let { formatPercent(it) } ?: "—",
                    )
                }
            },
            totalsRow = if (rows.isEmpty()) null else listOf(
                "Total", "",
                Money.grouped(totalBudget),
                Money.grouped(totalSpent),
                Money.grouped(totalRemaining),
                "",
            ),
            notes = listOf(
                "Remaining budget is not profit — it is the approved budget less recorded expenses.",
                "Comparison figures cover all projects and all time.",
            ),
        )
    }

    private fun periodLabel(period: ReportPeriod): String =
        if (period.from == null && period.to == null) "All time"
        else "${Dates.format(period.from)} – ${Dates.format(period.to)}"

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)

    private fun fileStampLabel(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US)
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}
