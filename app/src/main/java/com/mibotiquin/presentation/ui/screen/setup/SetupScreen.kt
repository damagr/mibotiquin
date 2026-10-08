package com.mibotiquin.presentation.ui.screen.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mibotiquin.MiBotiquinApplication
import com.mibotiquin.di.PreferencesManager

enum class SetupStep {
    CHOOSE_CAMERA,
    CHOOSE_NOTIFS,
    CHOOSE_FOLDER,
    DONE
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
    var folderPickCancelled by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) {
            // Picker cancelado → la pantalla ofrece Reintentar / Saltar
            folderPickCancelled = true
        } else {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                preferences.backupFolderUri = uri.toString()
                preferences.backupFolderDisplayName =
                    com.mibotiquin.presentation.ui.components.getDisplayName(
                        context.contentResolver, uri
                    )
            } catch (_: Exception) {
                // Provider no persistible: guardamos la URI igualmente
                preferences.backupFolderUri = uri.toString()
            }
            step = SetupStep.DONE
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (step) {
            SetupStep.CHOOSE_CAMERA -> {
                ChooseCameraStep(
                    onUseCamera = { enabled ->
                        preferences.useCamera = enabled
                        step = SetupStep.CHOOSE_NOTIFS
                    }
                )
            }
            SetupStep.CHOOSE_NOTIFS -> {
                ChooseNotificationsStep(
                    onConfirm = { cooldownDays, hour ->
                        preferences.notifCooldownDays = cooldownDays
                        preferences.notifHour = hour
                        // La programación se creó con el defecto en onCreate → reprogramar
                        MiBotiquinApplication.rescheduleExpiryChecks(context)
                        step = SetupStep.CHOOSE_FOLDER
                    }
                )
            }
            SetupStep.CHOOSE_FOLDER -> {
                ChooseFolderStep(
                    cancelled = folderPickCancelled,
                    onPickFolder = {
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            treeLauncher.launch(Uri.EMPTY)
                        }
                    },
                    // Saltar: sin carpeta SAF → almacenamiento interno va a ser el destino por
                    // defecto de los backups (Home + diálogo de primer botiquín a continuación)
                    onSkip = { step = SetupStep.DONE }
                )
            }
            SetupStep.DONE -> {
                DoneStep(onStart = onComplete)
            }
        }
    }
}

@Composable
private fun DoneStep(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Text(
            text = "¡Todo listo!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Ya puedes empezar a llenar tu botiquín. Escanea los códigos o añade productos a mano; te avisaremos antes de que caduquen.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Empezar")
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
    cancelled: Boolean,
    onPickFolder: () -> Unit,
    onSkip: () -> Unit
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

        if (cancelled) {
            Text(
                text = "No se eligió ninguna carpeta. Puedes reintentarlo o seguir con el almacenamiento interno.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )
            Button(
                onClick = onPickFolder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Reintentar")
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Saltar")
            }
        } else {
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
}

@Composable
private fun ChooseNotificationsStep(
    onConfirm: (cooldownDays: Int, hour: Int) -> Unit
) {
    var daysText by rememberSaveable { mutableStateOf("7") }
    var hour by rememberSaveable { mutableStateOf(9) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Text(
            text = "Avisos de caducidad",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "¿Cada cuánto quieres que te avisemos de los productos que caducan o están caducados, y a qué hora?",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Text("Recordar cada", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = daysText,
            onValueChange = { input ->
                if (input.length <= 3 && input.all { it.isDigit() }) daysText = input
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text("días (1–365)") },
            modifier = Modifier
                .width(180.dp)
                .padding(top = 8.dp)
        )

        Text(
            text = "A la hora",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            OutlinedButton(onClick = { if (hour > 0) hour-- }) { Text("−") }
            Text(
                text = "%02d:00".format(hour),
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedButton(onClick = { if (hour < 23) hour++ }) { Text("+") }
        }

        Button(
            onClick = {
                val days = daysText.toIntOrNull()?.coerceIn(1, 365) ?: 7
                onConfirm(days, hour)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text("Continuar")
        }
    }
}
