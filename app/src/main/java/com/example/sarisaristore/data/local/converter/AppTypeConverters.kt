package com.example.sarisaristore.data.local.converter

import androidx.room.TypeConverter
import com.example.sarisaristore.data.local.model.GCashTransactionStatus
import com.example.sarisaristore.data.local.model.GCashTransactionType
import com.example.sarisaristore.data.local.model.PaymentMethod
import com.example.sarisaristore.data.local.model.ProductActivityType
import com.example.sarisaristore.data.local.model.ThemeMode
import com.example.sarisaristore.data.local.model.UtangEntryStatus

class AppTypeConverters {
    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)

    @TypeConverter
    fun fromGCashTransactionType(value: GCashTransactionType): String = value.name

    @TypeConverter
    fun toGCashTransactionType(value: String): GCashTransactionType = GCashTransactionType.valueOf(value)

    @TypeConverter
    fun fromGCashTransactionStatus(value: GCashTransactionStatus): String = value.name

    @TypeConverter
    fun toGCashTransactionStatus(value: String): GCashTransactionStatus =
        GCashTransactionStatus.valueOf(value)

    @TypeConverter
    fun fromThemeMode(value: ThemeMode): String = value.name

    @TypeConverter
    fun toThemeMode(value: String): ThemeMode = ThemeMode.valueOf(value)

    @TypeConverter
    fun fromProductActivityType(value: ProductActivityType): String = value.name

    @TypeConverter
    fun toProductActivityType(value: String): ProductActivityType =
        ProductActivityType.valueOf(value)

    @TypeConverter
    fun fromUtangEntryStatus(value: UtangEntryStatus): String = value.name

    @TypeConverter
    fun toUtangEntryStatus(value: String): UtangEntryStatus = UtangEntryStatus.valueOf(value)
}
