package com.siteledger.app.data.remote.dto

import com.siteledger.app.core.util.Dates
import com.siteledger.app.data.local.entity.ExpenseCategoryEntity
import com.siteledger.app.data.local.entity.ExpenseEntity
import com.siteledger.app.data.local.entity.ProjectEntity
import com.siteledger.app.data.local.entity.SupplierEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire DTOs. Field names mirror the PostgreSQL columns created in
 * supabase/migrations so repeated uploads can target rows by their locally
 * generated UUID (`id`) idempotently.
 */

@Serializable
data class ProjectDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("client_name") val clientName: String?,
    val location: String?,
    @SerialName("budget_amount") val budgetAmount: Long,
    @SerialName("start_date") val startDate: String?,
    @SerialName("expected_end_date") val expectedEndDate: String?,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class ExpenseCategoryDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class SupplierDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val phone: String?,
    val location: String?,
    val notes: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("supplier_id") val supplierId: String?,
    val description: String,
    @SerialName("quantity_milli") val quantityMilli: Long?,
    @SerialName("unit_price_kes") val unitPriceKes: Long?,
    @SerialName("amount_kes") val amountKes: Long,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("expense_date") val expenseDate: String,
    val notes: String?,
    @SerialName("receipt_path") val receiptPath: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class UpdatedAtDto(
    val id: String,
    @SerialName("updated_at") val updatedAt: String,
)

fun ProjectEntity.toDto(ownerId: String): ProjectDto = ProjectDto(
    id = id,
    ownerId = ownerId,
    name = name,
    clientName = clientName,
    location = location,
    budgetAmount = budgetAmount,
    startDate = startDate,
    expectedEndDate = expectedEndDate,
    status = status,
    createdAt = Dates.millisToIsoUtc(createdAt),
    updatedAt = Dates.millisToIsoUtc(updatedAt),
    deletedAt = deletedAt?.let { Dates.millisToIsoUtc(it) },
)

fun ExpenseCategoryEntity.toDto(ownerId: String): ExpenseCategoryDto = ExpenseCategoryDto(
    id = id,
    ownerId = ownerId,
    name = name,
    isDefault = isDefault,
    createdAt = Dates.millisToIsoUtc(createdAt),
    updatedAt = Dates.millisToIsoUtc(updatedAt),
    deletedAt = deletedAt?.let { Dates.millisToIsoUtc(it) },
)

fun SupplierEntity.toDto(ownerId: String): SupplierDto = SupplierDto(
    id = id,
    ownerId = ownerId,
    name = name,
    phone = phone,
    location = location,
    notes = notes,
    createdAt = Dates.millisToIsoUtc(createdAt),
    updatedAt = Dates.millisToIsoUtc(updatedAt),
    deletedAt = deletedAt?.let { Dates.millisToIsoUtc(it) },
)

fun ExpenseEntity.toDto(ownerId: String): ExpenseDto = ExpenseDto(
    id = id,
    ownerId = ownerId,
    projectId = projectId,
    categoryId = categoryId,
    supplierId = supplierId,
    description = description,
    quantityMilli = quantityMilli,
    unitPriceKes = unitPriceKes,
    amountKes = amountKes,
    paymentMethod = paymentMethod,
    expenseDate = expenseDate,
    notes = notes,
    receiptPath = receiptRemotePath,
    createdAt = Dates.millisToIsoUtc(createdAt),
    updatedAt = Dates.millisToIsoUtc(updatedAt),
    deletedAt = deletedAt?.let { Dates.millisToIsoUtc(it) },
)

// ---- pull-side mappings back into Room ----

fun ProjectDto.toEntity(syncState: String): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    clientName = clientName,
    location = location,
    budgetAmount = budgetAmount,
    startDate = startDate,
    expectedEndDate = expectedEndDate,
    status = status,
    createdAt = Dates.parseIsoInstantToMillis(createdAt) ?: System.currentTimeMillis(),
    updatedAt = Dates.parseIsoInstantToMillis(updatedAt) ?: System.currentTimeMillis(),
    deletedAt = Dates.parseIsoInstantToMillis(deletedAt),
    syncState = syncState,
    remoteUpdatedAt = Dates.parseIsoInstantToMillis(updatedAt),
)

fun ExpenseCategoryDto.toEntity(syncState: String): ExpenseCategoryEntity = ExpenseCategoryEntity(
    id = id,
    name = name,
    isDefault = isDefault,
    createdAt = Dates.parseIsoInstantToMillis(createdAt) ?: System.currentTimeMillis(),
    updatedAt = Dates.parseIsoInstantToMillis(updatedAt) ?: System.currentTimeMillis(),
    deletedAt = Dates.parseIsoInstantToMillis(deletedAt),
    syncState = syncState,
    remoteUpdatedAt = Dates.parseIsoInstantToMillis(updatedAt),
)

fun SupplierDto.toEntity(syncState: String): SupplierEntity = SupplierEntity(
    id = id,
    name = name,
    phone = phone,
    location = location,
    notes = notes,
    createdAt = Dates.parseIsoInstantToMillis(createdAt) ?: System.currentTimeMillis(),
    updatedAt = Dates.parseIsoInstantToMillis(updatedAt) ?: System.currentTimeMillis(),
    deletedAt = Dates.parseIsoInstantToMillis(deletedAt),
    syncState = syncState,
    remoteUpdatedAt = Dates.parseIsoInstantToMillis(updatedAt),
)

fun ExpenseDto.toEntity(current: ExpenseEntity?, syncState: String): ExpenseEntity = ExpenseEntity(
    id = id,
    projectId = projectId,
    categoryId = categoryId,
    supplierId = supplierId,
    description = description,
    quantityMilli = quantityMilli,
    unitPriceKes = unitPriceKes,
    amountKes = amountKes,
    paymentMethod = paymentMethod,
    expenseDate = expenseDate,
    notes = notes,
    receiptLocalPath = current?.receiptLocalPath,
    receiptRemotePath = receiptPath,
    receiptUploadState = if (receiptPath != null) {
        com.siteledger.app.domain.model.ReceiptUploadState.UPLOADED.dbValue
    } else {
        current?.receiptUploadState ?: com.siteledger.app.domain.model.ReceiptUploadState.NONE.dbValue
    },
    createdAt = Dates.parseIsoInstantToMillis(createdAt) ?: System.currentTimeMillis(),
    updatedAt = Dates.parseIsoInstantToMillis(updatedAt) ?: System.currentTimeMillis(),
    deletedAt = Dates.parseIsoInstantToMillis(deletedAt),
    syncState = syncState,
    remoteUpdatedAt = Dates.parseIsoInstantToMillis(updatedAt),
)
