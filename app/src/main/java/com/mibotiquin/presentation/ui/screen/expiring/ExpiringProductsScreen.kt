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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mibotiquin.domain.model.ExpiryStatus
import com.mibotiquin.domain.model.ProductUiModel
import com.mibotiquin.presentation.ui.screen.home.HomeViewModel

/**
 * Pantalla de gestión de productos por caducar / caducados (SOON/CRITICAL/EXPIRED)
 * de TODOS los botiquines, agrupados por botiquín (encabezado + productos debajo).
 * Lista scrolleable con checkboxes + acciones en lote (Mantener = silenciar · Eliminar).
 * Se abre al tocar la notificación resumen de caducidad.
 */
@Composable
fun ExpiringProductsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel
) {
    val products by viewModel.expiringProducts.collectAsStateWithLifecycle()
    val cabinets by viewModel.cabinets.collectAsStateWithLifecycle()
    // Selección persistente (sobrevive rotación): Set<Long> serializable
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val toggleSelection: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    // Limpiar selección de productos que ya no están en la lista
    LaunchedEffect(products) {
        val ids = products.map { it.product.id }
        selectedIds = selectedIds.filter { it in ids }.toSet()
    }

    // Status bar height fijo (24dp), mismo patrón que el resto de pantallas
    val statusBarPadding = 24.dp

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarPadding, start = 8.dp, end = 8.dp, bottom = 8.dp),
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
            // Agrupar por botiquín (ID → nombre desde la lista de botiquines)
            val cabinetNames = cabinets.associate { it.id to it.name }
            val grouped = products.groupBy { it.product.cabinetId }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 8.dp
                )
            ) {
                grouped.forEach { (cabinetId, groupProducts) ->
                    val cabinetName = cabinetNames[cabinetId] ?: "Sin botiquín"

                    // Encabezado: nombre del botiquín + contador (como las familias en Home)
                    item(key = "header_$cabinetId") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cabinetName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = groupProducts.size.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Productos del grupo
                    items(groupProducts, key = { it.product.id }) { product ->
                        ExpiringRow(
                            product = product,
                            checked = product.product.id in selectedIds,
                            onToggle = { toggleSelection(product.product.id) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Acciones en lote (selección global entre grupos)
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
                        selectedIds = emptySet()
                    },
                    enabled = selectedProducts.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mantener")
                }
                Button(
                    onClick = {
                        viewModel.deleteProducts(selectedProducts)
                        selectedIds = emptySet()
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
