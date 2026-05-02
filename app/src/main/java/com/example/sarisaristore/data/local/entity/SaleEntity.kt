package com.example.sarisaristore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sarisaristore.data.local.model.PaymentMethod

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long? = null,
    val productNameSnapshot: String,
    val quantity: Int,
    val unitPriceCentavos: Long,
    val totalAmountCentavos: Long,
    val paymentMethod: PaymentMethod,
    val note: String? = null,
    val createdAt: Long,
)
