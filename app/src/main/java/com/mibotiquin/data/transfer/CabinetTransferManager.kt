package com.mibotiquin.data.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.Product
import com.mibotiquin.domain.repository.ProductRepository
import kotlinx.coroutines.flow.first

/**
 * Transferencia de botiquines entre dispositivos.
 * Regla de fusión: prevalece el botiquín con la fecha de actualización más reciente (reemplazo total).
 */
class CabinetTransferManager(
    private val context: Context,
    private val repository: ProductRepository
) {
    private val gson = Gson()

    data class TransferProduct(
        val barcode: String,
        val name: String,
        val category: String,
        val quantity: Int,
        val expiryDate: Long
    )

    data class TransferPayload(
        val cabinetId: String,
        val name: String,
        val updatedAt: Long,
        val products: List<TransferProduct>
    )

    sealed class ImportResult {
        data class Success(val cabinetId: String, val name: String, val productCount: Int) : ImportResult()
        data object RejectedOlderLocal : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    // ---- Exportar ----

    suspend fun exportCabinet(cabinetId: String, uri: Uri): Boolean = runCatching {
        val cabinet = repository.getCabinetById(cabinetId) ?: error("Botiquín no encontrado")
        val products = repository.getProducts(cabinetId).first()
        val payload = TransferPayload(
            cabinetId = cabinet.id,
            name = cabinet.name,
            updatedAt = repository.getCabinetLastUpdate(cabinetId),
            products = products.map {
                TransferProduct(
                    barcode = it.product.barcode,
                    name = it.product.name,
                    category = it.product.category.name,
                    quantity = it.product.quantity,
                    expiryDate = it.product.expiryDate
                )
            }
        )
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        } ?: error("No se pudo escribir el fichero")
    }.isSuccess

    suspend fun exportJson(cabinetId: String): String? = runCatching {
        val cabinet = repository.getCabinetById(cabinetId) ?: return null
        val products = repository.getProducts(cabinetId).first()
        gson.toJson(
            TransferPayload(
                cabinetId = cabinet.id,
                name = cabinet.name,
                updatedAt = repository.getCabinetLastUpdate(cabinetId),
                products = products.map {
                    TransferProduct(
                        barcode = it.product.barcode,
                        name = it.product.name,
                        category = it.product.category.name,
                        quantity = it.product.quantity,
                        expiryDate = it.product.expiryDate
                    )
                }
            )
        )
    }.getOrNull()

    /** Exporta a una carpeta SAF (árbol elegido por el usuario) usando DocumentFile. */
    suspend fun exportCabinetToFolder(
        cabinetId: String,
        treeUri: Uri,
        fileName: String
    ): Boolean = runCatching {
        val folder = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Carpeta de backups no accesible")
        val doc = folder.createFile("application/json", fileName)
            ?: error("No se pudo crear el fichero en la carpeta")
        val json = exportJson(cabinetId) ?: error("No se pudo leer el botiquín")
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("No se pudo escribir el fichero")
    }.isSuccess

    // ---- Importar ----

    suspend fun importCabinet(uri: Uri): ImportResult {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return ImportResult.Error("No se pudo leer el fichero")

            val payload = gson.fromJson(json, TransferPayload::class.java)
                ?: return ImportResult.Error("Formato no válido")

            applyPayload(payload)
        } catch (e: Exception) {
            ImportResult.Error("Formato no válido: ${e.message ?: "desconocido"}")
        }
    }

    suspend fun applyPayload(payload: TransferPayload): ImportResult {
        val existing = repository.getCabinetById(payload.cabinetId)

        if (existing != null) {
            val localUpdatedAt = repository.getCabinetLastUpdate(payload.cabinetId)
            if (localUpdatedAt >= payload.updatedAt) {
                return ImportResult.RejectedOlderLocal
            }
            // El remoto es más nuevo → reemplazo total (mismo id)
            repository.deleteCabinet(payload.cabinetId)
        }

        val cabinet = repository.createCabinet(payload.name, id = payload.cabinetId)
        payload.products.forEach { tp ->
            repository.addProduct(
                Product(
                    id = 0,
                    barcode = tp.barcode,
                    name = tp.name,
                    category = runCatching { Category.valueOf(tp.category) }
                        .getOrDefault(Category.MEDICINE),
                    quantity = tp.quantity,
                    expiryDate = tp.expiryDate,
                    cabinetId = cabinet.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = payload.updatedAt
                )
            )
        }
        return ImportResult.Success(payload.cabinetId, payload.name, payload.products.size)
    }
}
