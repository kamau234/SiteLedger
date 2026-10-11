package com.siteledger.app.feature.suppliers

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
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siteledger.app.core.designsystem.EmptyState
import com.siteledger.app.core.designsystem.FormTextField
import com.siteledger.app.core.designsystem.ScreenScaffold
import com.siteledger.app.core.designsystem.SyncBadge
import com.siteledger.app.di.AppContainer
import com.siteledger.app.di.viewModelFactoryFor

@Composable
fun SupplierListScreen(
    container: AppContainer,
    onAddSupplier: () -> Unit,
    onOpenSupplier: (String) -> Unit,
) {
    val viewModel: SuppliersViewModel = viewModel(
        factory = viewModelFactoryFor { SuppliersViewModel(container.supplierRepository) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Suppliers",
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSupplier) {
                Icon(Icons.Filled.Add, contentDescription = "New supplier")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FormTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = "Search suppliers",
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Search,
                )
            }
            if (state.items.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.totalCount == 0) "No suppliers yet" else "No matches",
                        message = if (state.totalCount == 0) {
                            "Add the traders and contractors you buy from, then attach them to expenses."
                        } else {
                            "Try a different search term."
                        },
                        actionLabel = if (state.totalCount == 0) "New supplier" else null,
                        onAction = if (state.totalCount == 0) onAddSupplier else null,
                    )
                }
            } else {
                items(state.items, key = { it.id }) { supplier ->
                    Card(onClick = { onOpenSupplier(supplier.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    supplier.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                val details = listOfNotNull(supplier.phone, supplier.location).joinToString(" · ")
                                if (details.isNotBlank()) {
                                    Text(
                                        details,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            SyncBadge(supplier.syncState)
                        }
                    }
                }
            }
        }
    }
}
