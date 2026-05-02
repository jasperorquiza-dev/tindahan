package com.example.sarisaristore.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.sarisaristore.util.CurrencyUtils
import com.example.sarisaristore.util.DateTimeUtils

@Composable
fun rememberCurrencyText(amountCentavos: Long): String =
    remember(amountCentavos) {
        CurrencyUtils.formatCentavos(amountCentavos)
    }

@Composable
fun rememberTimestampText(timestamp: Long): String =
    remember(timestamp) {
        DateTimeUtils.formatTimestamp(timestamp)
    }

@Composable
fun rememberDateText(timestamp: Long): String =
    remember(timestamp) {
        DateTimeUtils.formatDate(timestamp)
    }

@Composable
fun rememberTimeText(timestamp: Long): String =
    remember(timestamp) {
        DateTimeUtils.formatTime(timestamp)
    }
