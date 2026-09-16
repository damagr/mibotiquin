package com.mibotiquin.presentation.ui.screen.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mibotiquin.BuildConfig
import com.mibotiquin.data.transfer.CabinetTransferManager
import com.mibotiquin.data.update.UpdateChecker
import com.mibotiquin.di.PreferencesManager
import com.mibotiquin.domain.model.Cabinet
import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.Product
import com.mibotiquin.domain.model.ProductUiModel
import com.mibotiquin.domain.usecase.*
import com.mibotiquin.ui.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val getCabinetsUseCase: GetCabinetsUseCase,
    private val createCabinetUseCase: CreateCabinetUseCase,
    private val deleteCabinetUseCase: DeleteCabinetUseCase,
    private val getProductsUseCase: GetProductsUseCase,
    private val searchProductsUseCase: SearchProductsUseCase,
    private val updateProductQuantityUseCase: UpdateProductQuantityUseCase,
    private val updateProductExpiryDateUseCase: UpdateProductExpiryDateUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
    private val addProductUseCase: AddProductUseCase,
    private val getProductByBarcodeUseCase: GetProductByBarcodeUseCase,
    private val transferManager: CabinetTransferManager,
    private val preferences: PreferencesManager,
    private val updateChecker: UpdateChecker,
    private val context: Context
) : ViewModel() {

    // ---- Botiquines ----

    val cabinets: StateFlow<List<Cabinet>> = getCabinetsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeCabinet = MutableStateFlow<Cabinet?>(null)
    val activeCabinet: StateFlow<Cabinet?> = _activeCabinet.asStateFlow()

    // UI: diálogo de creación / borrado / confirmación de importación
    private val _showCreateCabinet = MutableStateFlow(false)
    val showCreateCabinet: StateFlow<Boolean> = _showCreateCabinet.asStateFlow()

    private val _cabinetToDelete = MutableStateFlow<Cabinet?>(null)
    val cabinetToDelete: StateFlow<Cabinet?> = _cabinetToDelete.asStateFlow()

    private val _transferEvent = MutableStateFlow<String?>(null)
    val transferEvent: StateFlow<String?> = _transferEvent.asStateFlow()

    // Botiquín importado que colisiona con uno local más nuevo
    private val _hasNewerRemote = MutableStateFlow(false)
    val hasNewerRemote: StateFlow<Boolean> = _hasNewerRemote.asStateFlow()

    // ---- Auto-update ----

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.UpToDate)
    val updateState: StateFlow<UpdateState> = _updateState

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _updateState
        .map { it is UpdateState.Available }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var _downloadProgress = 0
    var downloadProgress: Int
        get() = _downloadProgress
        private set(value) {
            _downloadProgress = value
        }

    init {
        // Resolver botiquín activo al arrancar
        viewModelScope.launch {
            var cabinetsLoaded = false
            cabinets.collect { list ->
                val current = _activeCabinet.value
                val storedId = preferences.activeCabinetId
                when {
                    // Primera emisión: Room aún no ha cargado, no decidir todavía
                    !cabinetsLoaded -> {
                        cabinetsLoaded = true
                        if (list.isEmpty()) {
                            _activeCabinet.value = null
                            _showCreateCabinet.value = true
                        }
                        // si hay items, el resto del when resuelve el activo
                        if (list.isNotEmpty()) {
                            resolveActive(current, storedId, list)
                        }
                    }
                    current?.id in list.map { it.id } -> Unit // ya activo
                    else -> resolveActive(current, storedId, list)
                }
                // Si hay botiquines, el diálogo de creación nunca debe estar abierto
                if (list.isNotEmpty()) _showCreateCabinet.value = false
            }
        }

        // Comprobar actualizaciones al arrancar (silencioso si está al día)
        checkForUpdate(userInitiated = false)
    }

    private fun resolveActive(current: Cabinet?, storedId: String?, list: List<Cabinet>) {
        when {
            storedId != null && list.any { it.id == storedId } ->
                _activeCabinet.value = list.first { it.id == storedId }
            current != null && list.any { it.id == current.id } ->
                _activeCabinet.value = current
            else -> {
                _activeCabinet.value = list.first()
                preferences.activeCabinetId = list.first().id
            }
        }
    }

    fun selectCabinet(id: String) {
        val cabinet = cabinets.value.firstOrNull { it.id == id } ?: return
        _activeCabinet.value = cabinet
        preferences.activeCabinetId = cabinet.id
    }

    fun onShowCreateCabinet(show: Boolean) {
        _showCreateCabinet.value = show
    }

    fun createCabinet(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val cabinet = createCabinetUseCase(name.trim())
            _activeCabinet.value = cabinet
            preferences.activeCabinetId = cabinet.id
            _showCreateCabinet.value = false
        }
    }

    fun onRequestDeleteCabinet(cabinet: Cabinet) {
        _cabinetToDelete.value = cabinet
    }

    fun onDismissDeleteCabinet() {
        _cabinetToDelete.value = null
    }

    fun deleteCabinet(id: String) {
        viewModelScope.launch {
            deleteCabinetUseCase(id)
            // Si era el activo, el colector de init se encargará de activar otro
            if (cabinets.value.size <= 1) {
                preferences.activeCabinetId = null
                _showCreateCabinet.value = true
            }
            _cabinetToDelete.value = null
        }
    }

    fun shareCabinet(uri: android.net.Uri) {
        val cabinet = _activeCabinet.value ?: return
        viewModelScope.launch {
            val ok = transferManager.exportCabinet(cabinet.id, uri)
            _transferEvent.value = if (ok) "Botiquín exportado" else "Error al exportar"
        }
    }

    fun importCabinet(uri: android.net.Uri) {
        viewModelScope.launch {
            when (val result = transferManager.importCabinet(uri)) {
                is CabinetTransferManager.ImportResult.Success -> {
                    selectCabinet(result.cabinetId)
                    _transferEvent.value =
                        "Botiquín '${result.name}' importado (${result.productCount} productos)"
                }
                is CabinetTransferManager.ImportResult.RejectedOlderLocal -> {
                    _hasNewerRemote.value = true
                    _transferEvent.value = "Importación rechazada: tu versión local es más reciente"
                }
                is CabinetTransferManager.ImportResult.Error -> {
                    _transferEvent.value = result.message
                }
            }
        }
    }

    fun onTransferEventShown() {
        _transferEvent.value = null
        _hasNewerRemote.value = false
    }

    // ---- Auto-update ----

    /** Comprueba si hay una nueva versión en GitHub */
    fun checkForUpdate(userInitiated: Boolean = false) {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                }
                if (release != null) {
                    _updateState.value = UpdateState.Available(release)
                } else {
                    _updateState.value = UpdateState.UpToDate
                    if (userInitiated) {
                        _transferEvent.value = "Ya tienes la última versión (v${BuildConfig.VERSION_NAME})"
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.UpToDate // falla silenciosamente
                if (userInitiated) {
                    _transferEvent.value = "No se pudo comprobar la actualización"
                }
            }
        }
    }

    /** Inicia la descarga de la actualización (con backup previo) */
    fun startUpdate(release: com.mibotiquin.data.api.GitHubRelease) {
        _updateState.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            try {
                // 1. Backup automático en la carpeta del usuario
                val activeId = _activeCabinet.value?.id ?: return@launch
                val backupFolderUri = preferences.backupFolderUri
                val backupUri = backupFolderUri?.let { Uri.parse(it) }
                    ?: return@launch

                val timestamp = System.currentTimeMillis()
                val backupFileName = "backup_${BuildConfig.VERSION_NAME}_$timestamp.json"
                val backupUriWithName = backupUri.buildUpon().appendPath(backupFileName).build()

                val backupOk = transferManager.exportCabinet(activeId, backupUriWithName)
                if (!backupOk) {
                    _updateState.value = UpdateState.Error("Error al crear backup de seguridad")
                    return@launch
                }

                // 2. Descargar APK
                val apkUrl = release.assets.firstOrNull()?.browserDownloadUrl
                    ?: return@launch

                _updateState.value = UpdateState.Downloading(0)

                val file = withContext(Dispatchers.IO) {
                    updateChecker.downloadApk(apkUrl) { progress ->
                        _downloadProgress = progress
                        _updateState.value = UpdateState.Downloading(progress)
                    }
                }

                _updateState.value = UpdateState.ReadyToInstall(file)

            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Error al descargar actualización")
            }
        }
    }

    /** Instala el APK descargado */
    fun installApk(file: java.io.File) {
        val activity = context as? Activity ?: return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        activity.startActivity(intent)
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateState.UpToDate
    }

    fun dismissUpdateError() {
        _updateState.value = UpdateState.UpToDate
    }

    fun needsInstallPermission(): Boolean = context.packageManager.canRequestPackageInstalls().not()

    // ---- Búsqueda / productos ----

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val products: StateFlow<List<com.mibotiquin.domain.model.ProductUiModel>> =
        combine(_searchQuery, _activeCabinet) { query, cabinet -> query to cabinet }
            .flatMapLatest { (query, cabinet) ->
                when {
                    cabinet == null -> flowOf(emptyList())
                    query.isBlank() -> getProductsUseCase(cabinet.id)
                    else -> searchProductsUseCase(cabinet.id, query)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query.trim()
    }

    fun onQuantityChange(product: com.mibotiquin.domain.model.ProductUiModel, newQuantity: Int) {
        viewModelScope.launch {
            updateProductQuantityUseCase(product.product.id, newQuantity.coerceAtLeast(0))
        }
    }

    fun onExpiryDateChange(product: com.mibotiquin.domain.model.ProductUiModel, newExpiryDate: Long) {
        viewModelScope.launch {
            updateProductExpiryDateUseCase(product.product.id, newExpiryDate)
        }
    }

    fun onDeleteProduct(product: com.mibotiquin.domain.model.ProductUiModel) {
        viewModelScope.launch {
            deleteProductUseCase(product.product.id)
        }
    }

    fun addProduct(
        barcode: String,
        name: String,
        category: Category,
        quantity: Int,
        expiryDate: Long
    ) {
        val cabinet = _activeCabinet.value ?: return
        viewModelScope.launch {
            val existing = _existingForBarcode.value?.product ?: _editingProduct.value?.product
            try {
                addProductUseCase(
                    Product(
                        id = existing?.id ?: 0, // si existe, REPLACE mantiene el id
                        barcode = barcode,
                        name = name,
                        category = category,
                        quantity = quantity,
                        expiryDate = expiryDate,
                        cabinetId = cabinet.id,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                _transferEvent.value = "Error al guardar el producto"
            }
            _existingForBarcode.value = null
            _editingProduct.value = null
        }
    }

    // ---- Edición de producto (tap en card) ----

    private val _editingProduct = MutableStateFlow<com.mibotiquin.domain.model.ProductUiModel?>(null)
    val editingProduct: StateFlow<com.mibotiquin.domain.model.ProductUiModel?> = _editingProduct

    fun openEditProduct(product: com.mibotiquin.domain.model.ProductUiModel) {
        _editingProduct.value = product
    }

    fun closeEditProduct() {
        _editingProduct.value = null
    }

    // Re-escaneo: producto existente pre-rellena el sheet
    private val _existingForBarcode = MutableStateFlow<com.mibotiquin.domain.model.ProductUiModel?>(null)
    val existingForBarcode: StateFlow<com.mibotiquin.domain.model.ProductUiModel?> = _existingForBarcode

    fun lookupBarcode(barcode: String) {
        val cabinet = _activeCabinet.value ?: return
        viewModelScope.launch {
            _existingForBarcode.value = getProductByBarcodeUseCase(cabinet.id, barcode)
        }
    }

    fun clearLookup() {
        _existingForBarcode.value = null
    }
}