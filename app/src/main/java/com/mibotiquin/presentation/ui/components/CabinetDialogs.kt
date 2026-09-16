package com.mibotiquin.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.mibotiquin.domain.model.Cabinet

/** Diálogo de creación de botiquín. Obligatorio cuando la DB está vacía (no dismissible). */
@Composable
fun CreateCabinetDialog(
    isMandatory: Boolean,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isMandatory) onDismiss() },
        shape = RectangleShape,
        title = {
            Text(
                text = if (isMandatory) "Dale nombre a tu botiquín" else "Nuevo botiquín",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isMandatory) {
                    Text(
                        text = "Crea tu primer botiquín para empezar a guardar productos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. Casa, Trabajo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            if (!isMandatory) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

/** Borrado estilo GitHub: confirmar escribiendo el nombre exacto. */
@Composable
fun DeleteCabinetDialog(
    cabinet: Cabinet,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typedName by rememberSaveable { mutableStateOf("") }
    val matches = typedName.trim() == cabinet.name

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = {
            Text(
                text = "Eliminar \"${cabinet.name}\"",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Esta acción no se puede deshacer. Se eliminarán todos los productos de este botiquín.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Escribe el nombre para confirmar:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = typedName,
                    onValueChange = { typedName = it },
                    label = { Text(cabinet.name) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = matches,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Eliminar botiquín")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
