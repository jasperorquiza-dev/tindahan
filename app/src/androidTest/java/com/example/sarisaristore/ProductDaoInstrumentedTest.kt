package com.example.sarisaristore

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sarisaristore.data.local.AppDatabase
import com.example.sarisaristore.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductDaoInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchAndLowStockQueriesWork() = runBlocking {
        val productDao = database.productDao()
        val timestamp = System.currentTimeMillis()
        productDao.insertProduct(
            ProductEntity(
                name = "Sardines",
                priceCentavos = 2_500,
                stockQuantity = 3,
                category = "Canned Goods",
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        productDao.insertProduct(
            ProductEntity(
                name = "Soap",
                priceCentavos = 1_000,
                stockQuantity = 10,
                category = "Hygiene",
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )

        val results = productDao.observeProducts("sar", null).first()

        assertEquals(1, results.size)
        assertEquals("Sardines", results.first().name)
    }
}
