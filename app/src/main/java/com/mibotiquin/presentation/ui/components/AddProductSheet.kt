package com.mibotiquin.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.ProductUiModel
import java.time.YearMonth
import java.time.ZoneId

/**
 * Diálogo de añadir/editar producto (cuadrado, sin bordes redondeados).
 * - Nombre, categoría (chips con FlowRow), cantidad, caducidad MM/AAAA
 * - En edición: botón Eliminar con confirmación
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddProductSheet(
    barcode: String,
    existing: ProductUiModel? = null,
    onSave: (barcode: String, name: String, category: Category, quantity: Int, expiryDate: Long) -> Unit,
    onDelete: (() -> Unit)? = null,   // solo en edición
    onDismiss: () -> Unit
) {
    // rememberSaveable: sobrevive rotación y cierre inesperado
    var name by rememberSaveable { mutableStateOf(existing?.product?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.product?.category ?: Category.MEDICINE) }
    var quantity by rememberSaveable { mutableIntStateOf(existing?.product?.quantity ?: 1) }

    // Caducidad: mes/año. Default: mismo mes del año siguiente
    val defaultExpiryMonth = remember(existing) {
        existing?.product?.expiryMonth ?: YearMonth.now(ZoneId.systemDefault()).plusYears(1)
    }
    var expiryMonth by rememberSaveable { mutableStateOf(defaultExpiryMonth.toString()) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val parsedExpiryMonth = expiryMonth.toYearMonthSafe(defaultExpiryMonth)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = {
            Text(
                text = if (existing != null) "Editar producto" else "Añadir producto",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Código: $barcode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del producto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Categorías: FlowRow (los chips se envuelven, no se amontonan)
                Text("Categoría", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Category.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }

                // Cantidad
                Text("Cantidad", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (quantity > 1) quantity-- },
                        enabled = quantity > 1
                    ) { Text("−") }
                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    OutlinedButton(onClick = { quantity++ }) { Text("+") }
                }

                // Caducidad MM/AAAA
                OutlinedButton(
                    onClick = { showMonthPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Caducidad: ${parsedExpiryMonth.monthValue.toString().padStart(2, '0')}/${parsedExpiryMonth.year}")
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            barcode,
                            name.trim(),
                            category,
                            quantity,
                            parsedExpiryMonth.atDay(1)
                                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        )
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text(if (existing != null) "Guardar cambios" else "Guardar")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Eliminar")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )

    // Picker mes/año
    if (showMonthPicker) {
        MonthYearPickerDialog(
            initial = parsedExpiryMonth,
            onConfirm = {
                expiryMonth = it.toString()
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false }
        )
    }

    // Confirmación de borrado
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RectangleShape,
            title = { Text("Eliminar producto") },
            text = { Text("¿Eliminar \"${existing?.product?.name}\" de tu botiquín? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Picker de mes/año: ◀ MM/AAAA ▶ (los medicamentos caducan por mes, no por día) */
@Composable
fun MonthYearPickerDialog(
    initial: YearMonth,
    onConfirm: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = { Text("Fecha de caducidad") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = { selected = selected.minusMonths(1) }) { Text("◀") }
                    Text(
                        text = "%02d/%04d".format(selected.monthValue, selected.year),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    OutlinedButton(onClick = { selected = selected.plusMonths(1) }) { Text("▶") }
                }
                OutlinedButton(onClick = { selected = selected.plusMonths(12) }) {
                    Text("+1 año")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun String.toYearMonthSafe(fallback: YearMonth): YearMonth {
    return try {
        YearMonth.parse(this)
    } catch (_: Exception) {
        fallback
    }
}
