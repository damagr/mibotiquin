package com.mibotiquin.presentation.ui.screen.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mibotiquin.BuildConfig
import com.mibotiquin.R
import com.mibotiquin.domain.model.Cabinet
import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.ProductUiModel
import com.mibotiquin.presentation.ui.components.AddProductSheet
import com.mibotiquin.presentation.ui.components.CreateCabinetDialog
import com.mibotiquin.presentation.ui.components.DeleteCabinetDialog
import com.mibotiquin.presentation.ui.components.ProductCard
import com.mibotiquin.presentation.ui.components.SearchBar
import com.mibotiquin.ui.UpdateState

@Composable
fun HomeScreen(
    onOpenScanner: () -> Unit,
    scannedCn: String? = null,
    scannedName: String? = null,
    scannedExpiry: String? = null,
    onScanConsumed: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cabinets by viewModel.cabinets.collectAsStateWithLifecycle()
    val activeCabinet by viewModel.activeCabinet.collectAsStateWithLifecycle()
    val showCreateCabinet by viewModel.showCreateCabinet.collectAsStateWithLifecycle()
    val cabinetToDelete by viewModel.cabinetToDelete.collectAsStateWithLifecycle()
    val transferEvent by viewModel.transferEvent.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    viewModel.useCameraRefresh.collectAsStateWithLifecycle() // recompose al cambiar cámara
    val useCamera = viewModel.useCamera

    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    // Permiso de notificaciones (API 33+) — una sola vez al abrir
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        focusRequester.requestFocus()
    }

    // SAF: exportar / importar botiquín
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::shareCabinet) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importCabinet) }

    // Selector de carpeta de backups
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { saveBackupFolder(context, it) }
    }

    LaunchedEffect(transferEvent) {
        transferEvent?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.onTransferEventShown()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Header: nombre del botiquín + versión + menú (con insets del status bar para notches)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CabinetHeader(
                cabinets = cabinets,
                activeCabinet = activeCabinet,
                onSelect = viewModel::selectCabinet,
                modifier = Modifier.weight(1f)
            )
            TransferMenu(
                onExport = {
                    val name = activeCabinet?.name ?: "botiquin"
                    exportLauncher.launch("botiquin_${name.replace(" ", "_")}.json")
                },
                onImport = { importLauncher.launch(arrayOf("application/json")) },
                onNewCabinet = { viewModel.onShowCreateCabinet(true) },
                onDeleteCabinet = {
                    activeCabinet?.let { viewModel.onRequestDeleteCabinet(it) }
                },
                onCheckUpdate = { viewModel.checkForUpdate(userInitiated = true) },
                onChangeBackupFolder = {
                    openDocumentTreeLauncher.launch(Uri.EMPTY)
                },
                onToggleCamera = { viewModel.onToggleCamera() },
                useCamera = useCamera,
                cabinetCount = cabinets.size
            )
        }

        // Search bar + botón de escaneo (fuera del searchbar: buscar ≠ introducir)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = query,
                onQueryChange = viewModel::onSearchQueryChange,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenScanner, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (useCamera) Icons.Filled.PhotoCamera else Icons.Filled.Add,
                    contentDescription = if (useCamera) "Escanear producto" else "Añadir producto",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        ProductList(
            products = products,
            query = query,
            onAddProduct = onOpenScanner,
            onProductClick = viewModel::openEditProduct,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        )
    }

    // ---- Diálogos ----

    if (showCreateCabinet) {
        CreateCabinetDialog(
            isMandatory = cabinets.isEmpty(),
            onCreate = viewModel::createCabinet,
            onDismiss = { viewModel.onShowCreateCabinet(false) }
        )
    }

    cabinetToDelete?.let { cabinet ->
        DeleteCabinetDialog(
            cabinet = cabinet,
            onConfirm = { viewModel.deleteCabinet(cabinet.id) },
            onDismiss = viewModel::onDismissDeleteCabinet
        )
    }

    // Edición de producto (tap en card) → diálogo pre-rellenado con borrado confirmado
    val editingProduct by viewModel.editingProduct.collectAsStateWithLifecycle()
    editingProduct?.let { product ->
        AddProductSheet(
            barcode = product.product.barcode,
            existing = product,
            onSave = { code, name, category, quantity, expiry ->
                viewModel.addProduct(code, name, category, quantity, expiry)
            },
            onDelete = {
                viewModel.onDeleteProduct(product)
                viewModel.closeEditProduct()
            },
            onDismiss = viewModel::closeEditProduct
        )
    }

    // ---- Diálogos de actualización ----

    when (val state = updateState) {
        is UpdateState.Available -> {
            UpdateAvailableDialog(
                release = state.release,
                onUpdate = { viewModel.startUpdate(state.release) },
                onDismiss = viewModel::dismissUpdateDialog
            )
        }
        is UpdateState.Downloading -> {
            UpdateDownloadingDialog(progress = state.progress)
        }
        is UpdateState.ReadyToInstall -> {
            UpdateReadyDialog(
                onInstall = { viewModel.installApk(state.file) },
                onDismiss = viewModel::dismissUpdateDialog
            )
        }
        is UpdateState.Error -> {
            UpdateErrorDialog(
                message = state.message,
                onDismiss = viewModel::dismissUpdateError
            )
        }
        else -> Unit
    }

    // CN escaneado → buscar en la DB local: si existe, editar; si no, sheet con prefills de CIMA/DataMatrix
    scannedCn?.let { barcode ->
        val existing by viewModel.existingForBarcode.collectAsStateWithLifecycle()
        var lookupDone by remember(barcode) { mutableStateOf(false) }

        LaunchedEffect(barcode) {
            viewModel.lookupBarcode(barcode)
            lookupDone = true
        }

        if (lookupDone) {
            val isNewProduct = existing == null
            AddProductSheet(
                barcode = barcode,
                existing = existing,
                prefillName = if (isNewProduct) scannedName else null,
                prefillExpiryYearMonth = if (isNewProduct) scannedExpiry else null,
                onSave = { code, name, category, quantity, expiry ->
                    viewModel.addProduct(code, name, category, quantity, expiry)
                    onScanConsumed()
                },
                onDismiss = {
                    viewModel.clearLookup()
                    onScanConsumed()
                }
            )
        }
    }
}

