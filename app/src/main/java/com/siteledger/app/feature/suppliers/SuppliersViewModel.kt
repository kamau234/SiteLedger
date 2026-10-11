package com.siteledger.app.feature.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.domain.model.Supplier
import com.siteledger.app.domain.repository.SupplierRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SuppliersUiState(
    val items: List<Supplier> = emptyList(),
    val query: String = "",
    val totalCount: Int = 0,
)

class SuppliersViewModel(supplierRepository: SupplierRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SuppliersUiState> = combine(
        supplierRepository.observeSuppliers(),
        query,
    ) { suppliers, searchQuery ->
        val filtered = if (searchQuery.isBlank()) {
            suppliers
        } else {
            suppliers.filter { supplier ->
                supplier.name.contains(searchQuery, ignoreCase = true) ||
                    supplier.phone?.contains(searchQuery, ignoreCase = true) == true ||
                    supplier.location?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        SuppliersUiState(items = filtered, query = searchQuery, totalCount = suppliers.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SuppliersUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }
}
