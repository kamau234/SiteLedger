package com.siteledger.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_categories",
    indices = [Index("sync_state")],
)
data class ExpenseCategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    @ColumnInfo(name = "sync_state")
    val syncState: String,
    /** Last observed server revision (updated_at) in epoch millis; see ProjectEntity. */
    @ColumnInfo(name = "remote_updated_at")
    val remoteUpdatedAt: Long? = null,
)

@Entity(
    tableName = "suppliers",
    indices = [Index("sync_state")],
)
data class SupplierEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "phone")
    val phone: String?,
    @ColumnInfo(name = "location")
    val location: String?,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    @ColumnInfo(name = "sync_state")
    val syncState: String,
    /** Last observed server revision (updated_at) in epoch millis; see ProjectEntity. */
    @ColumnInfo(name = "remote_updated_at")
    val remoteUpdatedAt: Long? = null,
)
