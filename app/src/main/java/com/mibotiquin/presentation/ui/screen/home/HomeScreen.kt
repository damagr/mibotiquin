package com.mibotiquin.presentation.ui.screen.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.focusRequester
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
import com.mibotiquin.presentation.ui.components.ProductCard
import com.mibotiquin.presentation.ui.components.SearchBar
import com.mibotiquin.presentation.ui.components.UpdateAvailableDialog
import com.mibotiquin.presentation.ui.components.UpdateDownloadingDialog
import com.mibotiquin.presentation.ui.components.UpdateErrorDialog
import com.mibotiquin.presentation.ui.components.UpdateReadyDialog
import com.mibotiquin.ui.UpdateState

@Composable
fun HomeScreen(
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit,
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
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val useCamera by viewModel.useCamera.collectAsStateWithLifecycle()
    val showProductSheet by viewModel.showProductSheet.collectAsStateWithLifecycle()
    val editingProduct by viewModel.editingProduct.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    // Permiso de notificaciones (API 33+) — launcher FUERA de corrutinas
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Permiso especial "instalar apps desconocidas" (API 26+): al volver de Ajustes, instala
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val st = viewModel.updateState.value
        if (st is UpdateState.ReadyToInstall && !viewModel.needsInstallPermission()) {
            viewModel.installApk(st.file)
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Sin autofoco: el teclado no se abre solo al entrar
    }

    // Diálogos globales (crear/eliminar botiquín + toast de transferencias) viven en AppNavHost,
    // compartidos con Ajustes. Aquí solo: sheet de producto y diálogos de actualización.

    Column(modifier = Modifier.fillMaxSize()) {

        // Header: nombre botiquín + versión + ajustes (insets status bar para notches)
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
            // Versión: elemento separado, a la izquierda del botón de ajustes
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Ajustes",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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

    // Edición de producto (tap en card) → sheet pre-rellenado con borrado confirmado
    if (showProductSheet) {
        editingProduct?.let { product ->
            AddProductSheet(
                barcode = product.product.barcode,
                existing = product,
                onSave = { code, name, category, quantity, expiry ->
                    viewModel.addProduct(code, name, category, quantity, expiry)
                },
                onDelete = {
                    viewModel.onDeleteProduct(product)
                    viewModel.closeProductSheet()
                },
                onDismiss = viewModel::closeProductSheet
            )
        }
    }

    // CN escaneado → buscar en la DB local: si existe, editar; si no, sheet con prefills
    scannedCn?.let { barcode ->
        val existing by viewModel.existingForBarcode.collectAsStateWithLifecycle()

        LaunchedEffect(barcode) {
            viewModel.lookupBarcode(barcode)
        }

        if (showProductSheet) {
            val isNewProduct = existing == null
            AddProductSheet(
                barcode = barcode,
                existing = if (isNewProduct) null else existing,
                prefillName = if (isNewProduct) scannedName else null,
                prefillExpiryYearMonth = if (isNewProduct) scannedExpiry else null,
                onSave = { code, name, category, quantity, expiry ->
                    viewModel.addProduct(code, name, category, quantity, expiry)
                    onScanConsumed()
                },
                onDismiss = {
                    viewModel.closeProductSheet()
                    onScanConsumed()
                }
            )
        }
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
                onInstall = {
                    val file = state.file
                    if (viewModel.needsInstallPermission()) {
                        // Abrir ajustes de "fuentes desconocidas"; al volver, el launcher instala
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                        ).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        installPermissionLauncher.launch(intent)
                    } else {
                        viewModel.installApk(file)
                    }
                },
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
}

// ---- Componentes internos ----

@Composable
private fun CabinetHeader(
    cabinets: List<Cabinet>,
    activeCabinet: Cabinet?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    if (cabinets.size <= 1 && activeCabinet != null) {
        // Un solo botiquín: solo el nombre, sin desplegable ni versión
        Text(
            text = activeCabinet.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = activeCabinet?.name ?: "Botiquín",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
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
