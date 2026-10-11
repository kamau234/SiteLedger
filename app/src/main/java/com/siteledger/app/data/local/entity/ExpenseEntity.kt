package com.siteledger.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
        ),
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
        ),
    ],
    indices = [
        Index("project_id"),
        Index("category_id"),
        Index("supplier_id"),
        Index("expense_date"),
        Index("sync_state"),
    ],
)
data class ExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "project_id")
    val projectId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "supplier_id")
    val supplierId: String?,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "quantity_milli")
    val quantityMilli: Long?,
    @ColumnInfo(name = "unit_price_kes")
    val unitPriceKes: Long?,
    @ColumnInfo(name = "amount_kes")
    val amountKes: Long,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String,
    @ColumnInfo(name = "expense_date")
    val expenseDate: String,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "receipt_local_path")
    val receiptLocalPath: String?,
    @ColumnInfo(name = "receipt_remote_path")
    val receiptRemotePath: String?,
    @ColumnInfo(name = "receipt_upload_state")
    val receiptUploadState: String,
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
