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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import com.mibotiquin.data.transfer.LocalTransfer
import com.mibotiquin.domain.model.Category
import com.mibotiquin.presentation.ui.screen.home.HomeViewModel
import com.mibotiquin.presentation.ui.theme.MiBotiquinTheme
import com.mibotiquin.ui.UpdateState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    transferViewModel: LocalTransferViewModel = viewModel()
) {
    val activeCabinet by viewModel.activeCabinet.collectAsStateWithLifecycle()
    val cabinets by viewModel.cabinets.collectAsStateWithLifecycle()
    val useCamera by viewModel.useCamera.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val viewModelToast by viewModel.toastMessage.collectAsStateWithLifecycle()

    // Transferencia directa: dispositivos + estados + resultado
    val devices by transferViewModel.devices.collectAsStateWithLifecycle()
    val searching by transferViewModel.searching.collectAsStateWithLifecycle()
    val listening by transferViewModel.listening.collectAsStateWithLifecycle()
    val sending by transferViewModel.sending.collectAsStateWithLifecycle()
    val transferResult by transferViewModel.transferResult.collectAsStateWithLifecycle()
    var showSendDialog by rememberSaveable { mutableStateOf(false) }
    var showReceiveDialog by rememberSaveable { mutableStateOf(false) }

    // Estado de actualización (para el feedback "Comprobando…" del botón en Acerca de)
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    // Avisos de caducidad (cooldown + hora)
    val notifCooldownDays by viewModel.notifCooldownDays.collectAsStateWithLifecycle()
    val notifHour by viewModel.notifHour.collectAsStateWithLifecycle()
    var showCooldownDialog by rememberSaveable { mutableStateOf(false) }
    var showHourDialog by rememberSaveable { mutableStateOf(false) }

    // LaunchedEffect para mostrar toast cuando el ViewModel emite mensaje
    LaunchedEffect(viewModelToast) {
        viewModelToast?.let { msg ->
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Resultado de transferencia directa: toast + cerrar el diálogo correspondiente
    LaunchedEffect(transferResult) {
        transferResult?.let { result ->
            val msg = when (result) {
                is LocalTransfer.TransferResult.Sent -> "Enviado a ${result.deviceName}"
                is LocalTransfer.TransferResult.Received ->
                    "${result.description} recibido (${result.productCount} productos)"
                is LocalTransfer.TransferResult.RejectedOlderLocal ->
                    "Transferencia rechazada: tu versión local es más reciente"
                is LocalTransfer.TransferResult.Error -> result.message
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            if (result !is LocalTransfer.TransferResult.Error) {
                showSendDialog = false
                showReceiveDialog = false
            }
            transferViewModel.consumeResult()
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
    var backupFolderSet by remember { mutableStateOf(viewModel.hasBackupFolder()) }
    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            saveBackupFolder(ctx, it)
            backupFolderSet = true
        }
    }

    // Status bar height fijo (24dp estándar Android) en lugar de detección automática
    val statusBarPadding = 24.dp

    // Navegación interna de Ajustes (menú ↔ sub-pantallas) sin tocar el NavHost
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = section != null) { section = null }

    MiBotiquinTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: ← vuelve al menú (o sale) · título según la sección
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarPadding, start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (section == null) onBack() else section = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (section == null) "Atrás" else "Volver al menú",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = settingsSectionTitle(section),
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
                    when (section) {

                        // ================= MENÚ =================
                        null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SettingsMenuRow(
                                    icon = Icons.Filled.Inventory2,
                                    title = "Botiquines",
                                    subtitle = activeCabinet?.name,
                                    onClick = { section = "cabinets" }
                                )
                                SettingsMenuRow(
                                    icon = Icons.Filled.LocalOffer,
                                    title = "Familias de medicamentos",
                                    onClick = { section = "families" }
                                )
                                SettingsMenuRow(
                                    icon = Icons.Filled.Share,
                                    title = "Compartir y transferir",
                                    onClick = { section = "share" }
                                )
                                SettingsMenuRow(
                                    icon = Icons.Filled.Backup,
                                    title = "Copias de seguridad",
                                    subtitle = if (backupFolderSet) "Automáticas semanales" else "Sin carpeta configurada",
                                    onClick = { section = "backup" }
                                )
                                SettingsMenuRow(
                                    icon = Icons.Filled.Notifications,
                                    title = "Avisos de caducidad",
                                    subtitle = "$notifCooldownDays días · %02d:00".format(notifHour),
                                    onClick = { section = "notifications" }
                                )
                                // Cámara: ajuste único → switch inline
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 16.dp)
                                    ) {
                                        Text("Cámara", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "Escanear códigos con la cámara",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = useCamera,
                                        onCheckedChange = { viewModel.onToggleCamera() }
                                    )
                                }
                                SettingsMenuRow(
                                    icon = Icons.Filled.Info,
                                    title = "Acerca de",
                                    subtitle = "v${BuildConfig.VERSION_NAME}",
                                    onClick = { section = "about" }
                                )
                            }
                        }

                        // ================= BOTIQUINES =================
                        "cabinets" -> {
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

                                if (cabinets.size > 1) {
                                    var expanded by rememberSaveable { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { expanded = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                                            Text("  Cambiar botiquín")
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

                                // Acción principal de la pantalla → relleno
                                Button(
                                    onClick = { viewModel.onShowCreateCabinet(true) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Text("  Nuevo botiquín")
                                }

                                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                                // Destructiva → fila roja discreta al final
                                TextButton(
                                    onClick = {
                                        activeCabinet?.let { viewModel.onRequestDeleteCabinet(it) }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("  Eliminar botiquín actual")
                                }
                            }
                        }

                        // ================= FAMILIAS =================
                        "families" -> {
                            CategoryManagementSection(viewModel = viewModel)
                        }

                        // ================= COMPARTIR Y TRANSFERIR =================
                        "share" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Misma red WiFi, sin ficheros. Si no compartís red: activa el punto de acceso en uno de los dos móviles y conecta el otro a él.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        transferViewModel.startDiscovery()
                                        showSendDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Text("  Enviar a otro dispositivo")
                                }
                                OutlinedButton(
                                    onClick = {
                                        transferViewModel.startReceiving()
                                        showReceiveDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Text("  Recibir de otro dispositivo")
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                Text(
                                    text = "Ficheros: guarda el botiquín activo o cárgalo desde un fichero (friends, WhatsApp, Drive…).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { shareCabinetLauncher.launch("botiquin.json") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Upload, contentDescription = null)
                                    Text("  Exportar botiquín")
                                }
                                OutlinedButton(
                                    onClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Text("  Importar botiquín")
                                }
                            }
                        }

                        // ================= COPIAS DE SEGURIDAD =================
                        "backup" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "El backup incluye TODOS tus botiquines. Se restaura automáticamente solo si eliges el fichero.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!backupFolderSet) {
                                    Text(
                                        text = "⚠ No has elegido carpeta de backups: los backups automáticos (semanal y previo a actualizar) no se harán.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    onClick = { exportBackupLauncher.launch("mibotiquin_backup.json") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Upload, contentDescription = null)
                                    Text("  Exportar backup")
                                }
                                OutlinedButton(
                                    onClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Text("  Restaurar backup")
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                Text(
                                    text = "Carpeta de backups automáticos (semanal y antes de actualizar la app).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { backupFolderLauncher.launch(Uri.EMPTY) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                    Text("  Elegir carpeta")
                                }
                            }
                        }

                        // ================= AVISOS DE CADUCIDAD =================
                        "notifications" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Te avisamos de los productos que caducan o están caducados. Al empeorar avisa siempre; si sigue igual, recuerda según el intervalo elegido.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Recordar cada", style = MaterialTheme.typography.bodyLarge)
                                    OutlinedButton(onClick = { showCooldownDialog = true }) {
                                        Text(if (notifCooldownDays == 1) "1 día" else "$notifCooldownDays días")
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Hora del aviso", style = MaterialTheme.typography.bodyLarge)
                                    OutlinedButton(onClick = { showHourDialog = true }) {
                                        Text("%02d:00".format(notifHour))
                                    }
                                }
                            }
                        }

                        // ================= ACERCA DE =================
                        "about" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.checkForUpdate(userInitiated = true) },
                                    enabled = updateState !is UpdateState.Checking,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                    Text(
                                        if (updateState is UpdateState.Checking) "  Comprobando…"
                                        else "  Buscar actualizaciones"
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showHelpDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.HelpOutline, contentDescription = null)
                                    Text("  ¿Cómo funciona?")
                                }
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
                    }

                    Box(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // ---- Diálogos de avisos de caducidad ----

    // Intervalo de recordatorio (1..365 días)
    if (showCooldownDialog) {
        var daysText by remember { mutableStateOf(notifCooldownDays.toString()) }
        AlertDialog(
            onDismissRequest = { showCooldownDialog = false },
            shape = RoundedCornerShape(0.dp),
            title = { Text("Recordar cada") },
            text = {
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { input ->
                        if (input.length <= 3 && input.all { it.isDigit() }) daysText = input
                    },
                    label = { Text("días (1–365)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val days = daysText.toIntOrNull()?.coerceIn(1, 365) ?: notifCooldownDays
                    viewModel.setNotifCooldownDays(days)
                    showCooldownDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showCooldownDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Hora del aviso (0..23, sin minutos)
    if (showHourDialog) {
        var hour by remember { mutableStateOf(notifHour) }
        AlertDialog(
            onDismissRequest = { showHourDialog = false },
            shape = RoundedCornerShape(0.dp),
            title = { Text("Hora del aviso") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { if (hour > 0) hour-- }) { Text("−") }
                    Text(
                        text = "%02d:00".format(hour),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    OutlinedButton(onClick = { if (hour < 23) hour++ }) { Text("+") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNotifHour(hour)
                    showHourDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showHourDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // ---- Ayuda: ¿Cómo funciona? ----
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            shape = RoundedCornerShape(0.dp),
            title = { Text("¿Cómo funciona?") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("📷 Añadir productos", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Toca el botón + y escanea el código de barras: la app busca el nombre. Después lee la caducidad del DataMatrix o la pones a mano con el calendario.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("🏷 Familias", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Agrupan tus productos: «Medicamentos» + las que crees tú. Puedes crearlas al vuelo con el chip «Nueva» dentro del formulario.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("🔔 Avisos de caducidad", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Te avisamos antes de que caduquen. Avisa siempre que un producto empeora; si sigue igual, recuerda cada X días a la hora que elijas. «Mantener» silencia hasta que empeore.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("📤 Compartir y transferir", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "En la misma WiFi, «Enviar/Recibir» pasa el botiquín directo, sin ficheros. Para alguien lejano, usa los ficheros (exportar/importar) o el resumen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("💾 Copias de seguridad", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Si eliges una carpeta, se guarda un backup semanal automático y otro antes de cada actualización. También puedes exportar/restaurar a mano.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("Entendido") }
            }
        )
    }

    // ---- Diálogos de transferencia directa ----

    // Enviar: elegir QUÉ transferir + DESTINO (dispositivos NSD o share sheet)
    if (showSendDialog) {
        // Elección de qué transferir: botiquín activo o backup completo
        var transferWhat by rememberSaveable { mutableStateOf(TransferWhat.CABINET.name) }

        AlertDialog(
            onDismissRequest = {
                showSendDialog = false
                transferViewModel.stopDiscovery()
            },
            shape = RoundedCornerShape(0.dp),
            title = { Text("Enviar a otro dispositivo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // --- Paso 1: QUÉ transferir ---
                    Text("¿Qué quieres transferir?", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = transferWhat == TransferWhat.CABINET.name,
                            onClick = { transferWhat = TransferWhat.CABINET.name },
                            label = { Text("Botiquín activo") }
                        )
                        FilterChip(
                            selected = transferWhat == TransferWhat.BACKUP.name,
                            onClick = { transferWhat = TransferWhat.BACKUP.name },
                            label = { Text("Backup completo") }
                        )
                    }

                    // --- Paso 2: DESTINO ---
                    Text("¿A dónde?", style = MaterialTheme.typography.titleSmall)
                    if (sending) {
                        Text("Enviando…", style = MaterialTheme.typography.bodyLarge)
                    } else if (searching && devices.isEmpty()) {
                        Text(
                            "Buscando dispositivos con Mi Botiquín en tu red…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (devices.isEmpty()) {
                        Text(
                            "No se han encontrado dispositivos. Comprueba que el otro móvil tenga Mi Botiquín abierto y estéis en la misma red WiFi (o en su punto de acceso).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        devices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        transferViewModel.sendTo(
                                            device,
                                            TransferWhat.valueOf(transferWhat),
                                            activeCabinet?.id
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Alternativa: compartir por app (share sheet) con lo elegido
                    OutlinedButton(
                        onClick = {
                            showSendDialog = false
                            transferViewModel.stopDiscovery()
                            when (TransferWhat.valueOf(transferWhat)) {
                                TransferWhat.CABINET -> viewModel.shareCabinetDirect()
                                TransferWhat.BACKUP -> viewModel.shareBackupDirect()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Compartir por app… (WhatsApp, Drive…)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSendDialog = false
                    transferViewModel.stopDiscovery()
                }) { Text("Cerrar") }
            }
        )
    }

    // Recibir: escuchando
    if (showReceiveDialog) {
        AlertDialog(
            onDismissRequest = {
                showReceiveDialog = false
                transferViewModel.stopReceiving()
            },
            shape = RoundedCornerShape(0.dp),
            title = { Text("Recibir de otro dispositivo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Esperando a que el otro dispositivo envíe su botiquín…",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Mantén esta pantalla abierta. Si no compartís red WiFi, activa el punto de acceso en el otro móvil y conecta este a él.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showReceiveDialog = false
                    transferViewModel.stopReceiving()
                }) { Text("Cancelar") }
            }
        )
    }
}

/** Título de la pantalla de Ajustes según la sub-sección activa */
private fun settingsSectionTitle(section: String?): String = when (section) {
    null -> "Ajustes"
    "cabinets" -> "Botiquines"
    "families" -> "Familias de medicamentos"
    "share" -> "Compartir y transferir"
    "backup" -> "Copias de seguridad"
    "notifications" -> "Avisos de caducidad"
    "about" -> "Acerca de"
    else -> "Ajustes"
}

/** Fila de menú (icono + título/subtítulo + chevron) que navega a una sub-pantalla */
@Composable
private fun SettingsMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
