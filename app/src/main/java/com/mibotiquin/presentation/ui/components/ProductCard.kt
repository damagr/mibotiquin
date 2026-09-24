package com.mibotiquin.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mibotiquin.R
import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.ExpiryStatus
import com.mibotiquin.domain.model.ProductUiModel
import com.mibotiquin.presentation.ui.theme.ExpiryColors

/**
 * Card de producto: tap para editar (sheet de edición).
 * Sin stepper ni borrado directo: la edición y el borrado viven dentro del sheet.
 */
@Composable
fun ProductCard(
    product: ProductUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val expiryColors = ExpiryColors(product.expiryStatus)
    val hasUrgency = product.expiryStatus != ExpiryStatus.OK

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (hasUrgency) 1.5.dp else 1.dp,
            color = if (hasUrgency) expiryColors.border else colors.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = categoryIcon(product.product.category),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(28.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = product.product.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cantidad como badge discreto (la edición vive en el sheet)
                    Text(
                        text = "x${product.product.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (product.product.quantity > 0) colors.onSurfaceVariant
                        else colors.error
                    )
                    ExpiryChip(
                        status = product.expiryStatus,
                        monthsText = product.formattedMonthsUntilExpiry,
                        expiryMonth = product.formattedExpiryMonth,
                        isNonPerishable = product.product.isNonPerishable,
                        colors = expiryColors
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiryChip(
    status: ExpiryStatus,
    monthsText: String,
    expiryMonth: String,
    isNonPerishable: Boolean,
    colors: com.mibotiquin.presentation.ui.theme.ExpiryColorSet
) {
    val themeColors = MaterialTheme.colorScheme

    if (status == ExpiryStatus.OK) {
        Text(
            text = if (isNonPerishable) "No perecedero" else "Caduca $expiryMonth",
            style = MaterialTheme.typography.bodyMedium,
            color = themeColors.onSurfaceVariant
        )
    } else {
        Row(
            modifier = Modifier
                .background(colors.background, RoundedCornerShape(100.dp))
                .border(1.5.dp, colors.border, RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = when (status) {
                    ExpiryStatus.SOON -> Icons.Filled.Schedule
                    ExpiryStatus.CRITICAL -> Icons.Filled.WarningAmber
                    ExpiryStatus.EXPIRED -> Icons.Filled.ErrorOutline
                    ExpiryStatus.EMPTY -> Icons.Filled.Inventory2
                    else -> Icons.Filled.Schedule
                },
                contentDescription = null,
                tint = colors.icon,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = when (status) {
                    ExpiryStatus.SOON, ExpiryStatus.CRITICAL -> monthsText
                    ExpiryStatus.EXPIRED -> "Caducado ${monthsText.replace("desde ", "")}"
                    ExpiryStatus.EMPTY -> stringResource(R.string.product_empty)
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.text
            )
        }
    }
}

private fun categoryIcon(category: Category): ImageVector = when (category) {
    is Category.CustomCategory -> Icons.Filled.Medication // Default icon for custom categories
    Category.Medicamentos -> Icons.Filled.Medication
}
