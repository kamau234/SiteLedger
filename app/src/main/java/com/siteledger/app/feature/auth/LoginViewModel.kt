package com.siteledger.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siteledger.app.domain.repository.AuthRepository
import com.siteledger.app.domain.repository.SessionPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSignUp: Boolean = false,
    val cloudConfigured: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(cloudConfigured = authRepository.session.value.cloudConfigured),
    )
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, error = null) }

    fun toggleMode() = _uiState.update {
        it.copy(
            isSignUp = !it.isSignUp,
            confirmPassword = "",
            error = null,
            info = null,
        )
    }

    fun signIn() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email and password") }
            return
        }
        if (state.submitting) return
        _uiState.update { it.copy(submitting = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.signIn(state.email, state.password).fold(
                onSuccess = {
                    // Navigation follows the session flow; nothing more to do here.
                    _uiState.update { it.copy(submitting = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            error = error.message ?: "Sign-in failed. Check your connection and try again.",
                        )
                    }
                },
            )
        }
    }

    fun signUp() {
        val state = _uiState.value
        val validationError = validateSignUp(state)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        if (state.submitting) return
        _uiState.update { it.copy(submitting = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.signUp(state.email, state.password).fold(
                onSuccess = {
                    val signedIn = authRepository.session.value.phase == SessionPhase.SIGNED_IN
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            confirmPassword = "",
                            info = if (signedIn) null else
                                "Account created. Check your email for the confirmation link, then sign in.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            error = when {
                                error.message?.contains("already registered", ignoreCase = true) == true ->
                                    "An account with this email already exists. Sign in instead."
                                else ->
                                    error.message
                                        ?: "Could not create the account. Check your connection and try again."
                            },
                        )
                    }
                },
            )
        }
    }

    private fun validateSignUp(state: LoginUiState): String? = when {
        state.email.isBlank() || !state.email.contains("@") -> "Enter a valid email address"
        state.password.length < 6 -> "Password must be at least 6 characters"
        state.password != state.confirmPassword -> "Passwords do not match"
        else -> null
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email first", info = null) }
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(info = "Password reset email sent to $email", error = null)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Could not send the reset email", info = null)
                    }
                },
            )
        }
    }

    fun continueLocalOnly() {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            authRepository.continueLocalOnly()
        }
    }
}
