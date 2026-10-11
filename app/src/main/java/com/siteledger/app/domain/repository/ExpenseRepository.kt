package com.siteledger.app.domain.repository

import com.siteledger.app.domain.model.CategoryTotal
import com.siteledger.app.domain.model.Expense
import com.siteledger.app.domain.model.ExpenseDraft
import com.siteledger.app.domain.model.ExpenseFilter
import com.siteledger.app.domain.model.ExpenseWithContext
import com.siteledger.app.domain.model.LedgerRow
import com.siteledger.app.domain.model.ProjectComparisonRow
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun observeExpenses(filter: ExpenseFilter): Flow<List<ExpenseWithContext>>
    fun observeRecentExpenses(limit: Int): Flow<List<ExpenseWithContext>>
    fun observeExpense(id: String): Flow<Expense?>
    suspend fun getExpense(id: String): Expense?
    suspend fun saveExpense(draft: ExpenseDraft): SaveResult
    suspend fun deleteExpense(id: String): SaveResult
    suspend fun countForProject(projectId: String): Int

    // Report queries (local Room data; usable without connectivity).
    suspend fun ledger(filter: ExpenseFilter): List<LedgerRow>
    suspend fun categoryBreakdown(projectId: String?, from: com.siteledger.app.domain.model.ReportPeriod): List<CategoryTotal>
    suspend fun comparison(): List<ProjectComparisonRow>
}
