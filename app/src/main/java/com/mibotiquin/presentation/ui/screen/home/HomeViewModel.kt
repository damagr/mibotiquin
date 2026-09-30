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
import com.mibotiquin.domain.repository.ProductRepository
import com.mibotiquin.domain.usecase.*
import com.mibotiquin.ui.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val getProspectoUrlUseCase: GetProspectoUrlUseCase,
    private val context: Context,
    private val productRepository: ProductRepository
) : ViewModel() {

    // ---- Configuración ----

    private val _useCamera = MutableStateFlow(preferences.useCamera)
    val useCamera: StateFlow<Boolean> = _useCamera.asStateFlow()

    fun onToggleCamera() {
        val next = !_useCamera.value
        preferences.useCamera = next
        _useCamera.value = next
    }

    // ---- Botiquines ----

    val cabinets: StateFlow<List<Cabinet>> = getCabinetsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeCabinet = MutableStateFlow<Cabinet?>(null)
    val activeCabinet: StateFlow<Cabinet?> = _activeCabinet.asStateFlow()

    private val _showCreateCabinet = MutableStateFlow(false)
    val showCreateCabinet: StateFlow<Boolean> = _showCreateCabinet.asStateFlow()

    private val _cabinetToDelete = MutableStateFlow<Cabinet?>(null)
    val cabinetToDelete: StateFlow<Cabinet?> = _cabinetToDelete.asStateFlow()

    private val _transferEvent = MutableStateFlow<String?>(null)
    val transferEvent: StateFlow<String?> = _transferEvent

    // Toast messages para mostrar en la UI
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    init {
        // Al arrancar (VM de scope Activity, una vez por sesión):
        // - con botiquines → restaura el activo persistido (o el primero)
        // - sin ninguno → pide crear el primero (tras setup o tras borrar el último)
        viewModelScope.launch {
            val list = getCabinetsUseCase().first()
            if (list.isEmpty()) {
                _showCreateCabinet.value = true
            } else {
                val saved = preferences.activeCabinetId
                _activeCabinet.value = list.firstOrNull { it.id == saved } ?: list.first()
                preferences.activeCabinetId = _activeCabinet.value?.id
            }
            // Comprobación automática de actualizaciones (silenciosa, userInitiated=false)
            checkForUpdate(userInitiated = false)
        }
    }

    // ---- Categorías ----

    val allCategories = productRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Category.predefined)

    // Botiquín importado con versión local más nueva
    private val _hasNewerRemote = MutableStateFlow(false)
    val hasNewerRemote: StateFlow<Boolean> = _hasNewerRemote

    // ---- Auto-update ----

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.UpToDate)
    val updateState: StateFlow<UpdateState> = _updateState

    /** Job de la actualización en curso (para poder cancelarla) */
    private var updateJob: kotlinx.coroutines.Job? = null

    val showUpdateDialog: StateFlow<Boolean> = _updateState
        .map { it is UpdateState.Available }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ---- Sheet de añadir/editar producto ----

    private val _showProductSheet = MutableStateFlow(false)
    val showProductSheet: StateFlow<Boolean> = _showProductSheet

    private val _editingProduct = MutableStateFlow<ProductUiModel?>(null)
    val editingProduct: StateFlow<ProductUiModel?> = _editingProduct

    // Prospecto para la ficha de edición (lookup CIMA por código al abrir)
    private val _editProspectoUrl = MutableStateFlow<String?>(null)
    val editProspectoUrl: StateFlow<String?> = _editProspectoUrl.asStateFlow()

    fun openEditProduct(product: ProductUiModel) {
        _editingProduct.value = product
        _editProspectoUrl.value = null
        _showProductSheet.value = true
        // Lookup CIMA por CN → chip Prospecto disponible al revisar un artículo ya guardado
        val barcode = product.product.barcode
        if (barcode.isNotBlank()) {
            viewModelScope.launch {
                _editProspectoUrl.value = getProspectoUrlUseCase(barcode)
            }
        }
    }

    fun closeProductSheet() {
        _showProductSheet.value = false
        _editingProduct.value = null
        _editProspectoUrl.value = null
    }

    // ---- Búsqueda ----

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val products: StateFlow<List<ProductUiModel>> =
        combine(_searchQuery, _activeCabinet) { query, cabinet -> query to cabinet }
            .flatMapLatest { (query, cabinet) ->
                when {
                    cabinet == null -> flowOf(emptyList())
                    query.isBlank() -> getProductsUseCase(cabinet.id)
                    else -> searchProductsUseCase(cabinet.id, query)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) { _searchQuery.value = query.trim() }

    fun onQuantityChange(product: ProductUiModel, newQuantity: Int) {
        viewModelScope.launch { updateProductQuantityUseCase(product.product.id, newQuantity.coerceAtLeast(0)) }
    }

    fun onExpiryDateChange(product: ProductUiModel, newExpiryDate: Long) {
        viewModelScope.launch { updateProductExpiryDateUseCase(product.product.id, newExpiryDate) }
    }

    fun onDeleteProduct(product: ProductUiModel) {
        viewModelScope.launch { deleteProductUseCase(product.product.id) }
    }

    fun addProduct(
        barcode: String, name: String, category: Category, quantity: Int, expiryDate: Long,
        isNonPerishable: Boolean = false
    ) {
        val cabinet = _activeCabinet.value ?: return
        viewModelScope.launch {
            val existing = _editingProduct.value?.product
            try {
                val result = addProductUseCase(
                    Product(
                        id = existing?.id ?: 0,
                        barcode = barcode, name = name, category = category,
                        quantity = quantity, expiryDate = expiryDate,
                        cabinetId = cabinet.id,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        isNonPerishable = isNonPerishable
                    )
                )
                if (result.merged) {
                    _transferEvent.value =
                        "Ya tenías este producto: +$quantity → total ${result.newTotal}"
                }
            } catch (e: Exception) {
                _transferEvent.value = "Error al guardar el producto"
            }
            closeProductSheet()
        }
    }

    // ---- Búsqueda por código de barras (re-escaneo) ----

    fun lookupBarcode(barcode: String) {
        // Re-escaneo: siempre modo nuevo — la deduplicación al guardar decide
        // (suma si misma caja física, nueva entrada si distinta fecha/familia)
        _showProductSheet.value = true
    }

    // ---- Botiquín CRUD ----

    fun selectCabinet(id: String) {
        val cabinet = cabinets.value.firstOrNull { it.id == id } ?: return
        _activeCabinet.value = cabinet
        preferences.activeCabinetId = cabinet.id
    }

    fun onShowCreateCabinet(show: Boolean) { _showCreateCabinet.value = show }

    fun createCabinet(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val cabinet = createCabinetUseCase(name.trim())
            _activeCabinet.value = cabinet
            preferences.activeCabinetId = cabinet.id
            _showCreateCabinet.value = false
        }
    }

    fun onRequestDeleteCabinet(cabinet: Cabinet) { _cabinetToDelete.value = cabinet }
    fun onDismissDeleteCabinet() { _cabinetToDelete.value = null }

    fun deleteCabinet(id: String) {
        viewModelScope.launch {
            deleteCabinetUseCase(id)
            // Consulta fresca: el StateFlow cabinets puede ir desactualizado justo tras el delete
            val remaining = getCabinetsUseCase().first()
            if (remaining.isEmpty()) {
                _activeCabinet.value = null
                preferences.activeCabinetId = null
                _showCreateCabinet.value = true
            } else if (_activeCabinet.value?.id == id) {
                // El eliminado era el activo → pasa al primero restante
                _activeCabinet.value = remaining.first()
                preferences.activeCabinetId = remaining.first().id
            }
            _transferEvent.value = "Botiquín eliminado"
            _cabinetToDelete.value = null
        }
    }

    fun shareCabinet(uri: Uri) {
        val cabinet = _activeCabinet.value ?: return
        viewModelScope.launch {
            val ok = transferManager.exportCabinet(cabinet.id, uri)
            _transferEvent.value = if (ok) "Botiquín exportado" else "Error al exportar"
        }
    }

    /** Compartir botiquín por el share sheet del sistema (WhatsApp, Bluetooth, Quick Share…) */
    fun shareCabinetDirect() {
        val cabinet = _activeCabinet.value ?: return
        viewModelScope.launch {
            val file = transferManager.exportCabinetToShareFile(
                cabinet.id, "botiquin_${cabinet.name.trim()}.json"
            )
            if (file == null) {
                _transferEvent.value = "Error al compartir"
                return@launch
            }
            launchShareSheet(file, "Compartir botiquín")
        }
    }

    /** Compartir backup completo por el share sheet del sistema */
    fun shareBackupDirect() {
        viewModelScope.launch {
            val (file, count) = transferManager.exportAllToShareFile(
                "mibotiquin_backup_${System.currentTimeMillis()}.json"
            )
            if (file == null) {
                _transferEvent.value = "Error al compartir backup"
                return@launch
            }
            launchShareSheet(file, "Compartir backup ($count botiquines)")
        }
    }

    private fun launchShareSheet(file: java.io.File, title: String) {
        val shareUri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            // ClipData: asegura que el permiso de lectura del URI llega a la app destino
            // a través del chooser (quirk conocido de Android)
            clipData = android.content.ClipData.newRawUri(title, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            // El context del VM es applicationContext: exige este flag para startActivity
            // (mismo patrón que installApk) — sin él, AndroidRuntimeException y crash
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun importCabinet(uri: Uri) {
        viewModelScope.launch {
            when (val result = transferManager.importAny(uri)) {
                is CabinetTransferManager.ImportResult.Success -> {
                    selectCabinet(result.cabinetId)
                    _transferEvent.value = "Botiquín '${result.name}' importado (${result.productCount} productos)"
                }
                is CabinetTransferManager.ImportResult.MultiSuccess -> {
                    _transferEvent.value =
                        "Backup restaurado (${result.cabinetCount} botiquines, ${result.productCount} productos)"
                }
                is CabinetTransferManager.ImportResult.RejectedOlderLocal ->
                    _transferEvent.value = "Importación rechazada: tu versión local es más reciente"
                is CabinetTransferManager.ImportResult.Error ->
                    _transferEvent.value = result.message
            }
        }
    }

    /** Backup completo: TODOS los botiquines a un JSON (vía SAF) */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val (ok, count) = transferManager.exportAllTo(uri)
            _transferEvent.value = if (ok) "Backup exportado ($count botiquines)" else "Error al exportar backup"
        }
    }

    fun onTransferEventShown() { _transferEvent.value = null }

    // ---- Categorías personalizadas ----

    suspend fun addCustomCategory(name: String) {
        try {
            productRepository.addCustomCategory(name)
        } catch (e: Exception) {
            // Sin rethrow: muestra el error y evita crash de la app
            showToast("Error al crear familia: ${e.message}")
        }
    }

    /** Crear familia rápida desde el sheet de producto (callback no-suspend) */
    fun addCustomCategoryQuick(name: String) {
        viewModelScope.launch { addCustomCategory(name) }
    }

    suspend fun renameCustomCategory(id: String, newName: String) {
        try {
            productRepository.renameCustomCategory(id, newName)
        } catch (e: Exception) {
            // Sin rethrow: muestra el error y evita crash de la app
            showToast("Error al renombrar familia: ${e.message}")
        }
    }

    suspend fun deleteCustomCategory(id: String) {
        try {
            productRepository.deleteCustomCategory(id)
        } catch (e: Exception) {
            // Sin rethrow: muestra el error y evita crash de la app
            showToast("Error al eliminar familia: ${e.message}")
        }
    }

    // ---- Auto-update ----

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
                _updateState.value = UpdateState.UpToDate
                if (userInitiated) {
                    _transferEvent.value = "No se pudo comprobar la actualización"
                }
            }
        }
    }

    fun startUpdate(release: com.mibotiquin.data.api.GitHubRelease) {
        _updateState.value = UpdateState.Preparing
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            try {
                val activeId = _activeCabinet.value?.id
                if (activeId == null) {
                    _updateState.value = UpdateState.Error("No hay botiquín activo")
                    return@launch
                }
                val backupFolderUri = preferences.backupFolderUri
                if (backupFolderUri == null) {
                    _updateState.value = UpdateState.Error(
                        "No hay carpeta de backups configurada. Elige una en ⚙ → Carpeta de backups."
                    )
                    return@launch
                }
                val fileName = "mibotiquin_backup_${BuildConfig.VERSION_NAME}_${System.currentTimeMillis()}.json"
                val backupOk = transferManager.exportCabinetToFolder(
                    activeId, Uri.parse(backupFolderUri), fileName
                )
                if (!backupOk) {
                    _updateState.value = UpdateState.Error("No se pudo crear el backup. Revisa la carpeta en ⚙ → Carpeta de backups.")
                    return@launch
                }

                // Sin assets = build de Actions aún en curso o fallido → error (no salida silenciosa)
                val apkUrl = release.assets.firstOrNull()?.browserDownloadUrl
                if (apkUrl == null) {
                    _updateState.value = UpdateState.Error(
                        "El APK aún no está disponible (el build puede seguir en curso). Inténtalo en unos minutos."
                    )
                    return@launch
                }
                val file = withContext(Dispatchers.IO) {
                    updateChecker.downloadApk(apkUrl) { progress ->
                        // Guard: al cancelar, no seguir actualizando el estado
                        if (updateJob?.isActive == true) {
                            _updateState.value = UpdateState.Downloading(progress)
                        }
                    }
                }
                if (updateJob?.isActive == true) {
                    _updateState.value = UpdateState.ReadyToInstall(file)
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Error al descargar actualización")
            }
        }
    }

    /** Cancelar la actualización en curso (backup o descarga) */
    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
        _updateState.value = UpdateState.UpToDate
    }

    fun installApk(file: java.io.File) {
        // Sin cast a Activity: FLAG_ACTIVITY_NEW_TASK permite lanzar desde applicationContext
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun dismissUpdateDialog() { _updateState.value = UpdateState.UpToDate }
    fun dismissUpdateError() { _updateState.value = UpdateState.UpToDate }
    fun needsInstallPermission(): Boolean = context.packageManager.canRequestPackageInstalls().not()
}
