package com.siteledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.siteledger.app.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT COUNT(*) FROM projects WHERE deleted_at IS NULL")
    suspend fun countActive(): Int

    @Upsert
    suspend fun upsert(entity: ProjectEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ProjectEntity>)

    @Query(
        "UPDATE projects SET deleted_at = :deletedAt, updated_at = :updatedAt, sync_state = :syncState " +
            "WHERE id = :id",
    )
    suspend fun applyTombstone(id: String, deletedAt: Long, updatedAt: Long, syncState: String)

    @Query("UPDATE projects SET sync_state = :syncState WHERE id = :id")
    suspend fun setSyncState(id: String, syncState: String)

    @Query("UPDATE projects SET remote_updated_at = :timestampMillis WHERE id = :id")
    suspend fun setRemoteUpdatedAt(id: String, timestampMillis: Long?)

    @Query("UPDATE projects SET sync_state = 'failed' WHERE id = :id")
    suspend fun markSyncFailed(id: String)
}
