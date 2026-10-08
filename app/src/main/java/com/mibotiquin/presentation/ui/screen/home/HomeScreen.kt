package com.mibotiquin.presentation.ui.screen.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun HomeScreen(
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    scannedCn: String? = null,
    scannedName: String? = null,
    scannedExpiry: String? = null,
    scannedProspectoUrl: String? = null,
    onScanConsumed: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cabinets by viewModel.cabinets.collectAsStateWithLifecycle()
    val activeCabinet by viewModel.activeCabinet.collectAsStateWithLifecycle()
    val useCamera by viewModel.useCamera.collectAsStateWithLifecycle()
    val showProductSheet by viewModel.showProductSheet.collectAsStateWithLifecycle()
    val editingProduct by viewModel.editingProduct.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    // Permiso de notificaciones (API 33+) — launcher FUERA de corrutinas
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
        // Sin autofoco: el teclado no se abre solo al entrar
    }

    // Diálogos globales (crear/eliminar botiquín + toast de transferencias + actualización)
    // viven en AppNavHost, compartidos con Ajustes. Aquí solo: sheet de producto.

    // Status bar height fijo (24dp estándar Android) en lugar de detección automática
    val statusBarPadding = 24.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header: nombre botiquín + versión + ajustes (insets status bar para notches)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarPadding, start = 16.dp, end = 16.dp, bottom = 8.dp),
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
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Añadir producto",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Chips de filtro (con contadores) — combinables con la búsqueda
            // Chip "Agotados" = lista de la compra · "Por caducar"/"Caducados" = estado de caducidad
            val upcoming by viewModel.upcomingCount.collectAsStateWithLifecycle()
            val expired by viewModel.expiredCount.collectAsStateWithLifecycle()
            val empty by viewModel.emptyCount.collectAsStateWithLifecycle()
            val activeFilter by viewModel.filter.collectAsStateWithLifecycle()
            val activeSort by viewModel.sort.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeFilter == ProductFilter.ALL,
                    onClick = { viewModel.setFilter(ProductFilter.ALL) },
                    label = { Text("Todos") }
                )
                FilterChip(
                    selected = activeFilter == ProductFilter.UPCOMING,
                    onClick = { viewModel.setFilter(ProductFilter.UPCOMING) },
                    label = { Text("Por caducar $upcoming") }
                )
                FilterChip(
                    selected = activeFilter == ProductFilter.EXPIRED,
                    onClick = { viewModel.setFilter(ProductFilter.EXPIRED) },
                    label = { Text("Caducados $expired") }
                )
                FilterChip(
                    selected = activeFilter == ProductFilter.EMPTY,
                    onClick = { viewModel.setFilter(ProductFilter.EMPTY) },
                    label = { Text("Agotados $empty") }
                )
            }

            // Ordenación (Opción B): texto con el criterio activo + desplegable
            var sortMenuOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ordenar:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    TextButton(onClick = { sortMenuOpen = true }) {
                        Text(
                            if (activeSort == ProductSort.FAMILY) "Familia" else "Caducidad"
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Familia") },
                            onClick = {
                                viewModel.setSort(ProductSort.FAMILY)
                                sortMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Caducidad") },
                            onClick = {
                                viewModel.setSort(ProductSort.EXPIRY)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }

            ProductList(
                products = products,
                query = query,
                filter = activeFilter,
                sort = activeSort,
                onAddProduct = onOpenScanner,
                onProductClick = viewModel::openEditProduct,
                onSetQuantity = { p, q -> viewModel.onQuantityChange(p, q) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            )
        }
    }

    // CN escaneado → siempre sheet en modo NUEVO con prefills:
    // la deduplicación al guardar decide (suma si misma caja física, nueva entrada si distinta fecha/familia)
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    scannedCn?.let { barcode ->
        LaunchedEffect(barcode) {
            viewModel.lookupBarcode(barcode)
        }

        if (showProductSheet && editingProduct == null) {
            AddProductSheet(
                barcode = barcode,
                existing = null,
                prefillName = scannedName,
                prefillExpiryYearMonth = scannedExpiry,
                prefillCn = barcode,
                prefillCimaName = scannedName,
                prefillProspectoUrl = scannedProspectoUrl,
                categories = allCategories,
                onSave = { code, name, category, quantity, expiry, isNonPerishable ->
                    viewModel.addProduct(code, name, category, quantity, expiry, isNonPerishable)
                    onScanConsumed()
                },
                onSaveAndContinue = { code, name, category, quantity, expiry, isNonPerishable ->
                    // Guarda y deja el formulario abierto para el siguiente (vuelta de la farmacia)
                    viewModel.addProduct(
                        code, name, category, quantity, expiry, isNonPerishable, keepOpen = true
                    )
                },
                onAddFamily = viewModel::addCustomCategoryQuick,
                onDelete = null,
                onDismiss = {
                    viewModel.closeProductSheet()
                    onScanConsumed()
                }
            )
        }
    }

    // Editar producto desde la tarjeta de la lista (sin escaneo previo)
    val editProspectoUrl by viewModel.editProspectoUrl.collectAsStateWithLifecycle()
    editingProduct?.let { product ->
        if (showProductSheet) {
            AddProductSheet(
                barcode = product.product.barcode,
                existing = product,
                prefillName = null,
                prefillExpiryYearMonth = null,
                prefillCn = null,
                prefillCimaName = null,
                prefillProspectoUrl = editProspectoUrl,
                categories = allCategories,
                onSave = { code, name, category, quantity, expiry, isNonPerishable ->
                    viewModel.addProduct(code, name, category, quantity, expiry, isNonPerishable)
                },
                onAddFamily = viewModel::addCustomCategoryQuick,
                onShareProduct = { viewModel.shareProductDirect(product) },
                onDelete = {
                    viewModel.onDeleteProduct(product)
                    viewModel.closeProductSheet()
                },
                onDismiss = {
                    viewModel.closeProductSheet()
                }
            )
        }
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
    filter: ProductFilter,
    sort: ProductSort,
    onAddProduct: () -> Unit,
    onProductClick: (ProductUiModel) -> Unit,
    onSetQuantity: (ProductUiModel, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) {
        EmptyState(query = query, filter = filter, onAddProduct = onAddProduct)
        return
    }

    // Orden por caducidad → lista plana (lo que antes expira primero).
    // Orden por familia → agrupada por familia (comportamiento de siempre).
    if (sort == ProductSort.EXPIRY) {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp)
        ) {
            items(products, key = { it.product.id }) { product ->
                ProductCard(
                    product = product,
                    onClick = { onProductClick(product) },
                    onSetQuantity = { newQty -> onSetQuantity(product, newQty) }
                )
            }
        }
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
                onProductClick = onProductClick,
                onSetQuantity = onSetQuantity
            )
        }
    }
}

