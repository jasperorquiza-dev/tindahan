package com.example.sarisaristore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.sarisaristore.data.local.entity.CustomerEntity
import com.example.sarisaristore.data.local.entity.UtangEntryEntity
import com.example.sarisaristore.data.local.model.UtangCustomerSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface UtangDao {
    @Query(
        """
        SELECT
            customer.id AS id,
            customer.name AS name,
            customer.createdAt AS createdAt,
            customer.updatedAt AS updatedAt,
            COALESCE(SUM(CASE WHEN entry.status = 'UNPAID' THEN 1 ELSE 0 END), 0) AS activeUnpaidCount
        FROM utang_customers AS customer
        LEFT JOIN utang_entries AS entry ON entry.customerId = customer.id
        WHERE (:query = '' OR customer.name LIKE '%' || :query || '%' COLLATE NOCASE)
        GROUP BY customer.id, customer.name, customer.createdAt, customer.updatedAt
        ORDER BY customer.name COLLATE NOCASE ASC
        """,
    )
    fun observeCustomerSummaries(query: String): Flow<List<UtangCustomerSummary>>

    @Query("SELECT * FROM utang_customers WHERE id = :customerId LIMIT 1")
    fun observeCustomer(customerId: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM utang_customers WHERE id = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: Long): CustomerEntity?

    @Query("SELECT * FROM utang_customers ORDER BY id ASC")
    suspend fun getAllCustomers(): List<CustomerEntity>

    @Insert
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("DELETE FROM utang_customers WHERE id = :customerId")
    suspend fun deleteCustomerById(customerId: Long)

    @Query("UPDATE utang_customers SET updatedAt = :updatedAt WHERE id = :customerId")
    suspend fun updateCustomerTimestamp(customerId: Long, updatedAt: Long)

    @Query(
        """
        SELECT * FROM utang_entries
        WHERE customerId = :customerId AND status = 'UNPAID'
        ORDER BY createdAt DESC, id DESC
        """,
    )
    fun observeActiveEntries(customerId: Long): Flow<List<UtangEntryEntity>>

    @Query(
        """
        SELECT * FROM utang_entries
        WHERE customerId = :customerId AND status = 'PAID'
        ORDER BY COALESCE(paidAt, updatedAt) DESC, createdAt DESC, id DESC
        """,
    )
    fun observePaidEntries(customerId: Long): Flow<List<UtangEntryEntity>>

    @Query("SELECT * FROM utang_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: Long): UtangEntryEntity?

    @Query("SELECT * FROM utang_entries ORDER BY id ASC")
    suspend fun getAllEntries(): List<UtangEntryEntity>

    @Insert
    suspend fun insertEntry(entry: UtangEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: UtangEntryEntity)
}
