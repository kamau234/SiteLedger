package com.siteledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.siteledger.app.data.local.entity.ExpenseCategoryEntity
import com.siteledger.app.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM expense_categories WHERE deleted_at IS NULL ORDER BY name ASC")
    fun observeActive(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun getById(id: String): ExpenseCategoryEntity?

    @Query("SELECT * FROM expense_categories WHERE deleted_at IS NULL")
    suspend fun getActive(): List<ExpenseCategoryEntity>

    @Query("SELECT * FROM expense_categories WHERE deleted_at IS NULL AND lower(name) = lower(:name)")
    suspend fun getActiveByName(name: String): List<ExpenseCategoryEntity>

    @Query("SELECT * FROM expense_categories")
    suspend fun getAll(): List<ExpenseCategoryEntity>

    @Query("DELETE FROM expense_categories WHERE id = :id")
    suspend fun deleteRow(id: String)

    @Upsert
    suspend fun upsert(entity: ExpenseCategoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ExpenseCategoryEntity>)

    @Query(
        "UPDATE expense_categories SET deleted_at = :deletedAt, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE id = :id",
    )
    suspend fun applyTombstone(id: String, deletedAt: Long, updatedAt: Long, syncState: String)

    @Query("UPDATE expense_categories SET sync_state = :syncState WHERE id = :id")
    suspend fun setSyncState(id: String, syncState: String)

    @Query("UPDATE expense_categories SET remote_updated_at = :timestampMillis WHERE id = :id")
    suspend fun setRemoteUpdatedAt(id: String, timestampMillis: Long?)

    @Query("UPDATE expense_categories SET sync_state = 'failed' WHERE id = :id")
    suspend fun markSyncFailed(id: String)
}

@Dao
interface SupplierDao {

    @Query("SELECT * FROM suppliers WHERE deleted_at IS NULL ORDER BY name ASC")
    fun observeActive(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getById(id: String): SupplierEntity?

    @Query("SELECT * FROM suppliers")
    suspend fun getAll(): List<SupplierEntity>

    @Upsert
    suspend fun upsert(entity: SupplierEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SupplierEntity>)

    @Query(
        "UPDATE suppliers SET deleted_at = :deletedAt, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE id = :id",
    )
    suspend fun applyTombstone(id: String, deletedAt: Long, updatedAt: Long, syncState: String)

    @Query("UPDATE suppliers SET sync_state = :syncState WHERE id = :id")
    suspend fun setSyncState(id: String, syncState: String)

    @Query("UPDATE suppliers SET remote_updated_at = :timestampMillis WHERE id = :id")
    suspend fun setRemoteUpdatedAt(id: String, timestampMillis: Long?)

    @Query("UPDATE suppliers SET sync_state = 'failed' WHERE id = :id")
    suspend fun markSyncFailed(id: String)
}
