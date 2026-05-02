package com.example.sarisaristore.data.local.model

enum class PaymentMethod {
    CASH,
    GCASH,
}

enum class GCashTransactionType {
    CASH_IN,
    CASH_OUT,
}

enum class GCashTransactionStatus {
    COMPLETED,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ProductActivityType {
    CREATED,
    UPDATED,
    DELETED,
    STOCK_ADJUSTED,
    SOLD,
}

enum class UtangEntryStatus {
    UNPAID,
    PAID,
}
