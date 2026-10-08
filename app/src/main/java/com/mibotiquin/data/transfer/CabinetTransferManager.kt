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
        /** Los datos importados ya existen (idénticos: misma fecha de actualización) */
        data object SameData : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    // Helper: obtener el displayName de una categoría (maneja tanto Medicamentos como CustomCategory)
    private fun getCategoryDisplayName(category: Category): String = when (category) {
        is Category.CustomCategory -> category.displayName
        Category.Medicamentos -> category.displayName
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
                var sameData = 0
                payloads.forEach { payload ->
                    when (val r = applyPayload(payload)) {
                        is ImportResult.Success -> productCount += r.productCount
                        is ImportResult.RejectedOlderLocal -> rejected = true
                        is ImportResult.SameData -> sameData++
                        is ImportResult.Error -> return r
                        else -> Unit
                    }
                }
                when {
                    // Todo el backup ya está en el destino (idéntico)
                    sameData == payloads.size -> ImportResult.SameData
                    rejected && productCount == 0 -> ImportResult.RejectedOlderLocal
                    else -> ImportResult.MultiSuccess(payloads.size, productCount)
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

    /**
     * Backup completo (TODOS los botiquines) a una carpeta SAF con nombre de fichero.
     * Sobrescribe el fichero si ya existe (evita el renombrado automático "(1)").
     */
    suspend fun exportAllToFolder(treeUri: Uri, fileName: String): Boolean = runCatching {
        val folder = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Carpeta de backups no accesible")
        folder.findFile(fileName)?.delete()
        val doc = folder.createFile("application/json", fileName)
            ?: error("No se pudo crear el fichero en la carpeta")
        val json = exportAllJson() ?: error("No se pudo leer el backup")
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("No se pudo escribir el fichero")
    }.isSuccess

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
        // Regla de fusión por NOMBRE: si el botiquín no existe en el destino → nuevo
        val existing = repository.getCabinetByName(payload.name)

        if (existing != null) {
            val localUpdatedAt = repository.getCabinetLastUpdate(existing.id)
            when {
                // La local es más nueva → rechazar
                localUpdatedAt > payload.updatedAt -> return ImportResult.RejectedOlderLocal
                // Idénticos (mismo fichero re-importado) → "Ya tienes estos datos"
                localUpdatedAt == payload.updatedAt -> return ImportResult.SameData
                // El remoto es más nuevo → reemplazo total
                else -> repository.deleteCabinet(existing.id)
            }
        }

        val cabinet = repository.createCabinet(
            payload.name,
            id = existing?.id ?: run {
                // Guard: el ID del payload no debe pisar un botiquín existente con OTRO nombre
                // (REPLACE sobre la PK dejaría productos residuales con dueño equivocado)
                val byId = repository.getCabinetById(payload.cabinetId)
                if (byId != null && byId.name != payload.name) {
                    java.util.UUID.randomUUID().toString()
                } else {
                    payload.cabinetId
                }
            }
        )

        // Familias: recrear las que falten en el destino ANTES de crear los productos
        // (los productos importados conservan su familia originaria; si no existe, se crea)
        val categoryMap = mutableMapOf<String, Category>()
        payload.products.map { it.category }.distinct().forEach { familyName ->
            categoryMap[familyName] = when (familyName) {
                "Medicamentos" -> Category.Medicamentos
                else -> repository.getAllCategories().first()
                    .firstOrNull { it.displayName == familyName }
                    ?: repository.addCustomCategory(familyName)
            }
        }

        payload.products.forEach { tp ->
            repository.addProduct(
                Product(
                    id = 0,
                    barcode = tp.barcode,
                    name = tp.name,
                    category = categoryMap[tp.category] ?: Category.Medicamentos,
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
