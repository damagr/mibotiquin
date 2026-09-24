package com.mibotiquin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mibotiquin.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity): Int

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM products WHERE cabinetId = :cabinetId")
    suspend fun deleteAllInCabinet(cabinetId: String): Int

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND cabinetId = :cabinetId")
    suspend fun getByBarcodeOnce(barcode: String, cabinetId: String): ProductEntity?

    /** Misma caja física: mismo código, botiquín, caducidad y familia → para sumar cantidad */
    @Query("SELECT * FROM products WHERE barcode = :barcode AND cabinetId = :cabinetId AND expiryDate = :expiryDate AND category = :category AND isNonPerishable = :isNonPerishable LIMIT 1")
    suspend fun findSameEntry(barcode: String, cabinetId: String, expiryDate: Long, category: String, isNonPerishable: Boolean): ProductEntity?

    /** Mismo artículo sin código: mismo nombre, botiquín, caducidad, familia y no-perecedero → para sumar */
    @Query("SELECT * FROM products WHERE name = :name AND cabinetId = :cabinetId AND expiryDate = :expiryDate AND category = :category AND isNonPerishable = :isNonPerishable LIMIT 1")
    suspend fun findSameEntryByName(name: String, cabinetId: String, expiryDate: Long, category: String, isNonPerishable: Boolean): ProductEntity?

    @Query("SELECT * FROM products WHERE cabinetId = :cabinetId ORDER BY category ASC, name ASC")
    fun getAllInCabinet(cabinetId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY category ASC, name ASC")
    fun getAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE cabinetId = :cabinetId ORDER BY category ASC, name ASC")
    suspend fun getAllInCabinetOnce(cabinetId: String): List<ProductEntity>

    /** Lectura one-shot global (todas las familias y botiquines) — para fusionar duplicados */
    @Query("SELECT * FROM products")
    suspend fun getAllOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE cabinetId = :cabinetId AND (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') ORDER BY category ASC, name ASC")
    fun searchInCabinet(query: String, cabinetId: String): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE cabinetId = :cabinetId AND quantity <= 0")
    suspend fun countEmptyInCabinet(cabinetId: String): Int

    @Query("SELECT COUNT(*) FROM products WHERE cabinetId = :cabinetId AND expiryDate < :now")
    suspend fun countExpiredInCabinet(cabinetId: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE cabinetId = :cabinetId AND expiryDate BETWEEN :now AND :soonThreshold")
    suspend fun countExpiringSoonInCabinet(cabinetId: String, now: Long, soonThreshold: Long): Int

    @Query("SELECT MAX(updatedAt) FROM products WHERE cabinetId = :cabinetId")
    suspend fun getCabinetLastUpdate(cabinetId: String): Long?

    /** Renombrar familia: actualiza el displayName en los productos que lo referencian */
    @Query("UPDATE products SET category = :newName, updatedAt = :now WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String, now: Long)
}
