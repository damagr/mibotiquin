package com.mibotiquin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(
    tableName = "products",
    indices = [
        // Sin unique: múltiples cajas del mismo medicamento (distintas fechas/familias);
        // la deduplicación (mismo barcode+botiquín+caducidad+familia → sumar) es a nivel de repo
        Index(value = ["barcode", "cabinetId"]),
        Index(value = ["name"]),
        Index(value = ["category"]),
        Index(value = ["expiryDate"]),
        Index(value = ["cabinetId"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val name: String,
    val category: String,  // displayName de la categoría (Medicamentos, custom, etc.)
    val quantity: Int,
    val expiryDate: Long,          // epoch millis UTC
    val cabinetId: String,         // FK lógica hacia cabinets
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)