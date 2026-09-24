package com.mibotiquin.data.local.mapper

import com.mibotiquin.data.local.entity.ProductEntity
import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.Product

fun ProductEntity.toDomain(): Product = Product(
    id = id,
    barcode = barcode,
    name = name,
    category = parseCategory(category),
    quantity = quantity,
    expiryDate = expiryDate,
    cabinetId = cabinetId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isNonPerishable = isNonPerishable
)

fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id,
    barcode = barcode,
    name = name,
    category = category.displayName,
    quantity = quantity,
    expiryDate = expiryDate,
    cabinetId = cabinetId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isNonPerishable = isNonPerishable
)

private fun parseCategory(categoryName: String): Category = when (categoryName) {
    "Medicamentos" -> Category.Medicamentos
    else -> Category.CustomCategory(
        id = "", // Will be set by repository when needed
        displayName = categoryName,
        order = 999
    )
}
