package com.siteledger.app.data.local

import com.siteledger.app.core.util.Dates
import com.siteledger.app.data.local.dao.ExpenseWithNames
import com.siteledger.app.data.local.entity.ExpenseCategoryEntity
import com.siteledger.app.data.local.entity.ExpenseEntity
import com.siteledger.app.data.local.entity.ProjectEntity
import com.siteledger.app.data.local.entity.SupplierEntity
import com.siteledger.app.domain.model.EntitySyncState
import com.siteledger.app.domain.model.Expense
import com.siteledger.app.domain.model.ExpenseCategory
import com.siteledger.app.domain.model.ExpenseWithContext
import com.siteledger.app.domain.model.PaymentMethod
import com.siteledger.app.domain.model.Project
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.model.ReceiptUploadState
import com.siteledger.app.domain.model.Supplier
import java.time.LocalDate

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    clientName = clientName,
    location = location,
    budgetAmount = budgetAmount,
    startDate = Dates.parseIso(startDate),
    expectedEndDate = Dates.parseIso(expectedEndDate),
    status = ProjectStatus.fromDb(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = EntitySyncState.fromDb(syncState),
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    clientName = clientName,
    location = location,
    budgetAmount = budgetAmount,
    startDate = startDate?.let { Dates.toIso(it) },
    expectedEndDate = expectedEndDate?.let { Dates.toIso(it) },
    status = status.dbValue,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState.dbValue,
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    projectId = projectId,
    categoryId = categoryId,
    supplierId = supplierId,
    description = description,
    quantityMilli = quantityMilli,
    unitPriceKes = unitPriceKes,
    amountKes = amountKes,
    paymentMethod = PaymentMethod.fromDb(paymentMethod),
    expenseDate = Dates.parseIso(expenseDate) ?: LocalDate.MIN,
    notes = notes,
    receiptLocalPath = receiptLocalPath,
    receiptRemotePath = receiptRemotePath,
    receiptUploadState = ReceiptUploadState.fromDb(receiptUploadState),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = EntitySyncState.fromDb(syncState),
)

fun ExpenseWithNames.toDomain(): ExpenseWithContext = ExpenseWithContext(
    expense = expense.toDomain(),
    projectName = projectName,
    categoryName = categoryName,
    supplierName = supplierName,
)

fun ExpenseCategoryEntity.toDomain(): ExpenseCategory = ExpenseCategory(
    id = id,
    name = name,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = EntitySyncState.fromDb(syncState),
)

fun SupplierEntity.toDomain(): Supplier = Supplier(
    id = id,
    name = name,
    phone = phone,
    location = location,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = EntitySyncState.fromDb(syncState),
)
