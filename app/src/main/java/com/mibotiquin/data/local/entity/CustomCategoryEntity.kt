package com.mibotiquin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val order: Int,
    val createdAt: Long = System.currentTimeMillis()
)