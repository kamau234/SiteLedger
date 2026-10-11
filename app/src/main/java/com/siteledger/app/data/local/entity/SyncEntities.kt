package com.siteledger.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Entity types tracked by the durable sync outbox. Order matters for uploads. */
object OutboxEntityType {
    const val EXPENSE_CATEGORY = "expense_category"
    const val SUPPLIER = "supplier"
    const val PROJECT = "project"
    const val EXPENSE = "expense"
    const val RECEIPT = "receipt"
}

object OutboxOperation {
    const val UPSERT = "upsert"
    const val DELETE = "delete"
    const val UPLOAD_RECEIPT = "upload_receipt"
}

object OutboxStatus {
    const val PENDING = "pending"
    const val IN_FLIGHT = "in_flight"
    const val FAILED = "failed"
}

/**
 * Local durable queue for unsynchronized changes. One entry is written in the same Room
 * transaction as the business change it describes.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index("entity_type"), Index("entity_id"), Index("status")],
)
data class SyncOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "operation")
    val operation: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "status")
    val status: String = OutboxStatus.PENDING,
    /**
     * Set by the user on the sync screen ("keep local version") to bypass the server
     * revision pre-flight check for this change.
     */
    @ColumnInfo(name = "force_overwrite")
    val forceOverwrite: Boolean = false,
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,
    @ColumnInfo(name = "active_user_id")
    val activeUserId: String? = null,
    @ColumnInfo(name = "last_successful_sync")
    val lastSuccessfulSync: Long? = null,
    @ColumnInfo(name = "initial_sync_complete")
    val initialSyncComplete: Boolean = false,
)
