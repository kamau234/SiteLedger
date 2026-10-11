package com.siteledger.app.feature.expenses

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.common.label
import com.siteledger.app.core.designsystem.ConfirmDialog
import com.siteledger.app.core.designsystem.DateField
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.core.designsystem.PickerField
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.core.designsystem.SectionHeader
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor
import com.siteledger.app.domain.model.PaymentMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExpenseFormScreen(
    container: AppContainer,
    expenseId: String?,
    initialProjectId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: ExpenseFormViewModel = viewModel(
        factory = viewModelFactoryFor {
            ExpenseFormViewModel(
                expenseId = expenseId,
                initialProjectId = initialProjectId,
                expenseRepository = container.expenseRepository,
                projectRepository = container.projectRepository,
                categoryRepository = container.categoryRepository,
                supplierRepository = container.supplierRepository,
                clock = container.clock,
            )
        },
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
        title = if (state.isEdit) "Edit expense" else "New expense",
        onBack = onBack,
        actions = {
            if (state.isEdit) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
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
            PickerField(
                label = "Project *",
                selectedText = state.projectId?.let { id -> state.projects.firstOrNull { it.first == id }?.second },
                options = state.projects,
                onSelect = viewModel::onProjectSelected,
                placeholder = "Select project",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            )
            state.errors["project"]?.let { FieldError(it) }

            PickerField(
                label = "Category *",
                selectedText = state.categoryId?.let { id -> state.categories.firstOrNull { it.first == id }?.second },
                options = state.categories,
                onSelect = viewModel::onCategorySelected,
                placeholder = "Select category",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            )
            state.errors["category"]?.let { FieldError(it) }

            PickerField(
                label = "Supplier",
                selectedText = state.supplierId?.let { id -> state.suppliers.firstOrNull { it.first == id }?.second }
                    ?: "No supplier",
                options = listOf("" to "No supplier") + state.suppliers,
                onSelect = viewModel::onSupplierSelected,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            )

            FormTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = "Description *",
                modifier = Modifier.fillMaxWidth(),
                isError = state.errors.containsKey("description"),
                supportingText = state.errors["description"],
                enabled = !state.loading,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(
                    value = state.quantityText,
                    onValueChange = viewModel::onQuantityChange,
                    label = "Quantity",
                    modifier = Modifier.weight(1f),
                    isError = state.errors.containsKey("quantity"),
                    supportingText = state.errors["quantity"],
                    keyboardType = KeyboardType.Decimal,
                    enabled = !state.loading,
                )
                FormTextField(
                    value = state.unitPriceText,
                    onValueChange = viewModel::onUnitPriceChange,
                    label = "Unit price (KES)",
                    modifier = Modifier.weight(1f),
                    isError = state.errors.containsKey("unitPrice"),
                    supportingText = state.errors["unitPrice"],
                    keyboardType = KeyboardType.Number,
                    enabled = !state.loading,
                )
            }

            FormTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = "Amount (KES) *",
                modifier = Modifier.fillMaxWidth(),
                isError = state.errors.containsKey("amount"),
                supportingText = state.errors["amount"]
                    ?: "Auto-calculated from quantity × unit price when both are filled in.",
                keyboardType = KeyboardType.Number,
                enabled = !state.loading,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Payment method",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            pair.forEach { method ->
                                FilterChip(
                                    selected = state.paymentMethod == method,
                                    onClick = { viewModel.onPaymentMethodChange(method) },
                                    label = { Text(method.label()) },
                                    enabled = !state.loading,
                                )
                            }
                        }
                    }
                }
            }

            DateField(
                label = "Expense date",
                value = state.date,
                onChange = { it?.let(viewModel::onDateChange) },
                clearable = false,
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

            ReceiptSection(
                container = container,
                receiptPath = state.receiptLocalPath,
                onPicked = viewModel::onReceiptPicked,
                onRemoved = viewModel::onReceiptRemoved,
                enabled = !state.loading,
            )

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving && !state.loading,
            ) {
                Text(if (state.saving) "Saving…" else "Save expense")
            }
            Text(
                "Expenses are saved on this phone first; the cloud copy follows automatically when online.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete this expense?",
            message = "The expense is removed from this phone and the cloud copy is deleted on the next sync. This cannot be undone.",
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

@Composable
private fun FieldError(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ReceiptSection(
    container: AppContainer,
    receiptPath: String?,
    onPicked: (String) -> Unit,
    onRemoved: () -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCapture by remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCapture
        if (success && file != null) onPicked(file.absolutePath)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val target = container.receiptStore.newCaptureFile()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.absolutePath
                }.getOrNull()
            }
            if (path != null) onPicked(path)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Receipt")
        if (receiptPath == null) {
            Text(
                "Attach a photo of the receipt. It stays on this phone and uploads to private storage after sign-in.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val file = container.receiptStore.newCaptureFile()
                        pendingCapture = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        takePicture.launch(uri)
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Take photo") }
                OutlinedButton(
                    onClick = { pickImage.launch("image/*") },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Choose photo") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReceiptPreview(
                    path = receiptPath,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Receipt attached",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Uploads to your private cloud storage after sign-in.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRemoved, enabled = enabled) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ReceiptPreview(path: String, modifier: Modifier = Modifier) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = "Receipt preview",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
