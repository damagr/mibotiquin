package com.mibotiquin.presentation.ui.screen.expiring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mibotiquin.domain.model.ExpiryStatus
import com.mibotiquin.domain.model.ProductUiModel
import com.mibotiquin.presentation.ui.screen.home.HomeViewModel

/**
 * Pantalla de gestión de productos por caducar / caducados (SOON/CRITICAL/EXPIRED).
 * Lista scrolleable con checkboxes + acciones en lote (Mantener = silenciar · Eliminar).
 * Se abre al tocar la notificación resumen de caducidad.
 */
@Composable
fun ExpiringProductsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel
) {
    val products by viewModel.expiringProducts.collectAsStateWithLifecycle()
    val selectedIds = remember { mutableStateListOf<Long>() }

    // Limpiar selección de productos que ya no están en la lista
    LaunchedEffect(products) {
        val ids = products.map { it.product.id }
        selectedIds.retainAll { it in ids }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Atrás",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Productos por caducar",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        HorizontalDivider()

        if (products.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No hay productos por caducar ni caducados.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 8.dp
                )
            ) {
                items(products, key = { it.product.id }) { product ->
                    ExpiringRow(
                        product = product,
                        checked = product.product.id in selectedIds,
                        onToggle = {
                            if (product.product.id in selectedIds) {
                                selectedIds.remove(product.product.id)
                            } else {
                                selectedIds.add(product.product.id)
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            // Acciones en lote
            val selectedProducts = products.filter { it.product.id in selectedIds }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.silenceProducts(selectedProducts)
                        selectedIds.clear()
                    },
                    enabled = selectedProducts.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mantener")
                }
                Button(
                    onClick = {
                        viewModel.deleteProducts(selectedProducts)
                        selectedIds.clear()
                    },
                    enabled = selectedProducts.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Eliminar")
                }
            }
        }
    }
}

@Composable
private fun ExpiringRow(
    product: ProductUiModel,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.product.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusLabel(product),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor(product)
            )
        }
        Text(
            text = "x${product.product.quantity}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun statusColor(product: ProductUiModel) = when (product.expiryStatus) {
    ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.error
    ExpiryStatus.CRITICAL -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(product: ProductUiModel): String = when (product.expiryStatus) {
    ExpiryStatus.EXPIRED -> "Caducado · ${product.formattedExpiryMonth}"
    ExpiryStatus.CRITICAL -> "Caduca ${product.formattedMonthsUntilExpiry} · ${product.formattedExpiryMonth}"
    ExpiryStatus.SOON -> "Caduca ${product.formattedMonthsUntilExpiry} · ${product.formattedExpiryMonth}"
    else -> product.formattedExpiryMonth
}
