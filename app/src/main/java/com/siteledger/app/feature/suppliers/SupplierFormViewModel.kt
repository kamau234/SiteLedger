package com.siteledger.app.feature.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.domain.Validation
import com.siteledger.app.domain.model.SupplierDraft
import com.siteledger.app.domain.repository.SaveResult
import com.siteledger.app.domain.repository.SupplierRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SupplierFormUiState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val location: String = "",
    val notes: String = "",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val done: Boolean = false,
    val message: String? = null,
)

class SupplierFormViewModel(
    private val supplierId: String?,
    private val supplierRepository: SupplierRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SupplierFormUiState(isEdit = supplierId != null, loading = supplierId != null),
    )
    val uiState: StateFlow<SupplierFormUiState> = _uiState

    init {
        if (supplierId != null) {
            viewModelScope.launch {
                val supplier = supplierRepository.getSupplier(supplierId)
                if (supplier == null) {
                    _uiState.update { it.copy(loading = false, message = "That supplier no longer exists") }
                } else {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            name = supplier.name,
                            phone = supplier.phone.orEmpty(),
                            location = supplier.location.orEmpty(),
                            notes = supplier.notes.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, errors = it.errors - Validation.SupplierField.NAME) }

    fun onPhoneChange(value: String) =
        _uiState.update { it.copy(phone = value, errors = it.errors - Validation.SupplierField.PHONE) }

    fun onLocationChange(value: String) = _uiState.update { it.copy(location = value) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun save() {
        val state = _uiState.value
        if (state.saving) return

        val draft = SupplierDraft(
            id = supplierId,
            name = state.name.trim(),
            phone = state.phone.trim().ifBlank { null },
            location = state.location.trim().ifBlank { null },
            notes = state.notes.trim().ifBlank { null },
        )
        val validationErrors = Validation.validateSupplier(draft)
        if (validationErrors.isNotEmpty()) {
            _uiState.update { it.copy(errors = validationErrors.associate { error -> error.field to error.message }) }
            return
        }

        _uiState.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            when (val result = supplierRepository.saveSupplier(draft)) {
                is SaveResult.Success -> _uiState.update { it.copy(saving = false, done = true) }
                is SaveResult.ValidationFailed -> _uiState.update {
                    it.copy(saving = false, errors = result.errors.associate { error -> error.field to error.message })
                }
                is SaveResult.Failure -> _uiState.update { it.copy(saving = false, message = result.message) }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = supplierId ?: return
        viewModelScope.launch {
            when (val result = supplierRepository.deleteSupplier(id)) {
                is SaveResult.Success -> onDeleted()
                is SaveResult.ValidationFailed -> _uiState.update {
                    it.copy(message = result.errors.joinToString("; ") { error -> error.message })
                }
                is SaveResult.Failure -> _uiState.update { it.copy(message = result.message) }
            }
        }
    }
}
