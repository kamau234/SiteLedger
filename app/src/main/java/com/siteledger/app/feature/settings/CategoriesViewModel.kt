package com.siteledger.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.domain.model.ExpenseCategory
import com.siteledger.app.domain.repository.CategoryRepository
import com.siteledger.app.domain.repository.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<ExpenseCategory> = emptyList(),
    val message: String? = null,
)

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryRepository.observeCategories(),
        message,
    ) { categories, currentMessage ->
        CategoriesUiState(categories = categories, message = currentMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun consumeMessage() {
        message.value = null
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            message.value = "Enter a category name"
            return
        }
        viewModelScope.launch {
            when (val result = categoryRepository.addCategory(trimmed)) {
                is SaveResult.Success -> message.value = "Category added"
                is SaveResult.ValidationFailed -> message.value = result.errors.joinToString("; ") { it.message }
                is SaveResult.Failure -> message.value = result.message
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            when (val result = categoryRepository.deleteCategory(id)) {
                is SaveResult.Success -> message.value = "Category removed"
                is SaveResult.ValidationFailed -> message.value = result.errors.joinToString("; ") { it.message }
                is SaveResult.Failure -> message.value = result.message
            }
        }
    }
}