@Composable
private fun CategorySection(
    category: Category,
    products: List<ProductUiModel>,
    onProductClick: (ProductUiModel) -> Unit,
    onSetQuantity: (ProductUiModel, Int) -> Unit
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
                    onClick = { onProductClick(product) },
                    onSetQuantity = { newQty -> onSetQuantity(product, newQty) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    query: String,
    filter: ProductFilter,
    onAddProduct: () -> Unit
) {
    // Mensaje según el filtro activo (los filtros de estado no sugieren "añadir")
    val filteredMessage: String? = when (filter) {
        ProductFilter.UPCOMING -> "No hay productos próximos a caducar"
        ProductFilter.EXPIRED -> "No hay productos caducados"
        ProductFilter.EMPTY -> "No hay productos agotados"
        ProductFilter.ALL -> null
    }

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
                text = filteredMessage
                    ?: if (query.isBlank())
                        stringResource(R.string.empty_no_products)
                    else
                        stringResource(R.string.empty_no_results, query),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (filteredMessage == null) {
                Text(
                    text = stringResource(R.string.empty_add_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                androidx.compose.material3.FilledTonalButton(
                    onClick = onAddProduct,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "Escanear código de barras", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Category.icon(): ImageVector = when (this) {
    is Category.CustomCategory -> Icons.Filled.Medication
    Category.Medicamentos -> Icons.Filled.Medication
}

// ---- Helpers ----

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
