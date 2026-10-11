package com.siteledger.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Financial rules (blueprint section 06). Budget is not revenue; remaining budget is not profit.
 */
object BudgetMath {

    const val DEFAULT_WARNING_THRESHOLD_PERCENT = 80

    fun spent(expenses: List<Expense>): Long =
        expenses.filter { it.deletedAt == null }.sumOf { it.amountKes }

    fun spentFromTotals(totals: List<ProjectTotals>): Long = totals.sumOf { it.totalKes }

    fun remaining(budgetKes: Long, spentKes: Long): Long = budgetKes - spentKes

    /**
     * Budget used percentage. Returns null for a zero budget so the UI can show a defined
     * state instead of dividing by zero.
     */
    fun percentUsed(budgetKes: Long, spentKes: Long): Double? =
        if (budgetKes <= 0L) null else spentKes * 100.0 / budgetKes

    fun isAtOrAboveThreshold(percentUsed: Double?, thresholdPercent: Int = DEFAULT_WARNING_THRESHOLD_PERCENT): Boolean =
        percentUsed != null && percentUsed >= thresholdPercent

    fun isOverBudget(percentUsed: Double?): Boolean = percentUsed != null && percentUsed > 100.0
}

data class ProjectSummary(
    val project: Project,
    val spentKes: Long,
    val expenseCount: Int,
    val warningThresholdPercent: Int = BudgetMath.DEFAULT_WARNING_THRESHOLD_PERCENT,
) {
    val remainingKes: Long get() = BudgetMath.remaining(project.budgetAmount, spentKes)
    val percentUsed: Double? get() = BudgetMath.percentUsed(project.budgetAmount, spentKes)
    val isOverBudget: Boolean get() = BudgetMath.isOverBudget(percentUsed)
    val isAtThreshold: Boolean get() = BudgetMath.isAtOrAboveThreshold(percentUsed, warningThresholdPercent)
}

data class DashboardSummary(
    val totalBudgetKes: Long,
    val totalSpentKes: Long,
    val totalRemainingKes: Long,
    val totalProjects: Int,
    val activeProjects: Int,
    val pendingSyncCount: Int,
    val recentExpenses: List<ExpenseWithContext>,
    val thresholdAlerts: List<ProjectSummary>,
)

data class CategoryTotal(
    val categoryId: String,
    val categoryName: String,
    val totalKes: Long,
    val expenseCount: Int,
)

data class LedgerRow(
    val expenseId: String,
    val date: LocalDate,
    val description: String,
    val categoryName: String,
    val supplierName: String?,
    val paymentMethod: PaymentMethod,
    val amountKes: Long,
    val quantityText: String?,
)

data class ProjectComparisonRow(
    val projectId: String,
    val name: String,
    val status: ProjectStatus,
    val budgetKes: Long,
    val spentKes: Long,
    val expenseCount: Int,
) {
    val remainingKes: Long get() = BudgetMath.remaining(budgetKes, spentKes)
    val percentUsed: Double? get() = BudgetMath.percentUsed(budgetKes, spentKes)
}

enum class ReportPreset { TODAY, THIS_WEEK, THIS_MONTH, LAST_MONTH, ALL_TIME, CUSTOM }

data class ReportPeriod(val from: LocalDate?, val to: LocalDate?) {
    fun contains(date: LocalDate): Boolean =
        (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))

    companion object {
        fun resolve(preset: ReportPreset, today: LocalDate): ReportPeriod = when (preset) {
            ReportPreset.TODAY -> ReportPeriod(today, today)
            ReportPreset.THIS_WEEK -> ReportPeriod(
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                today,
            )
            ReportPreset.THIS_MONTH -> ReportPeriod(today.withDayOfMonth(1), today)
            ReportPreset.LAST_MONTH -> {
                val lastMonth = YearMonth.from(today).minusMonths(1)
                ReportPeriod(lastMonth.atDay(1), lastMonth.atEndOfMonth())
            }
            ReportPreset.ALL_TIME -> ReportPeriod(null, null)
            ReportPreset.CUSTOM -> ReportPeriod(null, null)
        }
    }
}
