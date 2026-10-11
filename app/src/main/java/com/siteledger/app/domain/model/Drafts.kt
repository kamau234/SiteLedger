package com.siteledger.app.domain.model

import java.time.LocalDate

data class ProjectDraft(
    val id: String?,
    val name: String,
    val clientName: String?,
    val location: String?,
    val budgetAmount: Long,
    val startDate: LocalDate?,
    val expectedEndDate: LocalDate?,
    val status: ProjectStatus,
)

data class ExpenseDraft(
    val id: String?,
    val projectId: String?,
    val categoryId: String?,
    val supplierId: String?,
    val description: String,
    val quantityMilli: Long?,
    val unitPriceKes: Long?,
    val amountKes: Long,
    val paymentMethod: PaymentMethod,
    val expenseDate: LocalDate,
    val notes: String?,
    /** Path of the receipt already stored in app-private storage, if any. */
    val receiptLocalPath: String?,
    val receiptReplaced: Boolean,
)

data class SupplierDraft(
    val id: String?,
    val name: String,
    val phone: String?,
    val location: String?,
    val notes: String?,
)

data class ExpenseFilter(
    val projectId: String? = null,
    val categoryId: String? = null,
    val paymentMethod: PaymentMethod? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)
