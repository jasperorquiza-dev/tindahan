package com.example.sarisaristore.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sarisaristore.data.local.model.UtangEntryStatus

@Entity(
    tableName = "utang_entries",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["status"]),
        Index(value = ["customerId", "status"]),
    ],
)
@Immutable
data class UtangEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val noteText: String,
    val amountCentavos: Long? = null,
    val status: UtangEntryStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val paidAt: Long? = null,
)
