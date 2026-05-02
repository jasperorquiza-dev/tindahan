package com.example.sarisaristore.data.local.model

import androidx.compose.runtime.Immutable

@Immutable
data class UtangCustomerSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val activeUnpaidCount: Int,
)
