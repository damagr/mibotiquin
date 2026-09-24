package com.mibotiquin.presentation.ui.screen.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mibotiquin.data.api.CimaApi
import com.mibotiquin.data.api.CimaMedicamento
import com.mibotiquin.data.scan.CnExtractor
import com.mibotiquin.data.scan.Gs1Parser
import com.mibotiquin.di.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanStep {
    ChooseInput,       // sin cámara: elegir introducir CN o artículo manual
    ReadCn,            // cámara para EAN-13 / DataMatrix GTIN→CN
    ManualCn,          // teclado numérico 6 dígitos
    ConsultingCima,    // spinner consultando CIMA
    CnResult,          // resultado CIMA (encontrado o no) + opciones
    ReadDataMatrix     // cámara DataMatrix (caducidad AI 17 → MM/AAAA)
}

class ScannerViewModel(
    private val cimaApi: CimaApi,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val _step = MutableStateFlow(
        if (preferences.useCamera) ScanStep.ReadCn else ScanStep.ChooseInput
    )
    val step: StateFlow<ScanStep> = _step.asStateFlow()

    private val _cn = MutableStateFlow<String?>(null)
    val cn: StateFlow<String?> = _cn.asStateFlow()

    private val _cimaName = MutableStateFlow<String?>(null)
    val cimaName: StateFlow<String?> = _cimaName.asStateFlow()

    /** true cuando el CN existe en CIMA (cámara o manual) */
    private val _cimaFound = MutableStateFlow(false)
    val cimaFound: StateFlow<Boolean> = _cimaFound.asStateFlow()

    private val _dmExpiry = MutableStateFlow<String?>(null)  // "yyyy-MM"
    val dmExpiry: StateFlow<String?> = _dmExpiry.asStateFlow()

    /** Semáforo cuando ya hay caducidad válida (DM o manual) → listo para ir al formulario */
    private val _dmConfirmed = MutableStateFlow(false)
    val dmConfirmed: StateFlow<Boolean> = _dmConfirmed.asStateFlow()

    val useCamera: Boolean
        get() = preferences.useCamera

    /** Volver al escaneo de CN o a la elección inicial (según cámara), desde cualquier etapa intermedia */
    fun onRescan() {
        _step.value = if (useCamera) ScanStep.ReadCn else ScanStep.ChooseInput
    }

    /** Entrar en CN manual */
    fun onManualCnRequested() {
        _step.value = ScanStep.ManualCn
    }

    // ---- Flujo CN ----

    /** Callback único del analyzer: (raw, isDataMatrix) */
    fun onBarcodeScanned(raw: String, isDataMatrix: Boolean) {
        when (_step.value) {
            ScanStep.ReadCn -> {
                if (!isDataMatrix) {
                    CnExtractor.fromEan13(raw)?.let { validateCn(it) }
                } else {
                    Gs1Parser.parse(raw).gtin?.let { CnExtractor.fromGtin(it) }?.let { validateCn(it) }
                }
            }
            ScanStep.ReadDataMatrix -> {
                if (isDataMatrix) {
                    val parsed = Gs1Parser.parse(raw)
                    if (parsed.expiryDate != null) {
                        _dmExpiry.value = "%04d-%02d".format(parsed.expiryDate.year, parsed.expiryDate.monthValue)
                        _dmConfirmed.value = true
                    }
                }
            }
            else -> Unit
        }
    }

    /** Introducir a mano, desde la pantalla manual */
    fun onManualCnEntered(input: String) {
        CnExtractor.fromManual(input)?.let { validateCn(it) }
    }

    private fun validateCn(cn: String) {
        _cn.value = cn
        _step.value = ScanStep.ConsultingCima
        _cimaName.value = null
        _cimaFound.value = false

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { cimaApi.getByCn(cn) }
                val med: CimaMedicamento? = if (response.isSuccessful) response.body() else null

                if (med != null) {
                    _cimaName.value = med.nombre
                    _cimaFound.value = true
                } else {
                    _cimaName.value = null
                    _cimaFound.value = false
                }
            } catch (_: Exception) {
                _cimaName.value = null
                _cimaFound.value = false
            }
            // Siempre avanzar de etapa: CnResult muestra el resultado (encontrado o no)
            _step.value = ScanStep.CnResult
        }
    }

    // ---- Tras resultado CIMA ----

    /** Llamado desde el Screen al tocar "Continuar" tras confirmar el CN */
    fun onContinueAfterCima(): Boolean = if (useCamera) {
        _step.value = ScanStep.ReadDataMatrix
        true
    } else {
        false
    }

    /** CN introducido manualmente → ir a ManualCn (si no se pone ninguna otra cosa) */
    fun onNextAfterCimaFound() {
        _step.value = ScanStep.ReadDataMatrix
    }

    /** El usuario pulsa "Manual" desde el CN encontrado (salta el DM) */
    fun goManualAfterCimaConcept() {
        // la llamada sigue en el Screen: no hacer nada aquí;
        // a nivel de datos/CN con CIMA es perfecto
    }

    /** Confirmar la fecha manual (del picker MM/AAAA) tras fallo/salto del DM */
    fun onManualExpiry(year: Int, month: Int) {
        _dmExpiry.value = "%04d-%02d".format(year, month)
        _dmConfirmed.value = true
    }
}
