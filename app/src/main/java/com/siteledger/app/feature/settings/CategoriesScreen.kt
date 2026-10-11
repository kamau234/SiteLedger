package com.siteledger.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.designsystem.ConfirmDialog
import com.siteledger.app.core.designsystem.EmptyState
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.core.designsystem.SyncBadge
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor

@Composable
fun CategoriesScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: CategoriesViewModel = viewModel(
        factory = viewModelFactoryFor { CategoriesViewModel(container.categoryRepository) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    ScreenScaffold(
        title = "Expense categories",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(onClick = { newName = ""; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Categories group your expenses for reports. Defaults are created when you first sign in; you can add more or remove unused ones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.categories.isEmpty()) {
                item {
                    EmptyState(
                        title = "No categories",
                        message = "Add a category to start recording expenses.",
                        actionLabel = "Add category",
                        onAction = { newName = ""; showAddDialog = true },
                    )
                }
            } else {
                items(state.categories, key = { it.id }) { category ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (category.isDefault) {
                                    Text(
                                        "Default category",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            SyncBadge(category.syncState)
                            IconButton(onClick = { pendingDeleteId = category.id }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete ${category.name}",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New category") },
            text = {
                FormTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = "Category name",
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        viewModel.addCategory(newName)
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        val category = state.categories.firstOrNull { it.id == deleteId }
        ConfirmDialog(
            title = "Remove ${category?.name ?: "this category"}?",
            message = "Categories used by recorded expenses cannot be removed; expenses keep their history either way.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                pendingDeleteId = null
                viewModel.deleteCategory(deleteId)
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}
