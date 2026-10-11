package com.siteledger.app.domain

import com.siteledger.app.domain.model.ExpenseDraft
import com.siteledger.app.domain.model.ProjectDraft
import com.siteledger.app.domain.model.SupplierDraft

data class FieldError(val field: String, val message: String)

/**
 * Domain validation rules. Shared by form ViewModels (live feedback) and repositories
 * (guaranteed before persistence). Kept free of Android dependencies so it is unit-testable.
 */
object Validation {

    object ProjectField {
        const val NAME = "name"
        const val BUDGET = "budget"
        const val DATES = "dates"
    }

    object ExpenseField {
        const val PROJECT = "project"
        const val CATEGORY = "category"
        const val DESCRIPTION = "description"
        const val AMOUNT = "amount"
        const val QUANTITY = "quantity"
        const val UNIT_PRICE = "unitPrice"
    }

    object SupplierField {
        const val NAME = "name"
        const val PHONE = "phone"
    }

    enum class PasswordStrength(val score: Int, val label: String) {
        EMPTY(0, ""),
        WEAK(1, "Weak"),
        FAIR(2, "Fair"),
        GOOD(3, "Good"),
        STRONG(4, "Strong"),
    }

    /**
     * Heuristic strength score for live sign-up feedback: length bands plus character
     * variety. Six characters stay the hard minimum; the meter nudges users beyond it.
     */
    fun passwordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.EMPTY
        var score = 0
        if (password.length >= 6) score++
        if (password.length >= 10) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { it.isUpperCase() && it.isLetter() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return when {
            score >= 4 -> PasswordStrength.STRONG
            score == 3 -> PasswordStrength.GOOD
            score == 2 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }

    fun validateProject(draft: ProjectDraft): List<FieldError> = buildList {
        if (draft.name.isBlank()) {
            add(FieldError(ProjectField.NAME, "Project name is required"))
        }
        if (draft.budgetAmount < 0) {
            add(FieldError(ProjectField.BUDGET, "Budget cannot be negative"))
        }
        val start = draft.startDate
        val end = draft.expectedEndDate
        if (start != null && end != null && end.isBefore(start)) {
            add(FieldError(ProjectField.DATES, "Expected end date cannot be before the start date"))
        }
    }

    fun validateExpense(draft: ExpenseDraft): List<FieldError> = buildList {
        if (draft.projectId.isNullOrBlank()) {
            add(FieldError(ExpenseField.PROJECT, "Select a project"))
        }
        if (draft.categoryId.isNullOrBlank()) {
            add(FieldError(ExpenseField.CATEGORY, "Select a category"))
        }
        if (draft.description.isBlank()) {
            add(FieldError(ExpenseField.DESCRIPTION, "Description is required"))
        }
        if (draft.amountKes < 0) {
            add(FieldError(ExpenseField.AMOUNT, "Amount cannot be negative"))
        }
        val quantity = draft.quantityMilli
        if (quantity != null && quantity <= 0) {
            add(FieldError(ExpenseField.QUANTITY, "Quantity must be greater than zero"))
        }
        val unitPrice = draft.unitPriceKes
        if (unitPrice != null && unitPrice < 0) {
            add(FieldError(ExpenseField.UNIT_PRICE, "Unit price cannot be negative"))
        }
    }

    fun validateSupplier(draft: SupplierDraft): List<FieldError> = buildList {
        if (draft.name.isBlank()) {
            add(FieldError(SupplierField.NAME, "Supplier name is required"))
        }
        if ((draft.phone?.length ?: 0) > 32) {
            add(FieldError(SupplierField.PHONE, "Phone number is too long"))
        }
    }
}
