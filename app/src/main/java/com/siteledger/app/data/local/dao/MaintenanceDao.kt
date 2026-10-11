package com.siteledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Maintenance operations used by the explicit "clear local data" sign-out flow.
 * Only executed after a user-facing confirmation that explains what is removed.
 */
@Dao
interface MaintenanceDao {

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()

    @Query("DELETE FROM projects")
    suspend fun clearProjects()

    @Query("DELETE FROM expense_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM suppliers")
    suspend fun clearSuppliers()

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query("DELETE FROM app_metadata")
    suspend fun clearMetadata()
}
