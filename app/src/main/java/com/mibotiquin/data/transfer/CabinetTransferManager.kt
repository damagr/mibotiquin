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
        val category: String, // displayName de la categoría
        val quantity: Int,
        val expiryDate: Long,
        val isNonPerishable: Boolean = false  // sin caducidad (vendas, cinta, etc.)
    )

    data class TransferPayload(
        val cabinetId: String,
        val name: String,
        val updatedAt: Long,
        val products: List<TransferProduct>
    )

    sealed class ImportResult {
        data class Success(val cabinetId: String, val name: String, val productCount: Int) : ImportResult()
        /** Backup completo (todos los botiquines) restaurado */
        data class MultiSuccess(val cabinetCount: Int, val productCount: Int) : ImportResult()
        data object RejectedOlderLocal : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    // Helper: obtener el displayName de una categoría (maneja tanto Medicamentos como CustomCategory)
    private fun getCategoryDisplayName(category: Category): String = when (category) {
        is Category.CustomCategory -> category.displayName
        Category.Medicamentos -> category.displayName
    }

    // Helper: parsear string de categoría a Category
    private fun parseCategory(categoryName: String): Category {
        // Primero intentar como CustomCategory (buscar en repo no es posible aquí, asumimos Medicamentos por defecto)
        // En el futuro se podría buscar en el repo, pero por ahora Medicamentos por defecto
        return Category.Medicamentos
    }

    // ---- Backup completo (TODOS los botiquines) ----

    /** Exporta todos los botiquines como array de payloads a la carpeta SAF dada. */
    suspend fun exportAllTo(uri: Uri): Pair<Boolean, Int> {
        return runCatching {
            val cabinets = repository.getAllCabinets().first()
            val payloads = cabinets.map { cab ->
                val products = repository.getProducts(cab.id).first()
                TransferPayload(
                    cabinetId = cab.id,
                    name = cab.name,
                    updatedAt = repository.getCabinetLastUpdate(cab.id),
                    products = products.map {
                        TransferProduct(
                            barcode = it.product.barcode,
                            name = it.product.name,
                            category = getCategoryDisplayName(it.product.category),
                            quantity = it.product.quantity,
                            expiryDate = it.product.expiryDate,
                            isNonPerishable = it.product.isNonPerishable
                        )
                    }
                )
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(gson.toJson(payloads).toByteArray(Charsets.UTF_8))
            } ?: error("No se pudo escribir el fichero")
            true to payloads.size
        }.getOrDefault(false to 0)
    }

    /**
     * Importa un fichero detectando el formato automáticamente:
     * - "[" → array de payloads (backup completo: aplica cada botiquín con su regla de fusión)
     * - "{" → payload individual (botiquín)
     */
    suspend fun importAny(uri: Uri): ImportResult {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return ImportResult.Error("No se pudo leer el fichero")

            importJson(json)
        } catch (e: Exception) {
            ImportResult.Error("Formato no válido: ${e.message ?: "desconocido"}")
        }
    }

    /**
     * Importa un JSON string detectando el formato automáticamente:
     * - "[" → array de payloads (backup completo: aplica cada botiquín con su regla de fusión)
     * - "{" → payload individual (botiquín)
     * Reutilizado también por la transferencia directa (NSD/sockets).
     */
    suspend fun importJson(json: String): ImportResult {
        return try {
            val trimmed = json.trimStart()
            if (trimmed.startsWith("[")) {
                val payloads = gson.fromJson(json, Array<TransferPayload>::class.java).toList()
                    ?: return ImportResult.Error("Formato no válido")
                var productCount = 0
                var rejected = false
                payloads.forEach { payload ->
                    when (val r = applyPayload(payload)) {
                        is ImportResult.Success -> productCount += r.productCount
                        is ImportResult.RejectedOlderLocal -> rejected = true
                        is ImportResult.Error -> return r
                        else -> Unit
                    }
                }
                if (rejected && productCount == 0) {
                    ImportResult.RejectedOlderLocal
                } else {
                    ImportResult.MultiSuccess(payloads.size, productCount)
                }
            } else {
                val payload = gson.fromJson(json, TransferPayload::class.java)
                    ?: return ImportResult.Error("Formato no válido")
                applyPayload(payload)
            }
        } catch (e: Exception) {
            ImportResult.Error("Formato no válido: ${e.message ?: "desconocido"}")
        }
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
                    category = getCategoryDisplayName(it.product.category),
                    quantity = it.product.quantity,
                    expiryDate = it.product.expiryDate,
                    isNonPerishable = it.product.isNonPerishable
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
                        category = getCategoryDisplayName(it.product.category),
                        quantity = it.product.quantity,
                        expiryDate = it.product.expiryDate,
                        isNonPerishable = it.product.isNonPerishable
                    )
                }
            )
        )
    }.getOrNull()

    /** Backup completo (TODOS los botiquines) a JSON string — para transferencia directa. */
    suspend fun exportAllJson(): String? = runCatching {
        val cabinets = repository.getAllCabinets().first()
        val payloads = cabinets.map { cab ->
            val products = repository.getProducts(cab.id).first()
            TransferPayload(
                cabinetId = cab.id,
                name = cab.name,
                updatedAt = repository.getCabinetLastUpdate(cab.id),
                products = products.map {
                    TransferProduct(
                        barcode = it.product.barcode,
                        name = it.product.name,
                        category = getCategoryDisplayName(it.product.category),
                        quantity = it.product.quantity,
                        expiryDate = it.product.expiryDate,
                        isNonPerishable = it.product.isNonPerishable
                    )
                }
            )
        }
        gson.toJson(payloads)
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

    // ---- Ficheros temporales compartibles (share sheet) ----

    /** Botiquín activo a fichero temporal en cacheDir — para compartir por el share sheet. */
    suspend fun exportCabinetToShareFile(cabinetId: String, fileName: String): java.io.File? = runCatching {
        val json = exportJson(cabinetId) ?: error("No se pudo leer el botiquín")
        val dir = java.io.File(context.cacheDir, "compartir")
        dir.mkdirs()
        val file = java.io.File(dir, fileName)
        file.writeText(json, Charsets.UTF_8)
        file
    }.getOrNull()

    /** Backup completo (TODOS los botiquines) a fichero temporal — para compartir. */
    suspend fun exportAllToShareFile(fileName: String): Pair<java.io.File?, Int> {
        return runCatching {
            val cabinets = repository.getAllCabinets().first()
            val payloads = cabinets.map { cab ->
                val products = repository.getProducts(cab.id).first()
                TransferPayload(
                    cabinetId = cab.id,
                    name = cab.name,
                    updatedAt = repository.getCabinetLastUpdate(cab.id),
                    products = products.map {
                        TransferProduct(
                            barcode = it.product.barcode,
                            name = it.product.name,
                            category = getCategoryDisplayName(it.product.category),
                            quantity = it.product.quantity,
                            expiryDate = it.product.expiryDate,
                            isNonPerishable = it.product.isNonPerishable
                        )
                    }
                )
            }
            val dir = java.io.File(context.cacheDir, "compartir")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.writeText(gson.toJson(payloads), Charsets.UTF_8)
            file to payloads.size
        }.getOrDefault(null to 0)
    }

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
                    category = parseCategory(tp.category),
                    quantity = tp.quantity,
                    expiryDate = tp.expiryDate,
                    cabinetId = cabinet.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = payload.updatedAt,
                    isNonPerishable = tp.isNonPerishable
                )
            )
        }
        return ImportResult.Success(payload.cabinetId, payload.name, payload.products.size)
    }
}
