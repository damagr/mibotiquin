package com.mibotiquin.domain.repository

import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.Cabinet
import com.mibotiquin.domain.model.Product
import com.mibotiquin.domain.model.AddProductResult
import com.mibotiquin.domain.model.ProductUiModel
import kotlinx.coroutines.flow.Flow

interface ProductRepository {

    // ---- Botiquines ----
    fun getAllCabinets(): Flow<List<Cabinet>>
    suspend fun getCabinetById(id: String): Cabinet?
    suspend fun createCabinet(name: String, id: String? = null): Cabinet
    suspend fun deleteCabinet(id: String)
    suspend fun cabinetCount(): Int
    suspend fun getCabinetLastUpdate(id: String): Long

    // ---- Categorías ----
    fun getAllCategories(): Flow<List<Category>>
    suspend fun addCustomCategory(name: String): Category.CustomCategory
    suspend fun updateCustomCategory(category: Category.CustomCategory)
    suspend fun deleteCustomCategory(id: String)

    // ---- Productos (scoped al botiquín) ----
    fun getProducts(cabinetId: String): Flow<List<ProductUiModel>>
    fun getAllProducts(): Flow<List<ProductUiModel>>  // para notificaciones (todos los botiquines)
    fun searchProducts(cabinetId: String, query: String): Flow<List<ProductUiModel>>
    suspend fun getProductById(id: Long): ProductUiModel?
    suspend fun getProductByBarcode(cabinetId: String, barcode: String): ProductUiModel?
    suspend fun addProduct(product: Product): AddProductResult
    suspend fun updateQuantity(id: Long, quantity: Int): Int
    suspend fun updateExpiryDate(id: Long, expiryDate: Long): Int
    suspend fun deleteProduct(id: Long): Int
    suspend fun getEmptyCount(cabinetId: String): Int
    suspend fun getExpiredCount(cabinetId: String): Int
    suspend fun getExpiringSoonCount(cabinetId: String): Int
}
