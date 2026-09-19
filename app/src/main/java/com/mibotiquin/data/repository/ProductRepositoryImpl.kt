package com.mibotiquin.data.repository

import com.mibotiquin.data.local.dao.CabinetDao
import com.mibotiquin.data.local.dao.CustomCategoryDao
import com.mibotiquin.data.local.dao.ProductDao
import com.mibotiquin.data.local.entity.CabinetEntity
import com.mibotiquin.data.local.mapper.toDomain
import com.mibotiquin.data.local.mapper.toEntity
import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.Cabinet
import com.mibotiquin.domain.model.Product
import com.mibotiquin.domain.model.ProductUiModel
import com.mibotiquin.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ProductRepositoryImpl(
    private val productDao: ProductDao,
    private val cabinetDao: CabinetDao,
    private val customCategoryDao: CustomCategoryDao
) : ProductRepository {

    // ---- Botiquines ----

    override fun getAllCabinets(): Flow<List<Cabinet>> =
        cabinetDao.getAll().map { entities ->
            entities.map { Cabinet(it.id, it.name, it.updatedAt, it.createdAt) }
        }

    override suspend fun getCabinetById(id: String): Cabinet? =
        cabinetDao.getById(id)?.let { Cabinet(it.id, it.name, it.updatedAt, it.createdAt) }

    override suspend fun createCabinet(name: String, id: String?): Cabinet {
        val entity = CabinetEntity(
            id = id ?: java.util.UUID.randomUUID().toString(),
            name = name.trim()
        )
        cabinetDao.insert(entity)
        return Cabinet(entity.id, entity.name, entity.updatedAt, entity.createdAt)
    }

    override suspend fun deleteCabinet(id: String) {
        productDao.deleteAllInCabinet(id)
        cabinetDao.deleteById(id)
    }

    override suspend fun cabinetCount(): Int = cabinetDao.count()

    override suspend fun getCabinetLastUpdate(id: String): Long =
        productDao.getCabinetLastUpdate(id) ?: 0L

    // ---- Categorías ----

    override fun getAllCategories(): Flow<List<com.mibotiquin.domain.model.Category>> =
        customCategoryDao.getAll().map { customEntities ->
            val customCategories = customEntities.map { it.toDomain() }
            // Predefinida "Medicamentos" siempre primera (order = 0)
            com.mibotiquin.domain.model.Category.predefined + customCategories.sortedBy { it.order }
        }

    override suspend fun addCustomCategory(name: String): Category.CustomCategory {
        val maxOrder = customCategoryDao.getMaxOrder() ?: -1
        val newOrder = maxOrder + 1
        val id = java.util.UUID.randomUUID().toString()
        val entity = com.mibotiquin.data.local.entity.CustomCategoryEntity(
            id = id,
            name = name.trim(),
            order = newOrder
        )
        customCategoryDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun updateCustomCategory(category: Category.CustomCategory) {
        val entity = com.mibotiquin.data.local.entity.CustomCategoryEntity(
            id = category.id,
            name = category.displayName,
            order = category.order
        )
        customCategoryDao.insert(entity)
    }

    override suspend fun deleteCustomCategory(id: String) {
        customCategoryDao.deleteById(id)
    }

    // ---- Productos ----

    override fun getProducts(cabinetId: String): Flow<List<ProductUiModel>> =
        productDao.getAllInCabinet(cabinetId).map { it.map { e -> e.toDomain().toUiModel() } }

    override fun getAllProducts(): Flow<List<ProductUiModel>> =
        productDao.getAll().map { it.map { e -> e.toDomain().toUiModel() } }

    override fun searchProducts(cabinetId: String, query: String): Flow<List<ProductUiModel>> =
        if (query.isBlank()) getProducts(cabinetId)
        else productDao.searchInCabinet(query.trim(), cabinetId)
            .map { it.map { e -> e.toDomain().toUiModel() } }

    override suspend fun getProductById(id: Long): ProductUiModel? =
        productDao.getByIdOnce(id)?.toDomain()?.toUiModel()

    override suspend fun getProductByBarcode(cabinetId: String, barcode: String): ProductUiModel? =
        productDao.getByBarcodeOnce(barcode, cabinetId)?.toDomain()?.toUiModel()

    override suspend fun addProduct(product: Product): Long =
        productDao.insert(product.toEntity())

    override suspend fun updateQuantity(id: Long, quantity: Int): Int =
        productDao.getByIdOnce(id)?.let { entity ->
            productDao.update(
                entity.copy(quantity = quantity, updatedAt = System.currentTimeMillis())
            )
        } ?: 0

    override suspend fun updateExpiryDate(id: Long, expiryDate: Long): Int =
        productDao.getByIdOnce(id)?.let { entity ->
            productDao.update(
                entity.copy(expiryDate = expiryDate, updatedAt = System.currentTimeMillis())
            )
        } ?: 0

    override suspend fun deleteProduct(id: Long): Int = productDao.deleteById(id)

    override suspend fun getEmptyCount(cabinetId: String): Int =
        productDao.countEmptyInCabinet(cabinetId)

    override suspend fun getExpiredCount(cabinetId: String): Int =
        productDao.countExpiredInCabinet(cabinetId, System.currentTimeMillis())

    override suspend fun getExpiringSoonCount(cabinetId: String): Int {
        val now = System.currentTimeMillis()
        val threshold = now + 30L * 24 * 60 * 60 * 1000
        return productDao.countExpiringSoonInCabinet(cabinetId, now, threshold)
    }
}

private fun Product.toUiModel(): ProductUiModel = ProductUiModel(product = this)
