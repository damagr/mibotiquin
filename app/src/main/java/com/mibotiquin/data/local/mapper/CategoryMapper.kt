package com.mibotiquin.data.local.mapper

import com.mibotiquin.data.local.entity.CustomCategoryEntity
import com.mibotiquin.domain.model.Category

fun CustomCategoryEntity.toDomain(): Category.CustomCategory = Category.CustomCategory(
    id = id,
    displayName = name,
    order = order
)

fun Category.CustomCategory.toEntity(): com.mibotiquin.data.local.entity.CustomCategoryEntity = 
    com.mibotiquin.data.local.entity.CustomCategoryEntity(
        id = id,
        name = displayName,
        order = order,
        createdAt = System.currentTimeMillis()
    )