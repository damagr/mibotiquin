package com.mibotiquin.presentation.ui.screen.setup

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class SetupStep {
    CHOOSE_FOLDER,
    CREATE_CABINET
}

/**
 * Flujo de primera instalación:
 * 1. Elegir carpeta de backups (SAF)
 * 2. Crear primer botiquín
 */
@Composable
fun SetupScreen(
    onComplete: () -> Unit,
    preferences: com.mibotiquin.di.PreferencesManager
) {
    var step by remember { mutableStateOf(SetupStep.CHOOSE_FOLDER) }
    var showCabinetDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selected ->
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(selected, flags)
                preferences.backupFolderUri = selected.toString()
                preferences.backupFolderDisplayName =
                    com.mibotiquin.presentation.ui.components.getDisplayName(
                        context.contentResolver, selected
                    )
                step = SetupStep.CREATE_CABINET
                showCabinetDialog = true
            } catch (_: Exception) {
                // Provider no persistible: avanzamos igualmente
                preferences.backupFolderUri = selected.toString()
                step = SetupStep.CREATE_CABINET
                showCabinetDialog = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (step) {
            SetupStep.CHOOSE_FOLDER -> {
                ChooseFolderStep(
                    onPickFolder = {
                        treeLauncher.launch(Uri.EMPTY)
                    }
                )
            }
            SetupStep.CREATE_CABINET -> {
                // El Home ya gestiona el diálogo de crear botiquín;
                // aquí solo completamos el setup
                LaunchedEffect(Unit) {
                    onComplete()
                }
            }
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
            text = "Antes de empezar, elige dónde se guardarán los backups automáticos que se hacen al actualizar la app.",
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Carpeta de backups",
                    style = MaterialTheme.typography.titleMedium
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