package com.example.sarisaristore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.sarisaristore.data.local.entity.ProductActivityLogEntity

@Dao
interface ProductActivityLogDao {
    @Insert
    suspend fun insertLog(log: ProductActivityLogEntity): Long

    @Query("SELECT * FROM product_activity_logs ORDER BY id ASC")
    suspend fun getAllLogs(): List<ProductActivityLogEntity>
}
