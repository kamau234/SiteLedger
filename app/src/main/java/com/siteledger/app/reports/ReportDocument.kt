package com.siteledger.app.reports

import com.siteledger.app.domain.model.ReportPeriod
import com.siteledger.app.domain.model.ReportPreset
import java.time.LocalDate

enum class ReportType(val title: String, val fileSlug: String) {
    PROJECT_SUMMARY("Project Financial Summary", "Project_Summary"),
    EXPENSE_LEDGER("Expense Ledger", "Expense_Ledger"),
    CATEGORY_BREAKDOWN("Category Breakdown", "Category_Breakdown"),
    PERIOD("Period Report", "Period_Report"),
    PROJECT_COMPARISON("Project Comparison", "Project_Comparison"),
}

data class ReportRequest(
    val type: ReportType,
    val projectId: String? = null,
    val preset: ReportPreset = ReportPreset.ALL_TIME,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
) {
    fun resolvePeriod(today: LocalDate): ReportPeriod =
        if (preset == ReportPreset.CUSTOM) ReportPeriod(customFrom, customTo)
        else ReportPeriod.resolve(preset, today)
}

data class ReportColumn(
    val title: String,
    val weight: Float,
    val alignEnd: Boolean = false,
    val numeric: Boolean = false,
)

/** Format-neutral report content consumed by both the PDF and XLSX writers. */
data class ReportDocument(
    val title: String,
    val fileSlug: String,
    val subtitle: String?,
    val periodLabel: String,
    val generatedAtLabel: String,
    val fileStamp: String,
    val companyName: String,
    val summaryRows: List<Pair<String, String>>,
    val tableColumns: List<ReportColumn>,
    val tableRows: List<List<String>>,
    val totalsRow: List<String>?,
    val notes: List<String>,
) {
    fun suggestedFileName(extension: String): String =
        "SiteLedger_${fileSlug}_$fileStamp.$extension"
}
