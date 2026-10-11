package com.siteledger.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.domain.repository.AuthRepository
import com.siteledger.app.domain.repository.SessionInfo
import com.siteledger.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val companyName: String = "",
    val nameEdited: Boolean = false,
    val thresholdPercent: Int = 80,
    val session: SessionInfo? = null,
    val signingOut: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** While non-null, an in-progress edit shadows the stored company name. */
    private val nameEdit = MutableStateFlow<String?>(null)
    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.companyName,
            settingsRepository.warningThresholdPercent,
            authRepository.session,
            nameEdit,
        ) { storedName, threshold, session, edit ->
            SettingsUiState(
                companyName = edit ?: storedName,
                nameEdited = edit != null && edit != storedName,
                thresholdPercent = threshold,
                session = session,
            )
        }.combine(local) { base, l ->
            base.copy(signingOut = l.signingOut, message = l.message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onCompanyNameChange(value: String) = nameEdit.update { value }

    fun saveCompanyName() {
        val value = uiState.value.companyName.trim()
        if (value.isEmpty()) {
            local.update { it.copy(message = "Enter a business or company name") }
            return
        }
        viewModelScope.launch {
            settingsRepository.setCompanyName(value)
            nameEdit.value = null
            local.update { it.copy(message = "Company name saved") }
        }
    }

    fun saveThreshold(percent: Int) {
        viewModelScope.launch {
            settingsRepository.setWarningThresholdPercent(percent)
        }
    }

    fun signOut(keepLocalData: Boolean) {
        if (local.value.signingOut) return
        local.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            authRepository.signOut(keepLocalData)
            local.update { it.copy(signingOut = false) }
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }
}
