package com.example.sarisaristore.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sarisaristore.data.local.model.ThemeMode

@Entity(tableName = "app_settings")
@Immutable
data class AppSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val storeTitle: String = "My Store",
    val passcodeEnabled: Boolean = false,
    val passcodeSalt: String? = null,
    val passcodeHash: String? = null,
    val lowStockThreshold: Int = 5,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        const val SINGLETON_ID = 1

        fun default() = AppSettingsEntity()
    }
}
