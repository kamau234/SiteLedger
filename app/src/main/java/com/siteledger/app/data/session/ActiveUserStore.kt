package com.siteledger.app.data.session

import com.siteledger.app.data.local.dao.AppMetadataDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the id that local records are associated with (the Supabase user id, or a
 * locally generated id in local-only mode). Persisted in app_metadata.
 */
class ActiveUserStore(private val metadataDao: AppMetadataDao) {

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId

    val current: String?
        get() = _userId.value

    suspend fun load() {
        _userId.value = metadataDao.get()?.activeUserId
    }

    suspend fun set(userId: String) {
        metadataDao.setActiveUser(userId)
        _userId.value = userId
    }

    suspend fun clear() {
        metadataDao.setActiveUser(null)
        _userId.value = null
    }

    companion object {
        /**
         * Placeholder owner written into queued outbox payloads while no cloud user is active.
         * The sync engine stamps the authenticated user onto every payload at upload time
         * ([com.siteledger.app.sync.SyncEngine]), so this value never reaches the server.
         */
        const val PENDING_OWNER = "pending-owner"
    }
}
