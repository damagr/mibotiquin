package com.mibotiquin.presentation.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.mibotiquin.BuildConfig
import com.mibotiquin.domain.model.Category
import com.mibotiquin.presentation.ui.screen.home.HomeViewModel
import com.mibotiquin.presentation.ui.theme.MiBotiquinTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val activeCabinet by viewModel.activeCabinet.collectAsStateWithLifecycle()
    val cabinets by viewModel.cabinets.collectAsStateWithLifecycle()
    val useCamera by viewModel.useCamera.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val viewModelToast by viewModel.toastMessage.collectAsStateWithLifecycle()

    // LaunchedEffect para mostrar toast cuando el ViewModel emite mensaje
    LaunchedEffect(viewModelToast) {
        viewModelToast?.let { msg ->
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

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
    ) { uri -> uri?.let { saveBackupFolder(ctx, it) } }

    // Status bar height fijo (24dp estándar Android) en lugar de detección automática
    val statusBarPadding = 24.dp

    MiBotiquinTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarPadding, start = 16.dp, end = 16.dp, bottom = 8.dp),
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

                    // === Familias de medicamentos ===
                    SettingsSection(title = "Familias de medicamentos") {
                        CategoryManagementSection(viewModel = viewModel)
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "MiBotiquín v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Icono: \"Farmacia\" de Freepik (Flaticon)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
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

@Composable
private fun CategoryManagementSection(viewModel: HomeViewModel) {
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val customCategories = allCategories.filterIsInstance<Category.CustomCategory>()
    var newCategoryName by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<Category.CustomCategory?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<Category.CustomCategory?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (customCategories.isEmpty()) {
            Text(
                text = "No hay familias personalizadas. Pulsa \"Nueva familia\" para crear una.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            customCategories.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row {
                        IconButton(
                            onClick = {
                                categoryToRename = category
                                renameValue = category.displayName
                                showRenameDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Renombrar familia",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                categoryToDelete = category
                                showDeleteConfirm = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Eliminar familia",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                newCategoryName = ""
                showAddDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Nueva familia")
        }
    }

    // Diálogo añadir familia
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            shape = RoundedCornerShape(0.dp),
            title = { Text("Nueva familia") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Nombre de la familia") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCategoryName.trim().isNotBlank()) {
                            val name = newCategoryName.trim()
                            viewModel.viewModelScope.launch {
                                try {
                                    viewModel.addCustomCategory(name)
                                    showAddDialog = false
                                } catch (e: Exception) {
                                    viewModel.showToast("Error al crear familia: ${e.message}")
                                }
                            }
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo confirmar eliminar
    if (showDeleteConfirm) {
        categoryToDelete?.let { category ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false; categoryToDelete = null },
                shape = RoundedCornerShape(0.dp),
                title = { Text("Eliminar familia") },
                text = { Text("¿Eliminar la familia \"${category.displayName}\"? Los productos de esta familia pasarán a \"Medicamentos\".") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = category.id
                            viewModel.viewModelScope.launch {
                                try {
                                    viewModel.deleteCustomCategory(id)
                                    showDeleteConfirm = false
                                    categoryToDelete = null
                                } catch (e: Exception) {
                                    viewModel.showToast("Error al eliminar familia: ${e.message}")
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Eliminar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false; categoryToDelete = null }) { Text("Cancelar") }
                }
            )
        }
    }

    // Diálogo renombrar familia
    if (showRenameDialog) {
        categoryToRename?.let { category ->
            AlertDialog(
                onDismissRequest = { showRenameDialog = false; categoryToRename = null },
                shape = RoundedCornerShape(0.dp),
                title = { Text("Renombrar familia") },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text("Nuevo nombre") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = category.id
                            val name = renameValue.trim()
                            if (name.isNotBlank()) {
                                viewModel.viewModelScope.launch {
                                    try {
                                        viewModel.renameCustomCategory(id, name)
                                        showRenameDialog = false
                                        categoryToRename = null
                                    } catch (e: Exception) {
                                        viewModel.showToast("Error al renombrar familia: ${e.message}")
                                    }
                                }
                            }
                        }
                    ) { Text("Renombrar") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false; categoryToRename = null }) { Text("Cancelar") }
                }
            )
        }
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
