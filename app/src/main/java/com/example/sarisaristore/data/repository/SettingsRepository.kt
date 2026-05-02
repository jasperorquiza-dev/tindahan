package com.example.sarisaristore.data.repository

import com.example.sarisaristore.data.local.dao.AppSettingsDao
import com.example.sarisaristore.data.local.entity.AppSettingsEntity
import com.example.sarisaristore.data.local.model.ThemeMode
import com.example.sarisaristore.security.PasscodeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val appSettingsDao: AppSettingsDao,
    private val passcodeManager: PasscodeManager,
) {
    fun observeSettings(): Flow<AppSettingsEntity> = appSettingsDao.observeSettings().map {
        it ?: AppSettingsEntity.default()
    }

    suspend fun getSettings(): AppSettingsEntity = appSettingsDao.getSettings() ?: AppSettingsEntity.default()

    suspend fun ensureInitialized() {
        if (appSettingsDao.getSettings() == null) {
            appSettingsDao.upsertSettings(AppSettingsEntity.default())
        }
    }

    suspend fun updateStoreTitle(storeTitle: String) {
        val current = getSettings()
        appSettingsDao.upsertSettings(current.copy(storeTitle = storeTitle.trim().ifBlank { "My Store" }))
    }

    suspend fun updateLowStockThreshold(threshold: Int) {
        val current = getSettings()
        appSettingsDao.upsertSettings(current.copy(lowStockThreshold = threshold.coerceAtLeast(0)))
    }

    suspend fun updateThemeMode(themeMode: ThemeMode) {
        val current = getSettings()
        appSettingsDao.upsertSettings(current.copy(themeMode = themeMode))
    }

    suspend fun enablePasscode(passcode: String) {
        val current = getSettings()
        val salt = passcodeManager.generateSalt()
        val hash = passcodeManager.hashPasscode(passcode, salt)
        appSettingsDao.upsertSettings(
            current.copy(
                passcodeEnabled = true,
                passcodeSalt = salt,
                passcodeHash = hash,
            ),
        )
    }

    suspend fun disablePasscode() {
        val current = getSettings()
        appSettingsDao.upsertSettings(
            current.copy(
                passcodeEnabled = false,
                passcodeSalt = null,
                passcodeHash = null,
            ),
        )
    }

    suspend fun verifyPasscode(passcode: String): Boolean {
        val current = getSettings()
        val salt = current.passcodeSalt ?: return false
        val hash = current.passcodeHash ?: return false
        return passcodeManager.verifyPasscode(passcode, salt, hash)
    }
}
