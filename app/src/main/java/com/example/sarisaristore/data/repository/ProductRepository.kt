package com.example.sarisaristore.data.repository

import com.example.sarisaristore.camera.ImageStorageManager
import com.example.sarisaristore.data.local.dao.ProductDao
import com.example.sarisaristore.data.local.entity.ProductEntity
import com.example.sarisaristore.data.local.model.ProductActivityType
import kotlinx.coroutines.flow.Flow

class ProductRepository(
    private val productDao: ProductDao,
    private val productActivityRepository: ProductActivityRepository,
    private val imageStorageManager: ImageStorageManager,
) {
    fun observeProducts(query: String, category: String?): Flow<List<ProductEntity>> =
        productDao.observeProducts(query.trim(), category?.takeIf { it.isNotBlank() })

    fun observeCategories(): Flow<List<String>> = productDao.observeCategories()

    suspend fun getProduct(productId: Long): ProductEntity? = productDao.getProductById(productId)

    suspend fun addProduct(
        name: String,
        imagePath: String?,
        priceCentavos: Long,
        stockQuantity: Int = 1,
        category: String?,
    ): Long {
        val cleanName = name.trim()
        val timestamp = System.currentTimeMillis()
        val productId = productDao.insertProduct(
            ProductEntity(
                name = cleanName,
                imagePath = imagePath,
                priceCentavos = priceCentavos,
                stockQuantity = stockQuantity,
                category = category?.trim()?.ifBlank { null },
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        productActivityRepository.log(
            productId = productId,
            productNameSnapshot = cleanName,
            activityType = ProductActivityType.CREATED,
            createdAt = timestamp,
        )
        return productId
    }

    suspend fun updateProduct(
        productId: Long,
        name: String,
        imagePath: String?,
        priceCentavos: Long,
        stockQuantity: Int = 1,
        category: String?,
    ) {
        val current = productDao.getProductById(productId) ?: return
        val timestamp = System.currentTimeMillis()
        val previousImagePath = current.imagePath
        if (!previousImagePath.isNullOrBlank() && previousImagePath != imagePath) {
            imageStorageManager.deleteImage(current.imagePath)
        }
        val updated = current.copy(
            name = name.trim(),
            imagePath = imagePath,
            priceCentavos = priceCentavos,
            stockQuantity = stockQuantity,
            category = category?.trim()?.ifBlank { null },
            updatedAt = timestamp,
        )
        productDao.updateProduct(updated)
        productActivityRepository.log(
            productId = productId,
            productNameSnapshot = updated.name,
            activityType = ProductActivityType.UPDATED,
            createdAt = timestamp,
        )
    }

    suspend fun deleteProduct(productId: Long) {
        val current = productDao.getProductById(productId) ?: return
        val timestamp = System.currentTimeMillis()
        productActivityRepository.log(
            productId = current.id,
            productNameSnapshot = current.name,
            activityType = ProductActivityType.DELETED,
            createdAt = timestamp,
        )
        productDao.deleteProduct(current)
        imageStorageManager.deleteImage(current.imagePath)
    }
}
