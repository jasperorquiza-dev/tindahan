package com.example.sarisaristore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.sarisaristore.data.local.entity.SaleEntity

@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(sale: SaleEntity): Long

    @Query("SELECT * FROM sales ORDER BY id ASC")
    suspend fun getAllSales(): List<SaleEntity>
}
