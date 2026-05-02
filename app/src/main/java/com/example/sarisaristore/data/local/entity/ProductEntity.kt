package com.example.sarisaristore.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
@Immutable
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imagePath: String? = null,
    val priceCentavos: Long,
    val stockQuantity: Int,
    val category: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
