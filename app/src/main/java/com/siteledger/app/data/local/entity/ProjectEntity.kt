package com.siteledger.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [Index("status"), Index("sync_state")],
)
data class ProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "client_name")
    val clientName: String?,
    @ColumnInfo(name = "location")
    val location: String?,
    @ColumnInfo(name = "budget_amount")
    val budgetAmount: Long,
    @ColumnInfo(name = "start_date")
    val startDate: String?,
    @ColumnInfo(name = "expected_end_date")
    val expectedEndDate: String?,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    @ColumnInfo(name = "sync_state")
    val syncState: String,
    /**
     * Last server revision (updated_at) we observed for this row, in epoch millis.
     * Conflict checks compare server timestamps only, never the device clock.
     */
    @ColumnInfo(name = "remote_updated_at")
    val remoteUpdatedAt: Long? = null,
)
