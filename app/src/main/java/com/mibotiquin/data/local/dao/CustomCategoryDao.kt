package com.mibotiquin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mibotiquin.data.local.entity.CustomCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY `order` ASC")
    fun getAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories WHERE id = :id")
    fun getById(id: String): Flow<CustomCategoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CustomCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CustomCategoryEntity>)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()

    @Query("SELECT MAX(`order`) FROM custom_categories")
    fun getMaxOrder(): Int?
}