package com.example.sarisaristore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sarisaristore.data.local.model.ProductActivityType

@Entity(tableName = "product_activity_logs")
data class ProductActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long? = null,
    val productNameSnapshot: String,
    val activityType: ProductActivityType,
    val quantityDelta: Int? = null,
    val note: String? = null,
    val createdAt: Long,
)
