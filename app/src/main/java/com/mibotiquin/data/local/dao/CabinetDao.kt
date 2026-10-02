package com.mibotiquin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mibotiquin.data.local.entity.CabinetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CabinetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cabinet: CabinetEntity)

    @Query("SELECT * FROM cabinets ORDER BY createdAt ASC")
    fun getAll(): Flow<List<CabinetEntity>>

    @Query("SELECT * FROM cabinets WHERE id = :id")
    suspend fun getById(id: String): CabinetEntity?

    /** Buscar botiquín por nombre — regla de fusión de importación por nombre */
    @Query("SELECT * FROM cabinets WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CabinetEntity?

    @Query("DELETE FROM cabinets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE cabinets SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: String, name: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM cabinets")
    suspend fun count(): Int
}
