package com.mibotiquin.presentation.ui.screen.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mibotiquin.data.api.CimaMedicamento
import com.mibotiquin.data.api.CimaApi
import com.mibotiquin.data.scan.CnExtractor
import com.mibotiquin.data.scan.Gs1Parser
import com.mibotiquin.di.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Pasos del flujo de escaneo: CN → CIMA → DataMatrix → formulario */
sealed class ScanStep {
    /** Leyendo el CN con la cámara */
    object ReadCn : ScanStep()

    /** Introducir CN a mano (teclado numérico, 6 dígitos) */
    object ManualCn : ScanStep()

    /** Consultando la API de CIMA */
    object ConsultingCima : ScanStep()

    /** CN no encontrado en CIMA → introducir nombre + fecha a mano */
    object CnNotFound : ScanStep()

    /** Escaneando DataMatrix para caducidad/lote */
    object ReadDataMatrix : ScanStep()
}

sealed class CimaLookupResult {
    data class Success(val medicamento: CimaMedicamento) : CimaLookupResult()
    data object NotFound : CimaLookupResult()
    data class Error(val message: String) : CimaLookupResult()
}

class ScannerViewModel(
    private val cimaApi: CimaApi,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val _step = MutableStateFlow<ScanStep>(ScanStep.ReadCn)
    val step: StateFlow<ScanStep> = _step.asStateFlow()

    private val _cn = MutableStateFlow<String?>(null)
    val cn: StateFlow<String?> = _cn.asStateFlow()

    private val _cimaResult = MutableStateFlow<CimaLookupResult?>(null)
    val cimaResult: StateFlow<CimaLookupResult?> = _cimaResult.asStateFlow()

    // Datos del DataMatrix (caducidad como YearMonth "yyyy-MM" y lote)
    private val _dataMatrixExpiry = MutableStateFlow<String?>(null) // "yyyy-MM"
    val dataMatrixExpiry: StateFlow<String?> = _dataMatrixExpiry.asStateFlow()

    private val _dataMatrixBatch = MutableStateFlow<String?>(null)
    val dataMatrixBatch: StateFlow<String?> = _dataMatrixBatch.asStateFlow()

    val useCamera: Boolean
        get() = preferences.useCamera

    init {
        // Sin cámara: el flujo empieza directamente en el CN manual
        if (!useCamera) _step.value = ScanStep.ManualCn
    }

    fun reset() {
        _step.value = ScanStep.ReadCn
        _cn.value = null
        _cimaResult.value = null
        _dataMatrixExpiry.value = null
        _dataMatrixBatch.value = null
    }

    // ---- Paso 1: CN ----

    /** Resultado de la cámara: EAN-13 / DataMatrix AI 01 → extraer CN */
    fun onBarcodeScanned(raw: String, isDataMatrix: Boolean) {
        if (!useCamera) return
        val cn = if (isDataMatrix) {
            val parsed = Gs1Parser.parse(raw)
            parsed.gtin?.let { CnExtractor.fromGtin(it) }
        } else {
            CnExtractor.fromEan13(raw)
        }
        if (cn != null) {
            onCnConfirmed(cn)
        }
        // Si no se pudo extraer CN, seguimos escaneando (el usuario puede pasar a manual)
    }

    /** CN confirmado (escaneado o introducido a mano) → consultar CIMA */
    fun onCnConfirmed(cn: String) {
        _cn.value = cn
        _step.value = ScanStep.ConsultingCima
        lookupCima(cn)
    }

    fun onManualCnRequested() {
        _step.value = ScanStep.ManualCn
    }

    fun onManualCnCancelled() {
        _step.value = ScanStep.ReadCn
    }

    private fun lookupCima(cn: String) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    cimaApi.getByCn(cn)
                }
                if (response.isSuccessful) {
                    val medicamento = response.body()
                    if (medicamento != null) {
                        _cimaResult.value = CimaLookupResult.Success(medicamento)
                        // CN encontrado → siguiente paso: DataMatrix opcional
                        _step.value = ScanStep.ReadDataMatrix
                    } else {
                        _cimaResult.value = CimaLookupResult.NotFound
                        _step.value = ScanStep.CnNotFound
                    }
                } else {
                    _cimaResult.value = CimaLookupResult.NotFound
                    _step.value = ScanStep.CnNotFound
                }
            } catch (e: Exception) {
                _cimaResult.value = CimaLookupResult.Error(
                    e.message ?: "Sin conexión con CIMA"
                )
                _step.value = ScanStep.CnNotFound
            }
        }
    }

    // ---- Paso 2: DataMatrix (opcional) ----

    /** DataMatrix escaneado → extraer caducidad (AI 17 → MM/AAAA) y lote (AI 10) */
    fun onDataMatrixScanned(raw: String) {
        if (!useCamera) return
        val parsed = Gs1Parser.parse(raw)
        parsed.expiryDate?.let { date ->
            _dataMatrixExpiry.value = "%04d-%02d".format(date.year, date.monthValue)
        }
        parsed.batchNumber?.let { _dataMatrixBatch.value = it }
    }

    fun skipDataMatrix() {
        // El formulario se abre con fecha vacía (manual)
        _dataMatrixExpiry.value = null
    }
}
