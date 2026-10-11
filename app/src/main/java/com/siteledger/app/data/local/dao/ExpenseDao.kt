package com.siteledger.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.siteledger.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

data class ExpenseWithNames(
    @Embedded val expense: ExpenseEntity,
    @ColumnInfo(name = "project_name") val projectName: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "supplier_name") val supplierName: String?,
)

data class ProjectTotalsRow(
    @ColumnInfo(name = "projectId") val projectId: String,
    @ColumnInfo(name = "totalKes") val totalKes: Long,
    @ColumnInfo(name = "expenseCount") val expenseCount: Int,
)

data class CategoryTotalRow(
    @ColumnInfo(name = "categoryId") val categoryId: String,
    @ColumnInfo(name = "categoryName") val categoryName: String,
    @ColumnInfo(name = "totalKes") val totalKes: Long,
    @ColumnInfo(name = "expenseCount") val expenseCount: Int,
)

private const val BASE_SELECT = """
    SELECT e.*, p.name AS project_name, c.name AS category_name, s.name AS supplier_name
    FROM expenses e
    JOIN projects p ON p.id = e.project_id
    JOIN expense_categories c ON c.id = e.category_id
    LEFT JOIN suppliers s ON s.id = e.supplier_id
"""

private const val FILTER_CLAUSE = """
    WHERE e.deleted_at IS NULL
    AND (:projectId IS NULL OR e.project_id = :projectId)
    AND (:categoryId IS NULL OR e.category_id = :categoryId)
    AND (:paymentMethod IS NULL OR e.payment_method = :paymentMethod)
    AND (:fromDate IS NULL OR e.expense_date >= :fromDate)
    AND (:toDate IS NULL OR e.expense_date <= :toDate)
"""

@Dao
interface ExpenseDao {

    @Query("$BASE_SELECT $FILTER_CLAUSE ORDER BY e.expense_date DESC, e.created_at DESC")
    fun observeFiltered(
        projectId: String?,
        categoryId: String?,
        paymentMethod: String?,
        fromDate: String?,
        toDate: String?,
    ): Flow<List<ExpenseWithNames>>

    @Query("$BASE_SELECT WHERE e.deleted_at IS NULL ORDER BY e.expense_date DESC, e.created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExpenseWithNames>>

    @Query("$BASE_SELECT WHERE e.deleted_at IS NULL AND e.project_id = :projectId ORDER BY e.expense_date DESC, e.created_at DESC")
    fun observeByProject(projectId: String): Flow<List<ExpenseWithNames>>

    @Query("$BASE_SELECT $FILTER_CLAUSE ORDER BY e.expense_date ASC, e.created_at ASC")
    suspend fun ledger(
        projectId: String?,
        categoryId: String?,
        paymentMethod: String?,
        fromDate: String?,
        toDate: String?,
    ): List<ExpenseWithNames>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: String): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT COUNT(*) FROM expenses WHERE deleted_at IS NULL AND project_id = :projectId")
    suspend fun countForProject(projectId: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE deleted_at IS NULL AND category_id = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    @Query("SELECT id FROM expenses WHERE deleted_at IS NULL AND category_id = :categoryId")
    suspend fun getIdsForCategory(categoryId: String): List<String>

    @Query(
        "UPDATE expenses SET category_id = :toCategoryId, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE category_id = :fromCategoryId AND deleted_at IS NULL",
    )
    suspend fun reparentCategories(fromCategoryId: String, toCategoryId: String, updatedAt: Long, syncState: String)

    @Query("SELECT COUNT(*) FROM expenses WHERE deleted_at IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query(
        "SELECT project_id AS projectId, SUM(amount_kes) AS totalKes, COUNT(*) AS expenseCount " +
            "FROM expenses WHERE deleted_at IS NULL GROUP BY project_id",
    )
    fun observeTotalsByProject(): Flow<List<ProjectTotalsRow>>

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName,
               COALESCE(SUM(e.amount_kes), 0) AS totalKes, COUNT(e.id) AS expenseCount
        FROM expense_categories c
        LEFT JOIN expenses e ON e.category_id = c.id AND e.deleted_at IS NULL
            AND (:projectId IS NULL OR e.project_id = :projectId)
            AND (:fromDate IS NULL OR e.expense_date >= :fromDate)
            AND (:toDate IS NULL OR e.expense_date <= :toDate)
        WHERE c.deleted_at IS NULL
        GROUP BY c.id, c.name
        ORDER BY totalKes DESC, c.name ASC
        """,
    )
    suspend fun categoryBreakdown(projectId: String?, fromDate: String?, toDate: String?): List<CategoryTotalRow>

    @Query(
        """
        SELECT p.id AS projectId, p.name AS name, p.status AS status,
               p.budget_amount AS budgetKes, p.expected_end_date AS expectedEndDate,
               COALESCE(SUM(CASE WHEN e.deleted_at IS NULL THEN e.amount_kes ELSE 0 END), 0) AS spentKes,
               COALESCE(SUM(CASE WHEN e.deleted_at IS NULL THEN 1 ELSE 0 END), 0) AS expenseCount
        FROM projects p
        LEFT JOIN expenses e ON e.project_id = p.id
        WHERE p.deleted_at IS NULL
        GROUP BY p.id
        ORDER BY p.name ASC
        """,
    )
    suspend fun projectComparison(): List<ProjectComparisonRowRaw>

    @Upsert
    suspend fun upsert(entity: ExpenseEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ExpenseEntity>)

    @Query(
        "UPDATE expenses SET deleted_at = :deletedAt, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE id = :id",
    )
    suspend fun applyTombstone(id: String, deletedAt: Long, updatedAt: Long, syncState: String)

    @Query("UPDATE expenses SET sync_state = :syncState WHERE id = :id")
    suspend fun setSyncState(id: String, syncState: String)

    @Query("UPDATE expenses SET remote_updated_at = :timestampMillis WHERE id = :id")
    suspend fun setRemoteUpdatedAt(id: String, timestampMillis: Long?)

    @Query("UPDATE expenses SET sync_state = 'failed' WHERE id = :id")
    suspend fun markSyncFailed(id: String)

    @Query("UPDATE expenses SET receipt_remote_path = :remotePath, receipt_upload_state = :uploadState WHERE id = :id")
    suspend fun setReceiptUpload(id: String, remotePath: String?, uploadState: String)

    /** Receipts saved before any cloud user was active; the sync engine derives their paths. */
    @Query(
        "SELECT * FROM expenses WHERE deleted_at IS NULL AND receipt_local_path IS NOT NULL " +
            "AND receipt_remote_path IS NULL AND receipt_upload_state = 'pending'",
    )
    suspend fun receiptUploadsMissingRemotePath(): List<ExpenseEntity>

    @Query("UPDATE expenses SET receipt_local_path = :localPath, receipt_upload_state = :uploadState, updated_at = :updatedAt WHERE id = :id")
    suspend fun setReceiptLocal(id: String, localPath: String?, uploadState: String, updatedAt: Long)
}

data class ProjectComparisonRowRaw(
    @ColumnInfo(name = "projectId") val projectId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "budgetKes") val budgetKes: Long,
    @ColumnInfo(name = "expectedEndDate") val expectedEndDate: String?,
    @ColumnInfo(name = "spentKes") val spentKes: Long,
    @ColumnInfo(name = "expenseCount") val expenseCount: Int,
)
