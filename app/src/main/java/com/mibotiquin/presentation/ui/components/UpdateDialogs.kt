package com.mibotiquin.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mibotiquin.data.api.GitHubRelease
import com.mibotiquin.ui.UpdateState

@Composable
fun UpdateAvailableDialog(
    release: com.mibotiquin.data.api.GitHubRelease,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        shape = androidx.compose.ui.graphics.RectangleShape,
        onDismissRequest = onDismiss,
        title = { Text("Nueva versión disponible") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Versión ${release.tagName} disponible.")
                release.body?.let { body ->
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(
                        text = body.take(500),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                }
                Text("Se creará un backup de tus datos antes de actualizar. ¿Actualizar ahora?")
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("Actualizar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ahora no") }
        }
    )
}

@Composable
fun UpdateDownloadingDialog(progress: Int) {
    AlertDialog(
        shape = androidx.compose.ui.graphics.RectangleShape,
        onDismissRequest = {},
        title = { Text("Descargando actualización…") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text("$progress%")
            }
        },
        confirmButton = {}
    )
}

@Composable
fun UpdateReadyDialog(
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        shape = androidx.compose.ui.graphics.RectangleShape,
        onDismissRequest = onDismiss,
        title = { Text("Actualización lista") },
        text = { Text("Se instalará la nueva versión ahora.") },
        confirmButton = {
            Button(onClick = onInstall) { Text("Instalar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        shape = androidx.compose.ui.graphics.RectangleShape,
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}