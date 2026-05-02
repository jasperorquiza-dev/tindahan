package com.example.sarisaristore.data.repository

import com.example.sarisaristore.data.local.dao.ProductActivityLogDao
import com.example.sarisaristore.data.local.entity.ProductActivityLogEntity
import com.example.sarisaristore.data.local.model.ProductActivityType

class ProductActivityRepository(
    private val productActivityLogDao: ProductActivityLogDao,
) {
    suspend fun log(
        productId: Long?,
        productNameSnapshot: String,
        activityType: ProductActivityType,
        quantityDelta: Int? = null,
        note: String? = null,
        createdAt: Long,
    ) {
        productActivityLogDao.insertLog(
            ProductActivityLogEntity(
                productId = productId,
                productNameSnapshot = productNameSnapshot,
                activityType = activityType,
                quantityDelta = quantityDelta,
                note = note,
                createdAt = createdAt,
            ),
        )
    }
}
