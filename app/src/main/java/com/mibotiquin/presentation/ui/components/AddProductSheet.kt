package com.mibotiquin.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mibotiquin.data.scan.CnExtractor
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
    prefillName: String? = null,            // nombre consultado de CIMA
    prefillExpiryYearMonth: String? = null, // "yyyy-MM" del parser GS1 (DataMatrix)
    prefillCn: String? = null,              // CN 6 dígitos para link prospecto CIMA
    prefillCimaName: String? = null,        // nombre CIMA confirmado (solo si existe en CIMA)
    onSave: (barcode: String, name: String, category: Category, quantity: Int, expiryDate: Long) -> Unit,
    onDelete: (() -> Unit)? = null,   // solo en edición
    onDismiss: () -> Unit
) {
    // rememberSaveable: sobrevive rotación y cierre inesperado
    // Prioridad: producto existente (edición) > prefills (CIMA/DataMatrix) > defaults
    var name by rememberSaveable {
        mutableStateOf(existing?.product?.name ?: prefillName ?: "")
    }
    var category by rememberSaveable {
        mutableStateOf(existing?.product?.category ?: Category.MEDICINE)
    }
    var quantity by rememberSaveable { mutableIntStateOf(existing?.product?.quantity ?: 1) }

    // Caducidad: mes/año. Prioridad: existente > DataMatrix > mismo mes del año siguiente
    val defaultExpiryMonth = remember(existing, prefillExpiryYearMonth) {
        existing?.product?.expiryMonth
            ?: prefillExpiryYearMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: YearMonth.now(ZoneId.systemDefault()).plusYears(1)
    }
    var expiryMonth by rememberSaveable { mutableStateOf(defaultExpiryMonth.toString()) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    // Launcher para abrir prospecto CIMA en navegador
    val openProspectoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val parsedExpiryMonth = expiryMonth.toYearMonthSafe(defaultExpiryMonth)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header custom: título a la izquierda, icono eliminar a la derecha (solo edición)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existing != null) "Editar producto" else "Añadir producto",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (existing != null && onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Eliminar producto",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Text(
                    text = "Código: $barcode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Chip "Prospecto" si tenemos CN confirmado + nombre CIMA
                if (prefillCn != null && prefillCimaName != null) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            val url = "https://cima.aemps.es/cima/dochtml/ft/$prefillCn.html"
                            openProspectoLauncher.launch(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        },
                        label = { Text("🔗 Prospecto") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

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
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { selected = selected.minusYears(1) }) { Text("-1 año") }
                    OutlinedButton(onClick = { selected = selected.plusYears(1) }) { Text("+1 año") }
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
