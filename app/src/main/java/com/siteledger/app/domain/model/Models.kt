package com.siteledger.app.domain.model

import java.time.LocalDate

enum class ProjectStatus(val dbValue: String) {
    PLANNED("planned"),
    ACTIVE("active"),
    COMPLETED("completed"),
    ARCHIVED("archived");

    companion object {
        fun fromDb(value: String): ProjectStatus =
            entries.firstOrNull { it.dbValue == value } ?: ACTIVE
    }
}

enum class PaymentMethod(val dbValue: String) {
    CASH("cash"),
    MOBILE_MONEY("mobile_money"),
    BANK("bank"),
    OTHER("other");

    companion object {
        fun fromDb(value: String): PaymentMethod =
            entries.firstOrNull { it.dbValue == value } ?: OTHER
    }
}

/** Sync state of a business record as seen by the UI. */
enum class EntitySyncState(val dbValue: String) {
    /** Local change is queued and not yet acknowledged by the cloud. */
    PENDING("pending"),

    /** Record matches the last acknowledged cloud state. */
    SYNCED("synced"),

    /** Upload failed permanently and needs attention (see sync screen). */
    FAILED("failed");

    companion object {
        fun fromDb(value: String): EntitySyncState =
            entries.firstOrNull { it.dbValue == value } ?: SYNCED
    }
}

enum class ReceiptUploadState(val dbValue: String) {
    NONE("none"),
    PENDING("pending"),
    UPLOADED("uploaded"),
    FAILED("failed");

    companion object {
        fun fromDb(value: String): ReceiptUploadState =
            entries.firstOrNull { it.dbValue == value } ?: NONE
    }
}

data class Project(
    val id: String,
    val name: String,
    val clientName: String?,
    val location: String?,
    val budgetAmount: Long,
    val startDate: LocalDate?,
    val expectedEndDate: LocalDate?,
    val status: ProjectStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: EntitySyncState,
)

data class Expense(
    val id: String,
    val projectId: String,
    val categoryId: String,
    val supplierId: String?,
    val description: String,
    val quantityMilli: Long?,
    val unitPriceKes: Long?,
    val amountKes: Long,
    val paymentMethod: PaymentMethod,
    val expenseDate: LocalDate,
    val notes: String?,
    val receiptLocalPath: String?,
    val receiptRemotePath: String?,
    val receiptUploadState: ReceiptUploadState,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: EntitySyncState,
)

data class ExpenseCategory(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: EntitySyncState,
)

data class Supplier(
    val id: String,
    val name: String,
    val phone: String?,
    val location: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: EntitySyncState,
)

data class ExpenseWithContext(
    val expense: Expense,
    val projectName: String,
    val categoryName: String,
    val supplierName: String?,
)

data class ProjectTotals(
    val projectId: String,
    val totalKes: Long,
    val expenseCount: Int,
)