// ---- Diálogos de actualización ----

@Composable
private fun UpdateAvailableDialog(
    release: com.mibotiquin.data.api.GitHubRelease,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva versión disponible") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Versión ${release.tagName} disponible.")
                if (!release.body.isNullOrBlank()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(
                        text = release.body.take(500),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text("Se creará un backup de tus datos antes de actualizar. ¿Actualizar ahora?")
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Actualizar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } }
    )
}

@Composable
private fun UpdateDownloadingDialog(progress: Int) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Descargando actualización…") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
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
private fun UpdateReadyDialog(
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualización lista") },
        text = { Text("Se instalará la nueva versión ahora.") },
        confirmButton = { Button(onClick = onInstall) { Text("Instalar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

// ---- Selectores y menús ----

@Composable
private fun CabinetHeader(
    cabinets: List<Cabinet>,
    activeCabinet: Cabinet?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val version = "v${BuildConfig.VERSION_NAME}"

    if (cabinets.size <= 1 && activeCabinet != null) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = activeCabinet.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = version,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        return
    }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = activeCabinet?.name ?: "Botiquín",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = version,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Cambiar botiquín",
                tint = MaterialTheme.colorScheme.primary
            )
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
                        onSelect(cabinet.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun TransferMenu(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onNewCabinet: () -> Unit,
    onDeleteCabinet: () -> Unit,
    onCheckUpdate: () -> Unit,
    onChangeBackupFolder: () -> Unit,
    onToggleCamera: () -> Unit,
    useCamera: Boolean,
    cabinetCount: Int
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Opciones de botiquín",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Compartir este botiquín") },
                onClick = { expanded = false; onExport() }
            )
            DropdownMenuItem(
                text = { Text("Importar botiquín") },
                onClick = { expanded = false; onImport() }
            )
            DropdownMenuItem(
                text = { Text("Nuevo botiquín") },
                onClick = { expanded = false; onNewCabinet() }
            )
            DropdownMenuItem(
                text = { Text("Buscar actualizaciones") },
                onClick = { expanded = false; onCheckUpdate() }
            )
            DropdownMenuItem(
                text = { Text(if (useCamera) "Cámara: activada" else "Cámara: desactivada") },
                onClick = { expanded = false; onToggleCamera() }
            )
            DropdownMenuItem(
                text = { Text("Carpeta de backups") },
                onClick = { expanded = false; onChangeBackupFolder() }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Eliminar este botiquín",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = { expanded = false; onDeleteCabinet() },
                enabled = cabinetCount > 1
            )
        }
    }
}

// ---- Lista ----

@Composable
private fun ProductList(
    products: List<ProductUiModel>,
    query: String,
    onAddProduct: () -> Unit,
    onProductClick: (ProductUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) {
        EmptyState(query = query, onAddProduct = onAddProduct)
        return
    }

    val grouped = products.groupBy { it.product.category }
        .toList()
        .sortedBy { it.first.order }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 8.dp)
    ) {
        items(grouped) { (category, categoryProducts) ->
            CategorySection(
                category = category,
                products = categoryProducts,
                onProductClick = onProductClick
            )
        }
    }
}

@Composable
private fun CategorySection(
    category: Category,
    products: List<ProductUiModel>,
    onProductClick: (ProductUiModel) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = category.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = products.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            products.forEach { product ->
                ProductCard(
                    product = product,
                    onClick = { onProductClick(product) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    query: String,
    onAddProduct: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Medication,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = if (query.isBlank())
                    stringResource(R.string.empty_no_products)
                else
                    stringResource(R.string.empty_no_results, query),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.empty_add_first),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            FilledTonalButton(
                onClick = onAddProduct,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Escanear código de barras", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Category.icon() = when (this) {
    Category.MEDICINE -> Icons.Filled.Medication
    Category.FIRST_AID -> Icons.Filled.LocalHospital
    Category.TOPICAL -> Icons.Filled.Healing
}

// ---- Helpers de carpeta de backups ----

private fun saveBackupFolder(context: Context, uri: Uri) {
    try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val prefs = context.getSharedPreferences("mibotiquin_prefs", 0)
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