package com.example.sarisaristore.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.sarisaristore.data.local.entity.GCashTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GCashTransactionDao {
    @Insert
    suspend fun insertTransaction(transaction: GCashTransactionEntity): Long

    @Query(
        """
        SELECT * FROM gcash_transactions
        WHERE (:start IS NULL OR createdAt >= :start)
        AND (:end IS NULL OR createdAt < :end)
        ORDER BY createdAt DESC
        """,
    )
    fun observeTransactions(start: Long?, end: Long?): Flow<List<GCashTransactionEntity>>

    @Query("SELECT * FROM gcash_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): GCashTransactionEntity?

    @Query("SELECT * FROM gcash_transactions ORDER BY id ASC")
    suspend fun getAllTransactions(): List<GCashTransactionEntity>

    @Update
    suspend fun updateTransaction(transaction: GCashTransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: GCashTransactionEntity)
}
