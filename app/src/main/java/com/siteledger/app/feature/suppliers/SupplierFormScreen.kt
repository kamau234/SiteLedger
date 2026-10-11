package com.siteledger.app.feature.suppliers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.designsystem.ConfirmDialog
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor

@Composable
fun SupplierFormScreen(
    container: AppContainer,
    supplierId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: SupplierFormViewModel = viewModel(
        factory = viewModelFactoryFor { SupplierFormViewModel(supplierId, container.supplierRepository) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) {
        if (state.done) onSaved()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    ScreenScaffold(
        title = if (state.isEdit) "Edit supplier" else "New supplier",
        onBack = onBack,
        actions = {
            if (state.isEdit) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete supplier")
                }
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Supplier name *",
                modifier = Modifier.fillMaxWidth(),
                isError = state.errors.containsKey("name"),
                supportingText = state.errors["name"],
                enabled = !state.loading,
            )
            FormTextField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = "Phone",
                modifier = Modifier.fillMaxWidth(),
                isError = state.errors.containsKey("phone"),
                supportingText = state.errors["phone"],
                keyboardType = KeyboardType.Phone,
                enabled = !state.loading,
            )
            FormTextField(
                value = state.location,
                onValueChange = viewModel::onLocationChange,
                label = "Location",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            )
            FormTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = "Notes",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                imeAction = ImeAction.Default,
                enabled = !state.loading,
            )
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving && !state.loading,
            ) {
                Text(if (state.saving) "Saving…" else "Save supplier")
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete this supplier?",
            message = "Expenses already recorded with this supplier keep their history; the supplier is removed from pickers after the next sync.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete(onDeleted)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
