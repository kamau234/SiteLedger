package com.siteledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.siteledger.app.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

/**
 * Parent entities are uploaded before children so that foreign keys exist on the server
 * when a child row (expense) references them.
 */
private const val ORDER_BY_DEPENDENCY = """
    CASE entity_type
        WHEN 'expense_category' THEN 1
        WHEN 'supplier' THEN 2
        WHEN 'project' THEN 3
        WHEN 'expense' THEN 4
        WHEN 'receipt' THEN 5
        ELSE 9
    END
"""

@Dao
interface OutboxDao {

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'failed'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT * FROM sync_outbox WHERE status = 'failed' ORDER BY created_at ASC")
    fun observeFailed(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE status = 'pending' ORDER BY $ORDER_BY_DEPENDENCY, created_at ASC LIMIT :limit")
    suspend fun nextPending(limit: Int): List<SyncOutboxEntity>

    @Query(
        "SELECT COUNT(*) FROM sync_outbox WHERE entity_type = :entityType AND entity_id = :entityId " +
            "AND status = 'pending'",
    )
    suspend fun hasPendingFor(entityType: String, entityId: String): Boolean

    @Query(
        "DELETE FROM sync_outbox WHERE entity_type = :entityType AND entity_id = :entityId " +
            "AND status = 'pending' AND operation = :operation",
    )
    suspend fun deletePendingOps(entityType: String, entityId: String, operation: String)

    @Query(
        "DELETE FROM sync_outbox WHERE entity_type = :entityType AND entity_id = :entityId " +
            "AND status = 'pending' AND operation IN ('upsert', 'delete')",
    )
    suspend fun deletePendingRowOps(entityType: String, entityId: String)

    @Query("SELECT * FROM sync_outbox WHERE id = :id")
    suspend fun getById(id: String): SyncOutboxEntity?

    @Insert
    suspend fun insert(entry: SyncOutboxEntity)

    @Query("UPDATE sync_outbox SET payload_json = :payloadJson WHERE id = :id")
    suspend fun updatePayload(id: String, payloadJson: String)

    @Query("UPDATE sync_outbox SET status = 'in_flight' WHERE id = :id")
    suspend fun markInFlight(id: String)

    @Query("UPDATE sync_outbox SET attempt_count = attempt_count + 1, last_error = :error, status = 'pending' WHERE id = :id")
    suspend fun recordAttempt(id: String, error: String?)

    @Query("UPDATE sync_outbox SET status = 'failed', last_error = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String?)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Plain retry re-checks the server revision; a previous "keep local version" decision
     * must not silently persist, so force_overwrite is cleared.
     */
    @Query("UPDATE sync_outbox SET status = 'pending', attempt_count = 0, last_error = NULL, force_overwrite = 0 WHERE id = :id")
    suspend fun retry(id: String)

    @Query("UPDATE sync_outbox SET status = 'pending', attempt_count = 0, last_error = NULL, force_overwrite = 1 WHERE id = :id")
    suspend fun retryKeepingLocal(id: String)

    @Query("UPDATE sync_outbox SET status = 'pending', attempt_count = 0, last_error = NULL WHERE status = 'failed'")
    suspend fun retryAllFailed()

    /** Recovers entries left IN_FLIGHT by a process death mid-upload (exit-gate scenario). */
    @Query("UPDATE sync_outbox SET status = 'pending' WHERE status = 'in_flight'")
    suspend fun resetInFlight(): Int

    @Query("SELECT * FROM sync_outbox WHERE status = 'pending'")
    suspend fun getPending(): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET payload_json = :payloadJson WHERE id = :id")
    suspend fun updatePayloadById(id: String, payloadJson: String)

    @Query("UPDATE sync_outbox SET payload_json = :payloadJson WHERE entity_type = :entityType AND entity_id = :entityId AND status = 'pending'")
    suspend fun updatePayloadForEntity(entityType: String, entityId: String, payloadJson: String)
}
