package com.siteledger.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.BuildConfig
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.core.designsystem.LabeledValue
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.core.designsystem.SectionHeader
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor
import com.siteledger.app.domain.repository.SessionPhase

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenCategories: () -> Unit,
    onOpenSuppliers: () -> Unit,
    onOpenSync: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactoryFor {
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                authRepository = container.authRepository,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    var showSignOutDialog by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Settings", snackbarHostState = snackbarHostState) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionHeader(title = "Business")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FormTextField(
                        value = state.companyName,
                        onValueChange = { viewModel.onCompanyNameChange(it) },
                        label = "Company / business name",
                        supportingText = "Shown on exported PDF and Excel reports",
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.saveCompanyName() },
                        enabled = state.nameEdited,
                    ) {
                        Text("Save name")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(title = "Budget warnings")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Alert when a project reaches ${state.thresholdPercent}% of budget",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    ThresholdSlider(
                        thresholdPercent = state.thresholdPercent,
                        onCommit = { viewModel.saveThreshold(it) },
                    )
                    Text(
                        text = "Projects at or above this level appear in the dashboard " +
                            "watch list and are highlighted in reports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(title = "Account")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val session = state.session
                    LabeledValue(
                        label = "Status",
                        value = when (session?.phase) {
                            SessionPhase.SIGNED_IN -> "Signed in"
                            SessionPhase.LOCAL_ONLY -> "Local only (no cloud account)"
                            SessionPhase.SESSION_EXPIRED -> "Session expired"
                            SessionPhase.SIGNED_OUT -> "Signed out"
                            SessionPhase.INITIALIZING -> "Restoring session…"
                            null -> "—"
                        },
                    )
                    if (!session?.email.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(label = "Account", value = session?.email ?: "—")
                    }
                    Spacer(Modifier.height(12.dp))
                    if (session?.phase == SessionPhase.SESSION_EXPIRED ||
                        session?.phase == SessionPhase.SIGNED_OUT
                    ) {
                        Button(
                            onClick = { viewModel.signOut(keepLocalData = true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Sign in again")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (session?.phase == SessionPhase.SIGNED_IN ||
                        session?.phase == SessionPhase.SESSION_EXPIRED ||
                        session?.phase == SessionPhase.LOCAL_ONLY
                    ) {
                        OutlinedButton(
                            onClick = { showSignOutDialog = true },
                            enabled = !state.signingOut,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.signingOut) "Signing out…" else "Sign out")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(title = "Data")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow("Expense categories", onOpenCategories)
                    SettingsRow("Suppliers", onOpenSuppliers)
                    SettingsRow("Sync status", onOpenSync)
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(title = "About")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LabeledValue(label = "SiteLedger", value = "Version ${BuildConfig.VERSION_NAME}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Your records are stored on this phone first and work without " +
                            "internet. When cloud sync is configured, changes upload " +
                            "automatically and never overwrite newer figures without your " +
                            "confirmation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showSignOutDialog) {
        var keepLocal by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = {
                Column {
                    Text(
                        text = "Choose what happens to the records saved on this phone. " +
                            "Anything still waiting to upload stays here unless you choose " +
                            "to remove it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = keepLocal,
                            onCheckedChange = { keepLocal = it },
                        )
                        Text(
                            text = "Keep local records on this phone",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut(keepLocalData = keepLocal)
                    },
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ThresholdSlider(
    thresholdPercent: Int,
    onCommit: (Int) -> Unit,
) {
    var sliderValue by remember(thresholdPercent) { mutableFloatStateOf(thresholdPercent.toFloat()) }
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onCommit(sliderValue.toInt()) },
        valueRange = 50f..100f,
        steps = 9,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
