package com.siteledger.app.feature.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.siteledger.app.core.common.Money
import com.siteledger.app.core.designsystem.SyncBadge
import com.siteledger.app.core.util.Dates
import com.siteledger.app.domain.model.ExpenseWithContext

/** Compact ledger row reused on the dashboard, project detail and expense list. */
@Composable
fun ExpenseRowItem(
    item: ExpenseWithContext,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProject: Boolean = true,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.expense.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = buildString {
                    if (showProject) append(item.projectName).append(" · ")
                    append(item.categoryName)
                    item.supplierName?.let { append(" · ").append(it) }
                    append(" · ").append(Dates.format(item.expense.expenseDate))
                }
                Text(
                    details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.formatKes(item.expense.amountKes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SyncBadge(item.expense.syncState)
            }
        }
    }
}
