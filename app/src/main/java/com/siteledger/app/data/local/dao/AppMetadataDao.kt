package com.siteledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.siteledger.app.data.local.entity.AppMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetadataDao {

    @Query("SELECT * FROM app_metadata WHERE id = 1")
    fun observe(): Flow<AppMetadataEntity?>

    @Query("SELECT * FROM app_metadata WHERE id = 1")
    suspend fun get(): AppMetadataEntity?

    @Upsert
    suspend fun upsert(entity: AppMetadataEntity)

    /** Creates the singleton row when it does not exist yet (fresh install / first sync). */
    @Query(
        "INSERT OR IGNORE INTO app_metadata (id, active_user_id, last_successful_sync, initial_sync_complete) " +
            "VALUES (1, NULL, NULL, 0)",
    )
    suspend fun ensureRow()

    @Transaction
    suspend fun setActiveUser(userId: String?) {
        ensureRow()
        updateActiveUser(userId)
    }

    @Query("UPDATE app_metadata SET active_user_id = :userId WHERE id = 1")
    suspend fun updateActiveUser(userId: String?)

    @Transaction
    suspend fun setLastSuccessfulSync(timestampMillis: Long) {
        ensureRow()
        updateLastSuccessfulSync(timestampMillis)
    }

    @Query("UPDATE app_metadata SET last_successful_sync = :timestampMillis WHERE id = 1")
    suspend fun updateLastSuccessfulSync(timestampMillis: Long)

    @Transaction
    suspend fun setInitialSyncComplete(complete: Boolean) {
        ensureRow()
        updateInitialSyncComplete(complete)
    }

    @Query("UPDATE app_metadata SET initial_sync_complete = :complete WHERE id = 1")
    suspend fun updateInitialSyncComplete(complete: Boolean)
}
