package com.example.sarisaristore.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyUtils {
    private val philippineCurrencyFormat = object : ThreadLocal<NumberFormat>() {
        override fun initialValue(): NumberFormat =
            NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
                currency = Currency.getInstance("PHP")
                maximumFractionDigits = 2
                minimumFractionDigits = 2
            }
    }

    fun formatCentavos(amountCentavos: Long): String =
        philippineCurrencyFormat.get()!!.format(BigDecimal(amountCentavos).movePointLeft(2))

    fun parseToCentavos(rawValue: String): Long? {
        val sanitized = rawValue.trim().replace(",", "")
        if (sanitized.isBlank()) {
            return null
        }
        return runCatching {
            BigDecimal(sanitized)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }
}
