package com.mibotiquin.presentation.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mibotiquin.data.transfer.CabinetTransferManager
import com.mibotiquin.data.transfer.LocalTransfer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de la transferencia directa (misma red WiFi, NSD + sockets).
 * Scoped al entry de Ajustes: al salir, onCleared detiene todo.
 */
class LocalTransferViewModel(
    private val localTransfer: LocalTransfer,
    private val transferManager: CabinetTransferManager
) : ViewModel() {

    // Dispositivos descubiertos en la red (recompone por callbacks del NSD)
    private val _devices = MutableStateFlow<List<LocalTransfer.DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<LocalTransfer.DiscoveredDevice>> = _devices.asStateFlow()

    // Búsqueda en curso
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    // Escuchando (modo recibir)
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    // Enviando
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    // Resultado de transferencia (toast)
    private val _transferResult = MutableStateFlow<LocalTransfer.TransferResult?>(null)
    val transferResult: StateFlow<LocalTransfer.TransferResult?> = _transferResult.asStateFlow()

    init {
        // Los dispositivos descubiertos llegan por callbacks del LocalTransfer
        localTransfer.setDiscoveryCallbacks(
            onResolved = { device ->
                _devices.value = (_devices.value + device).distinctBy { it.name }
            },
            onLost = { name ->
                _devices.value = _devices.value.filterNot { it.name == name }
            }
        )
    }

    /** Modo recibir: escucha hasta que otro dispositivo envíe */
    fun startReceiving() {
        localTransfer.startReceiving { result ->
            _transferResult.value = result
            // Tras una recepción, la escucha sigue activa (bucle en el servidor)
        }
        _listening.value = true
    }

    fun stopReceiving() {
        localTransfer.stopReceiving()
        _listening.value = false
    }

    /** Modo enviar: descubre dispositivos en la red */
    fun startDiscovery() {
        _devices.value = emptyList()
        _searching.value = true
        localTransfer.startDiscovery { error ->
            _transferResult.value = LocalTransfer.TransferResult.Error(error)
            _searching.value = false
        }
    }

    fun stopDiscovery() {
        localTransfer.stopDiscovery()
        _searching.value = false
    }

    /** Envía el botiquín activo (su JSON) a un dispositivo descubierto */
    fun sendTo(device: LocalTransfer.DiscoveredDevice, cabinetId: String) {
        _sending.value = true
        viewModelScope.launch {
            val json = transferManager.exportJson(cabinetId)
            _transferResult.value =
                if (json == null) LocalTransfer.TransferResult.Error("No se pudo leer el botiquín")
                else localTransfer.sendTo(device, json)
            _sending.value = false
        }
    }

    fun consumeResult() {
        _transferResult.value = null
    }

    override fun onCleared() {
        localTransfer.stopAll()
    }
}
