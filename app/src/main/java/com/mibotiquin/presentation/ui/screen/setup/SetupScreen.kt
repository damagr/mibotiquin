package com.mibotiquin.presentation.ui.screen.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mibotiquin.di.PreferencesManager

enum class SetupStep {
    CHOOSE_CAMERA,
    CHOOSE_FOLDER
}

/**
 * Configuración inicial (primera instalación):
 * 1. ¿Usar la cámara para escanear productos?
 * 2. Carpeta de backups (SAF)
 * Al terminar, Home pedirá crear el primer botiquín automáticamente.
 */
@Composable
fun SetupScreen(
    onComplete: () -> Unit,
    preferences: PreferencesManager
) {
    var step by rememberSaveable { mutableStateOf(SetupStep.CHOOSE_CAMERA) }
    val context = LocalContext.current

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selected ->
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(selected, flags)
                preferences.backupFolderUri = selected.toString()
                preferences.backupFolderDisplayName =
                    com.mibotiquin.presentation.ui.components.getDisplayName(
                        context.contentResolver, selected
                    )
            } catch (_: Exception) {
                // Provider no persistible: guardamos la URI igualmente
                preferences.backupFolderUri = selected.toString()
            }
            onComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (step) {
            SetupStep.CHOOSE_CAMERA -> {
                ChooseCameraStep(
                    onUseCamera = { enabled ->
                        preferences.useCamera = enabled
                        step = SetupStep.CHOOSE_FOLDER
                    }
                )
            }
            SetupStep.CHOOSE_FOLDER -> {
                ChooseFolderStep(
                    onPickFolder = {
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            treeLauncher.launch(Uri.EMPTY)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ChooseCameraStep(
    onUseCamera: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.MedicalInformation,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )

        Text(
            text = "Bienvenido a Mi Botiquín",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "¿Deseas usar la cámara para escanear los códigos de los productos?",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Text(
            text = "Podrás cambiar esta opción en los ajustes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = { onUseCamera(true) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text("Sí, usar cámara")
        }

        OutlinedButton(
            onClick = { onUseCamera(false) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text("No, introduciré los datos a mano")
        }
    }
}

@Composable
private fun ChooseFolderStep(
    onPickFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )

        Text(
            text = "Elige la carpeta de backups",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Los backups automáticos al actualizar la app se guardarán aquí.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Ej. Documentos/MiBotiquinBackups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onPickFolder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text("Elegir carpeta")
        }
    }
}
