package com.mibotiquin.domain.usecase

import com.mibotiquin.domain.model.Cabinet
import com.mibotiquin.domain.model.Product
import com.mibotiquin.domain.model.AddProductResult
import com.mibotiquin.domain.model.ProductUiModel
import com.mibotiquin.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// ---- Botiquines ----

class GetCabinetsUseCase(private val repository: ProductRepository) {
    operator fun invoke(): Flow<List<Cabinet>> = repository.getAllCabinets()
}

class CreateCabinetUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(name: String): Cabinet = repository.createCabinet(name)
}

class DeleteCabinetUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(id: String) = repository.deleteCabinet(id)
}

class GetCabinetByIdUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(id: String): Cabinet? = repository.getCabinetById(id)
}

class CabinetCountUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(): Int = repository.cabinetCount()
}

// ---- Productos ----

class GetProductsUseCase(private val repository: ProductRepository) {
    operator fun invoke(cabinetId: String): Flow<List<ProductUiModel>> =
        repository.getProducts(cabinetId)
}

class SearchProductsUseCase(private val repository: ProductRepository) {
    operator fun invoke(cabinetId: String, query: String): Flow<List<ProductUiModel>> =
        repository.searchProducts(cabinetId, query)
}

class GetProductByIdUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(id: Long): ProductUiModel? = repository.getProductById(id)
}

class GetProductByBarcodeUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(cabinetId: String, barcode: String): ProductUiModel? =
        repository.getProductByBarcode(cabinetId, barcode)
}

class AddProductUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(product: Product): AddProductResult = repository.addProduct(product)
}

class UpdateProductQuantityUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(id: Long, quantity: Int): Int =
        repository.updateQuantity(id, quantity)
}

class UpdateProductExpiryDateUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(id: Long, expiryDate: Long): Int =
        repository.updateExpiryDate(id, expiryDate)
}

class DeleteProductUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(id: Long): Int = repository.deleteProduct(id)
}

class GetEmptyCountUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(cabinetId: String): Int = repository.getEmptyCount(cabinetId)
}

class GetExpiredCountUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(cabinetId: String): Int = repository.getExpiredCount(cabinetId)
}

class GetExpiringSoonCountUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(cabinetId: String): Int = repository.getExpiringSoonCount(cabinetId)
}

// ---- CIMA (prospecto) ----

/** URL del prospecto (docs tipo 2, urlHtml) para un CN — null si no existe o sin conexión */
class GetProspectoUrlUseCase(private val api: com.mibotiquin.data.api.CimaApi) {
    suspend operator fun invoke(cn: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = api.getByCn(cn)
            response.body()?.docs
                ?.firstOrNull { it.tipo == 2 }
                ?.let { it.urlHtml ?: it.url }
        } catch (_: Exception) {
            null
        }
    }
}
