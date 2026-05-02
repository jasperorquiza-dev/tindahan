package com.example.sarisaristore.data.repository

import androidx.room.withTransaction
import com.example.sarisaristore.data.local.AppDatabase
import com.example.sarisaristore.data.local.dao.UtangDao
import com.example.sarisaristore.data.local.entity.CustomerEntity
import com.example.sarisaristore.data.local.entity.UtangEntryEntity
import com.example.sarisaristore.data.local.model.UtangCustomerSummary
import com.example.sarisaristore.data.local.model.UtangEntryStatus
import kotlinx.coroutines.flow.Flow

class UtangRepository(
    private val database: AppDatabase,
    private val utangDao: UtangDao,
) {
    fun observeCustomerSummaries(query: String): Flow<List<UtangCustomerSummary>> =
        utangDao.observeCustomerSummaries(query.trim())

    fun observeCustomer(customerId: Long): Flow<CustomerEntity?> = utangDao.observeCustomer(customerId)

    fun observeActiveEntries(customerId: Long): Flow<List<UtangEntryEntity>> =
        utangDao.observeActiveEntries(customerId)

    fun observePaidEntries(customerId: Long): Flow<List<UtangEntryEntity>> =
        utangDao.observePaidEntries(customerId)

    suspend fun addCustomer(name: String): Long {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Customer name is required." }

        val timestamp = System.currentTimeMillis()
        return utangDao.insertCustomer(
            CustomerEntity(
                name = cleanName,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
    }

    suspend fun updateCustomer(customerId: Long, name: String) {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Customer name is required." }

        val current = utangDao.getCustomerById(customerId)
            ?: error("Customer not found.")
        val timestamp = System.currentTimeMillis()
        utangDao.updateCustomer(
            current.copy(
                name = cleanName,
                updatedAt = timestamp,
            ),
        )
    }

    suspend fun deleteCustomer(customerId: Long) {
        val current = utangDao.getCustomerById(customerId)
            ?: return
        database.withTransaction {
            utangDao.deleteCustomerById(current.id)
        }
    }

    suspend fun addEntry(customerId: Long, noteText: String, amountCentavos: Long?): Long {
        val cleanNote = noteText.trim()
        require(cleanNote.isNotBlank()) { "Utang note is required." }
        require(amountCentavos == null || amountCentavos >= 0) { "Utang price cannot be negative." }

        return database.withTransaction {
            val customer = utangDao.getCustomerById(customerId)
                ?: error("Customer not found.")
            val timestamp = System.currentTimeMillis()
            val entryId = utangDao.insertEntry(
                UtangEntryEntity(
                    customerId = customer.id,
                    noteText = cleanNote,
                    amountCentavos = amountCentavos,
                    status = UtangEntryStatus.UNPAID,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
            utangDao.updateCustomerTimestamp(customer.id, timestamp)
            entryId
        }
    }

    suspend fun updateEntry(entryId: Long, noteText: String, amountCentavos: Long?) {
        val cleanNote = noteText.trim()
        require(cleanNote.isNotBlank()) { "Utang note is required." }
        require(amountCentavos == null || amountCentavos >= 0) { "Utang price cannot be negative." }

        database.withTransaction {
            val current = utangDao.getEntryById(entryId)
                ?: error("Utang entry not found.")
            val timestamp = System.currentTimeMillis()
            utangDao.updateEntry(
                current.copy(
                    noteText = cleanNote,
                    amountCentavos = amountCentavos,
                    updatedAt = timestamp,
                ),
            )
            utangDao.updateCustomerTimestamp(current.customerId, timestamp)
        }
    }

    suspend fun markEntryAsPaid(entryId: Long) {
        database.withTransaction {
            val current = utangDao.getEntryById(entryId)
                ?: error("Utang entry not found.")
            if (current.status == UtangEntryStatus.PAID) {
                return@withTransaction
            }

            val timestamp = System.currentTimeMillis()
            utangDao.updateEntry(
                current.copy(
                    status = UtangEntryStatus.PAID,
                    updatedAt = timestamp,
                    paidAt = timestamp,
                ),
            )
            utangDao.updateCustomerTimestamp(current.customerId, timestamp)
        }
    }

    suspend fun markEntryAsUnpaid(entryId: Long) {
        database.withTransaction {
            val current = utangDao.getEntryById(entryId)
                ?: error("Utang entry not found.")
            if (current.status == UtangEntryStatus.UNPAID) {
                return@withTransaction
            }

            val timestamp = System.currentTimeMillis()
            utangDao.updateEntry(
                current.copy(
                    status = UtangEntryStatus.UNPAID,
                    updatedAt = timestamp,
                    paidAt = null,
                ),
            )
            utangDao.updateCustomerTimestamp(current.customerId, timestamp)
        }
    }
}
