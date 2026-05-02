package com.example.sarisaristore.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sarisaristore.data.local.model.GCashTransactionStatus
import com.example.sarisaristore.data.local.model.GCashTransactionType

@Entity(tableName = "gcash_transactions")
@Immutable
data class GCashTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: GCashTransactionType,
    val amountCentavos: Long,
    val serviceFeeCentavos: Long,
    val customerName: String? = null,
    val receiptImagePath: String,
    val signatureImagePath: String? = null,
    val note: String? = null,
    val status: GCashTransactionStatus = GCashTransactionStatus.COMPLETED,
    val createdAt: Long,
)
