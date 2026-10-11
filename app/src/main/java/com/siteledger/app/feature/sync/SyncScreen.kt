package com.siteledger.app.feature.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.designsystem.LabeledValue
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.core.designsystem.SectionHeader
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor
import com.siteledger.app.domain.repository.SessionPhase
import com.siteledger.app.sync.SyncPhase

@Composable
fun SyncScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    val viewModel: SyncViewModel = viewModel(
        factory = viewModelFactoryFor {
            SyncViewModel(
                syncEngine = container.syncEngine,
                outboxDao = container.database.outboxDao(),
                metadataDao = container.database.appMetadataDao(),
                connectivity = container.connectivity,
                authRepository = container.authRepository,
                syncTrigger = container.syncScheduler,
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

    val session = state.session
    val signedIn = session?.phase == SessionPhase.SIGNED_IN || session?.phase == SessionPhase.LOCAL_ONLY
    val canSync = state.online && signedIn && state.phase == SyncPhase.IDLE && !state.running

    ScreenScaffold(
        title = "Sync",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LabeledValue(
                        label = "Connection",
                        value = if (state.online) "Online" else "Offline",
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledValue(
                        label = "Account",
                        value = session?.accountLabel() ?: "—",
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledValue(
                        label = "Last successful sync",
                        value = state.lastSuccessfulSyncLabel,
                    )
                    if (state.phase != SyncPhase.IDLE || state.running) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = when {
                                    state.running -> "Syncing…"
                                    state.phase == SyncPhase.UPLOADING -> "Uploading changes…"
                                    else -> "Downloading updates…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = canSync,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sync now")
                    }
                    if (!state.online) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "You are offline. Changes are saved on this phone and will " +
                                "sync automatically when a connection returns.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Waiting to upload", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.pendingCount == 0) {
                            "Everything on this phone has been sent to the cloud."
                        } else {
                            "${state.pendingCount} change${if (state.pendingCount == 1) "" else "s"} " +
                                "saved on this phone and waiting to upload. They are kept safely " +
                                "until the upload succeeds."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.failed.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionHeader(title = "Needs attention (${state.failed.size})")
                state.failed.forEach { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "${entry.label} · ${entry.detail}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (!entry.lastError.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = entry.lastError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row {
                                OutlinedButton(onClick = { viewModel.retry(entry.id) }) {
                                    Text("Retry")
                                }
                                Spacer(Modifier.width(12.dp))
                                TextButton(onClick = { viewModel.retryKeepingLocal(entry.id) }) {
                                    Text("Keep local version")
                                }
                            }
                            Text(
                                text = "Retry uploads the cloud copy again. Keep local version " +
                                    "deliberately overwrites the cloud copy with this phone's " +
                                    "version — use it when this phone has the correct figures.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.failed.size > 1) {
                    OutlinedButton(
                        onClick = { viewModel.retryAll() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry all")
                    }
                }
            }

            if (session != null &&
                (session.phase == SessionPhase.SESSION_EXPIRED || session.phase == SessionPhase.SIGNED_OUT)
            ) {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (session.phase == SessionPhase.SESSION_EXPIRED) {
                                "Your cloud session has expired. Local recording still works; " +
                                    "sign in again to resume syncing."
                            } else {
                                "Sign in to upload the changes saved on this phone."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onSignIn) {
                            Text("Sign in again")
                        }
                    }
                }
            }
        }
    }
}
