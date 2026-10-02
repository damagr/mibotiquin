package com.mibotiquin.data.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Transferencia DIRECTA entre dispositivos en la MISMA red WiFi (NSD/mDNS + sockets).
 * - Sin ficheros, sin nube, sin pairing — sin permisos nuevos (INTERNET ya declarado)
 * - Servidor (receptor): ServerSocket + registro NSD → recibe JSON → importa → ACK
 * - Cliente (emisor): descubrimiento NSD → lista de dispositivos → envía JSON → ACK
 * - Fallback si no comparten red: hotspot de un móvil + conexión del otro (sigue siendo "misma red")
 *
 * Protocolo: el emisor escribe el JSON y cierra la escritura; el receptor lee hasta EOF,
 * importa y responde una línea de ACK ("OK" / "ERR: mensaje").
 */
class LocalTransfer(
    private val context: Context,
    private val transferManager: CabinetTransferManager
) {
    companion object {
        const val SERVICE_TYPE = "_mibotiquin._tcp."
    }

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var serverScope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null

    /** Dispositivo Mi Botiquín descubierto en la red */
    data class DiscoveredDevice(val name: String, val host: InetAddress?, val port: Int)

    sealed class TransferResult {
        data class Sent(val deviceName: String) : TransferResult()
        data class Received(val description: String, val productCount: Int) : TransferResult()
        data object RejectedOlderLocal : TransferResult()
        data class Error(val message: String) : TransferResult()
    }

    // ---- Receptor (servidor) ----

    /**
     * Se pone a la escucha: registra el servicio NSD y acepta conexiones en bucle
     * hasta stopReceiving(). Cada conexión: lee JSON → importa → ACK.
     */
    fun startReceiving(onResult: (TransferResult) -> Unit) {
        stopAll()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverScope = scope
        scope.launch {
            try {
                val socket = ServerSocket(0) // puerto efímero
                serverSocket = socket
                registerNsd(socket.localPort)

                while (isActive) {
                    val client = socket.accept()
                    try {
                        val json = client.getInputStream().bufferedReader(Charsets.UTF_8).use {
                            it.readText()
                        }
                        val result = importAndMap(json)
                        runCatching {
                            client.getOutputStream().write(ackFor(result).toByteArray(Charsets.UTF_8))
                        }
                        withContext(Dispatchers.Main) { onResult(result) }
                    } catch (_: Exception) {
                        // conexión fallida concreta: seguir escuchando
                    } finally {
                        runCatching { client.close() }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        onResult(TransferResult.Error("Error al recibir: ${e.message ?: "desconocido"}"))
                    }
                }
            }
        }
    }

    /** Importa el JSON y mapea el resultado del gestor a TransferResult */
    private suspend fun importAndMap(json: String): TransferResult {
        return when (val r = transferManager.importJson(json)) {
            is CabinetTransferManager.ImportResult.Success ->
                TransferResult.Received("Botiquín '${r.name}'", r.productCount)
            is CabinetTransferManager.ImportResult.MultiSuccess ->
                TransferResult.Received("Backup (${r.cabinetCount} botiquines)", r.productCount)
            is CabinetTransferManager.ImportResult.RejectedOlderLocal ->
                TransferResult.RejectedOlderLocal
            is CabinetTransferManager.ImportResult.SameData ->
                TransferResult.Received("Ya tienes estos datos", 0)
            is CabinetTransferManager.ImportResult.Error ->
                TransferResult.Error(r.message)
        }
    }

    private fun ackFor(result: TransferResult): String = when (result) {
        is TransferResult.Sent, is TransferResult.Received -> "OK"
        TransferResult.RejectedOlderLocal ->
            "ERR:El otro dispositivo tiene una versión más reciente"
        is TransferResult.Error -> "ERR:${result.message}"
    }

    /** Detiene la escucha, cierra el socket y anula el registro NSD */
    fun stopReceiving() {
        serverScope?.cancel()
        serverScope = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        registrationListener?.let { listener ->
            runCatching { nsdManager()?.unregisterService(listener) }
        }
        registrationListener = null
    }

    private fun registerNsd(port: Int) {
        val manager = nsdManager()
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Mi Botiquín · ${Build.MODEL}".take(63)
            setServiceType(SERVICE_TYPE)
            setPort(port)
        }
        runCatching { manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    // ---- Emisor (cliente) ----

    /** Callbacks del descubrimiento (inyectados por el ViewModel) */
    private var onDeviceResolvedCallback: ((DiscoveredDevice) -> Unit)? = null
    private var onDeviceLostCallback: ((String) -> Unit)? = null

    fun setDiscoveryCallbacks(
        onResolved: (DiscoveredDevice) -> Unit,
        onLost: (String) -> Unit
    ) {
        onDeviceResolvedCallback = onResolved
        onDeviceLostCallback = onLost
    }

    /** Inicia el descubrimiento NSD; los dispositivos se entregan por callbacks */
    fun startDiscovery(onError: (String) -> Unit = {}) {
        stopDiscovery()
        val manager = nsdManager()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onError("No se pudo iniciar la búsqueda de dispositivos")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Resolver para obtener host+puerto
                val resolver = object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) = onDeviceResolved(info)
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                }
                runCatching { manager.resolveService(serviceInfo, resolver) }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onDeviceLost(serviceInfo.serviceName)
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { onError("No se pudo buscar dispositivos") }
    }

    /** Detiene el descubrimiento NSD */
    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager()?.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
    }

    /** Envía el botiquín (JSON) a un dispositivo y espera el ACK */
    suspend fun sendTo(device: DiscoveredDevice, json: String): TransferResult = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                val host = device.host ?: return@use TransferResult.Error("Host no disponible")
                socket.connect(InetSocketAddress(host, device.port), 10_000)
                socket.getOutputStream().apply {
                    write(json.toByteArray(Charsets.UTF_8))
                    flush()
                }
                socket.shutdownOutput()
                val ack = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
                if (ack == "OK") TransferResult.Sent(device.name)
                else TransferResult.Error(ack.removePrefix("ERR:"))
            }
        } catch (e: Exception) {
            TransferResult.Error("No se pudo conectar: ${e.message ?: "desconocido"}")
        }
    }

    // ---- Estado de descubrimiento (para la UI) ----

    private val _discovered = mutableListOf<DiscoveredDevice>()
    val discovered: List<DiscoveredDevice> get() = _discovered.toList()

    private fun onDeviceResolved(info: NsdServiceInfo) {
        val device = DiscoveredDevice(
            name = info.serviceName,
            host = info.host,
            port = info.port
        )
        if (_discovered.none { it.name == device.name }) {
            _discovered.add(device)
            onDeviceResolvedCallback?.invoke(device)
        }
    }

    private fun onDeviceLost(name: String) {
        _discovered.removeAll { it.name == name }
        onDeviceLostCallback?.invoke(name)
    }

    /** Para TODO (llamar en onCleared del ViewModel) */
    fun stopAll() {
        stopDiscovery()
        stopReceiving()
    }

    private fun nsdManager(): NsdManager =
        nsdManager ?: (context.getSystemService(Context.NSD_SERVICE) as NsdManager).also { nsdManager = it }
}
