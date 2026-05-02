package com.example.sarisaristore.data.repository

import com.example.sarisaristore.camera.ImageStorageManager
import com.example.sarisaristore.data.local.dao.GCashTransactionDao
import com.example.sarisaristore.data.local.entity.GCashTransactionEntity
import com.example.sarisaristore.data.local.model.GCashTransactionStatus
import com.example.sarisaristore.data.local.model.GCashTransactionType
import kotlinx.coroutines.flow.Flow

class GCashRepository(
    private val gcashTransactionDao: GCashTransactionDao,
    private val imageStorageManager: ImageStorageManager,
) {
    fun observeTransactions(range: DateRange): Flow<List<GCashTransactionEntity>> =
        gcashTransactionDao.observeTransactions(range.startInclusive, range.endExclusive)

    suspend fun getTransaction(transactionId: Long): GCashTransactionEntity? =
        gcashTransactionDao.getTransactionById(transactionId)

    suspend fun createTransaction(
        type: GCashTransactionType,
        amountCentavos: Long,
        serviceFeeCentavos: Long,
        customerName: String?,
        receiptImagePath: String,
        signatureImagePath: String?,
        note: String?,
    ) {
        require(amountCentavos > 0) { "Amount must be greater than zero." }
        require(receiptImagePath.isNotBlank()) { "Receipt image is required." }
        gcashTransactionDao.insertTransaction(
            GCashTransactionEntity(
                type = type,
                amountCentavos = amountCentavos,
                serviceFeeCentavos = serviceFeeCentavos.coerceAtLeast(0),
                customerName = customerName?.trim()?.ifBlank { null },
                receiptImagePath = receiptImagePath,
                signatureImagePath = signatureImagePath?.trim()?.ifBlank { null },
                note = note?.trim()?.ifBlank { null },
                status = GCashTransactionStatus.COMPLETED,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateTransaction(
        transactionId: Long,
        type: GCashTransactionType,
        amountCentavos: Long,
        serviceFeeCentavos: Long,
        customerName: String?,
        receiptImagePath: String,
        signatureImagePath: String?,
        note: String?,
    ) {
        require(amountCentavos > 0) { "Amount must be greater than zero." }
        require(receiptImagePath.isNotBlank()) { "Receipt image is required." }
        val current = gcashTransactionDao.getTransactionById(transactionId)
            ?: error("Transaction not found.")
        val previousReceiptImagePath = current.receiptImagePath
        val previousSignatureImagePath = current.signatureImagePath
        if (previousReceiptImagePath != receiptImagePath) {
            imageStorageManager.deleteImage(current.receiptImagePath)
        }
        if (previousSignatureImagePath != signatureImagePath) {
            imageStorageManager.deleteImage(current.signatureImagePath)
        }
        gcashTransactionDao.updateTransaction(
            current.copy(
                type = type,
                amountCentavos = amountCentavos,
                serviceFeeCentavos = serviceFeeCentavos.coerceAtLeast(0),
                customerName = customerName?.trim()?.ifBlank { null },
                receiptImagePath = receiptImagePath,
                signatureImagePath = signatureImagePath?.trim()?.ifBlank { null },
                note = note?.trim()?.ifBlank { null },
            ),
        )
    }

    suspend fun deleteTransaction(transactionId: Long) {
        val current = gcashTransactionDao.getTransactionById(transactionId) ?: return
        gcashTransactionDao.deleteTransaction(current)
        imageStorageManager.deleteImage(current.receiptImagePath)
        imageStorageManager.deleteImage(current.signatureImagePath)
    }
}
