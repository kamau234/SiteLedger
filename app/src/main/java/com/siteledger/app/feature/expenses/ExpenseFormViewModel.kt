package com.siteledger.app.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.core.common.Money
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.domain.Validation
import com.siteledger.app.domain.model.ExpenseDraft
import com.siteledger.app.domain.model.PaymentMethod
import com.siteledger.app.domain.repository.CategoryRepository
import com.siteledger.app.domain.repository.ExpenseRepository
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SaveResult
import com.siteledger.app.domain.repository.SupplierRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpenseFormUiState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val projectId: String? = null,
    val categoryId: String? = null,
    val supplierId: String? = null,
    val description: String = "",
    val quantityText: String = "",
    val unitPriceText: String = "",
    val amountText: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val date: LocalDate,
    val notes: String = "",
    val receiptLocalPath: String? = null,
    val receiptReplaced: Boolean = false,
    val projects: List<Pair<String, String>> = emptyList(),
    val categories: List<Pair<String, String>> = emptyList(),
    val suppliers: List<Pair<String, String>> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val done: Boolean = false,
    val message: String? = null,
)

class ExpenseFormViewModel(
    private val expenseId: String?,
    initialProjectId: String?,
    private val expenseRepository: ExpenseRepository,
    projectRepository: ProjectRepository,
    categoryRepository: CategoryRepository,
    supplierRepository: SupplierRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExpenseFormUiState(
            isEdit = expenseId != null,
            loading = expenseId != null,
            projectId = initialProjectId,
            date = clock.today(),
        ),
    )
    val uiState: StateFlow<ExpenseFormUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                projectRepository.observeProjects(),
                categoryRepository.observeCategories(),
                supplierRepository.observeSuppliers(),
            ) { projects, categories, suppliers ->
                Triple(
                    projects.map { it.id to it.name },
                    categories.map { it.id to it.name },
                    suppliers.map { it.id to it.name },
                )
            }.collect { (projects, categories, suppliers) ->
                _uiState.update {
                    it.copy(projects = projects, categories = categories, suppliers = suppliers)
                }
            }
        }
        if (expenseId != null) {
            viewModelScope.launch {
                val expense = expenseRepository.getExpense(expenseId)
                if (expense == null) {
                    _uiState.update { it.copy(loading = false, message = "That expense no longer exists") }
                } else {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            projectId = expense.projectId,
                            categoryId = expense.categoryId,
                            supplierId = expense.supplierId,
                            description = expense.description,
                            quantityText = expense.quantityMilli?.let(Money::formatQuantity).orEmpty(),
                            unitPriceText = expense.unitPriceKes?.toString().orEmpty(),
                            amountText = expense.amountKes.toString(),
                            paymentMethod = expense.paymentMethod,
                            date = expense.expenseDate,
                            notes = expense.notes.orEmpty(),
                            receiptLocalPath = expense.receiptLocalPath,
                            receiptReplaced = false,
                        )
                    }
                }
            }
        }
    }

    fun onProjectSelected(value: String) =
        _uiState.update { it.copy(projectId = value, errors = it.errors - Validation.ExpenseField.PROJECT) }

    fun onCategorySelected(value: String) =
        _uiState.update { it.copy(categoryId = value, errors = it.errors - Validation.ExpenseField.CATEGORY) }

    fun onSupplierSelected(value: String) =
        _uiState.update { it.copy(supplierId = value.ifBlank { null }) }

    fun onDescriptionChange(value: String) =
        _uiState.update { it.copy(description = value, errors = it.errors - Validation.ExpenseField.DESCRIPTION) }

    fun onQuantityChange(value: String) {
        _uiState.update { it.copy(quantityText = value, errors = it.errors - Validation.ExpenseField.QUANTITY) }
        recomputeAmount()
    }

    fun onUnitPriceChange(value: String) {
        _uiState.update { it.copy(unitPriceText = value, errors = it.errors - Validation.ExpenseField.UNIT_PRICE) }
        recomputeAmount()
    }

    fun onAmountChange(value: String) =
        _uiState.update { it.copy(amountText = value, errors = it.errors - Validation.ExpenseField.AMOUNT) }

    fun onPaymentMethodChange(value: PaymentMethod) = _uiState.update { it.copy(paymentMethod = value) }

    fun onDateChange(value: LocalDate) = _uiState.update { it.copy(date = value) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onReceiptPicked(path: String) =
        _uiState.update { it.copy(receiptLocalPath = path, receiptReplaced = true) }

    fun onReceiptRemoved() =
        _uiState.update { it.copy(receiptLocalPath = null, receiptReplaced = true) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun recomputeAmount() {
        val state = _uiState.value
        val quantity = Money.parseQuantityMilli(state.quantityText)
        val unitPrice = Money.parseKes(state.unitPriceText)
        if (quantity != null && unitPrice != null) {
            _uiState.update { it.copy(amountText = Money.amountFromQuantity(quantity, unitPrice).toString()) }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving) return

        val errors = mutableMapOf<String, String>()
        val amount = if (state.amountText.isBlank()) {
            errors[Validation.ExpenseField.AMOUNT] = "Enter the amount in whole shillings"
            0L
        } else {
            Money.parseKes(state.amountText) ?: run {
                errors[Validation.ExpenseField.AMOUNT] = "Enter a valid amount in whole shillings"
                0L
            }
        }
        val quantity = if (state.quantityText.isBlank()) {
            null
        } else {
            Money.parseQuantityMilli(state.quantityText) ?: run {
                errors[Validation.ExpenseField.QUANTITY] = "Enter a valid quantity, e.g. 3.5"
                null
            }
        }
        val unitPrice = if (state.unitPriceText.isBlank()) {
            null
        } else {
            Money.parseKes(state.unitPriceText) ?: run {
                errors[Validation.ExpenseField.UNIT_PRICE] = "Enter a valid unit price in whole shillings"
                null
            }
        }

        val draft = ExpenseDraft(
            id = expenseId,
            projectId = state.projectId,
            categoryId = state.categoryId,
            supplierId = state.supplierId,
            description = state.description.trim(),
            quantityMilli = quantity,
            unitPriceKes = unitPrice,
            amountKes = amount,
            paymentMethod = state.paymentMethod,
            expenseDate = state.date,
            notes = state.notes.trim().ifBlank { null },
            receiptLocalPath = state.receiptLocalPath,
            receiptReplaced = state.receiptReplaced,
        )

        Validation.validateExpense(draft).forEach { error -> errors.putIfAbsent(error.field, error.message) }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        _uiState.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            when (val result = expenseRepository.saveExpense(draft)) {
                is SaveResult.Success -> _uiState.update { it.copy(saving = false, done = true) }
                is SaveResult.ValidationFailed -> _uiState.update {
                    it.copy(saving = false, errors = result.errors.associate { error -> error.field to error.message })
                }
                is SaveResult.Failure -> _uiState.update { it.copy(saving = false, message = result.message) }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = expenseId ?: return
        viewModelScope.launch {
            when (val result = expenseRepository.deleteExpense(id)) {
                is SaveResult.Success -> onDeleted()
                is SaveResult.ValidationFailed -> _uiState.update {
                    it.copy(message = result.errors.joinToString("; ") { error -> error.message })
                }
                is SaveResult.Failure -> _uiState.update { it.copy(message = result.message) }
            }
        }
    }
}
