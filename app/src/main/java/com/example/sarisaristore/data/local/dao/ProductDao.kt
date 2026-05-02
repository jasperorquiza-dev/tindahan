package com.example.sarisaristore.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.sarisaristore.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query(
        """
        SELECT * FROM products
        WHERE (:query = '' OR name LIKE '%' || :query || '%' COLLATE NOCASE)
        AND (:category IS NULL OR category = :category)
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeProducts(query: String, category: String?): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Insert
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query(
        """
        SELECT DISTINCT category FROM products
        WHERE category IS NOT NULL AND TRIM(category) != ''
        ORDER BY category COLLATE NOCASE ASC
        """,
    )
    fun observeCategories(): Flow<List<String>>
}
