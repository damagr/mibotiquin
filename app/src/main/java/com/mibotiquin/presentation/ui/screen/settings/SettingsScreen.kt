package com.mibotiquin.presentation.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mibotiquin.BuildConfig
import com.mibotiquin.presentation.ui.screen.home.HomeViewModel
import com.mibotiquin.presentation.ui.theme.MiBotiquinTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val activeCabinet by viewModel.activeCabinet.collectAsStateWithLifecycle()
    val cabinets by viewModel.cabinets.collectAsStateWithLifecycle()
    val useCamera by viewModel.useCamera.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Launchers SAF (las acciones de backup/compartir viven aquí desde la migración)
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importCabinet) }
    val shareCabinetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val name = activeCabinet?.name ?: "botiquin"
            viewModel.shareCabinet(it)
        }
    }
    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { saveBackupFolder(context, it) } }

    MiBotiquinTheme {
        Surface(modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        text = "Ajustes",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // === Cámara ===
                    SettingsSection(title = "Cámara") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Escanear códigos con la cámara",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = useCamera,
                                onCheckedChange = { viewModel.onToggleCamera() }
                            )
                        }
                        Text(
                            text = "Si está desactivada, el botón '+' añade productos manualmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    // === Botiquines ===
                    SettingsSection(title = "Botiquín") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeCabinet?.name ?: "Sin botiquín",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${cabinets.size} botiquín(es)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Cambiar botiquín activo (igual que el dropdown del Home)
                            if (cabinets.size > 1) {
                                var expanded by rememberSaveable { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cambiar botiquín")
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        cabinets.forEach { cabinet ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = cabinet.name,
                                                        color = if (cabinet.id == activeCabinet?.id)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                onClick = {
                                                    expanded = false
                                                    viewModel.selectCabinet(cabinet.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.onShowCreateCabinet(true) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Nuevo botiquín")
                            }

                            Button(
                                onClick = {
                                    activeCabinet?.let { viewModel.onRequestDeleteCabinet(it) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Eliminar botiquín actual")
                            }
                        }
                    }

                    HorizontalDivider()

                    // === Copias de seguridad ===
                    SettingsSection(title = "Copias de seguridad") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "El backup incluye TODOS tus botiquines. Se restaura automáticamente solo si eliges el fichero.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { exportBackupLauncher.launch("mibotiquin_backup.json") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Exportar backup (JSON)")
                            }
                            OutlinedButton(
                                onClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Restaurar backup (JSON)")
                            }
                        }
                    }

                    HorizontalDivider()

                    // === Compartir ===
                    SettingsSection(title = "Compartir") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Comparte el botiquín activo con otro dispositivo. Si el otro tiene una versión más reciente, prevalecerá la suya.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { shareCabinetLauncher.launch("botiquin.json") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Compartir este botiquín")
                            }
                            OutlinedButton(
                                onClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Importar botiquín (JSON)")
                            }
                        }
                    }

                    HorizontalDivider()

                    // === Carpeta de backups ===
                    SettingsSection(title = "Carpeta de backups") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Los backups automáticos al actualizar la app se guardan aquí.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { backupFolderLauncher.launch(Uri.EMPTY) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Elegir carpeta")
                            }
                        }
                    }

                    HorizontalDivider()

                    // === Actualizaciones ===
                    SettingsSection(title = "Actualizaciones") {
                        Button(
                            onClick = { viewModel.checkForUpdate(userInitiated = true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Buscar actualizaciones")
                        }
                    }

                    HorizontalDivider()

                    // === Versión ===
                    SettingsSection(title = "Acerca de") {
                        Text(
                            text = "MiBotiquín v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

private fun saveBackupFolder(context: Context, uri: Uri) {
    try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val prefs = context.getSharedPreferences("mibotiquin_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("backup_folder_uri", uri.toString())
            .putString(
                "backup_folder_display_name",
                com.mibotiquin.presentation.ui.components.getDisplayName(context.contentResolver, uri)
            )
            .apply()
        android.widget.Toast.makeText(context, "Carpeta de backups actualizada", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al guardar carpeta", android.widget.Toast.LENGTH_SHORT).show()
    }
}
