package com.siteledger.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor
import com.siteledger.app.domain.Validation

@Composable
fun LoginScreen(container: AppContainer) {
    val viewModel: LoginViewModel = viewModel(
        factory = viewModelFactoryFor { LoginViewModel(container.authRepository) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "SiteLedger",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Simple, offline-first expense tracking for your construction sites.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (!state.cloudConfigured) {
            LocalOnlyIntro(state, viewModel)
        } else {
            CloudSignIn(state, viewModel)
        }
    }
}

@Composable
private fun LocalOnlyIntro(state: LoginUiState, viewModel: LoginViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Works without internet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "• Record site expenses in Kenyan Shillings\n" +
                    "• Track project budgets as work continues\n" +
                    "• Export PDF and Excel reports any time\n\n" +
                    "This build has no cloud account configured, so your records stay on this phone only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    state.error?.let { ErrorText(it) }
    Button(
        onClick = viewModel::continueLocalOnly,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.submitting,
    ) {
        if (state.submitting) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Start using SiteLedger")
        }
    }
}

@Composable
private fun CloudSignIn(state: LoginUiState, viewModel: LoginViewModel) {
    FormTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = "Email",
        modifier = Modifier.fillMaxWidth(),
        keyboardType = KeyboardType.Email,
        enabled = !state.submitting,
    )
    AuthPasswordField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = "Password",
        imeAction = if (state.isSignUp) ImeAction.Next else ImeAction.Done,
        enabled = !state.submitting,
    )
    if (state.isSignUp) {
        PasswordStrengthMeter(Validation.passwordStrength(state.password))
        AuthPasswordField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = "Confirm password",
            imeAction = ImeAction.Done,
            enabled = !state.submitting,
        )
        Text(
            "At least 6 characters. Your records stay private to this account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.error?.let { ErrorText(it) }
    state.info?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
    }
    Button(
        onClick = if (state.isSignUp) viewModel::signUp else viewModel::signIn,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.submitting,
    ) {
        if (state.submitting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                Text(if (state.isSignUp) "Creating account…" else "Signing in…")
            }
        } else {
            Text(if (state.isSignUp) "Create account" else "Sign in")
        }
    }
    if (state.isSignUp) {
        TextButton(
            onClick = viewModel::toggleMode,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.submitting,
        ) {
            Text("Already have an account? Sign in")
        }
    } else {
        TextButton(
            onClick = viewModel::toggleMode,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.submitting,
        ) {
            Text("New here? Create an account")
        }
        TextButton(
            onClick = viewModel::sendPasswordReset,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.submitting,
        ) {
            Text("Forgot password?")
        }
    }
}

@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    enabled: Boolean,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        keyboardType = if (visible) KeyboardType.Text else KeyboardType.Password,
        imeAction = imeAction,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }, enabled = enabled) {
                Text(if (visible) "Hide" else "Show")
            }
        },
    )
}

@Composable
private fun PasswordStrengthMeter(strength: Validation.PasswordStrength) {
    if (strength == Validation.PasswordStrength.EMPTY) return
    val color = when (strength) {
        Validation.PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        Validation.PasswordStrength.FAIR -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LinearProgressIndicator(
            progress = { strength.score / 4f },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            "Password strength: ${strength.label}",
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}
