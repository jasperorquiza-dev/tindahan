package com.example.sarisaristore

import android.app.Application
import androidx.room.Room
import com.example.sarisaristore.camera.ImageStorageManager
import com.example.sarisaristore.data.backup.AppBackupManager
import com.example.sarisaristore.data.backup.BackupPreferences
import com.example.sarisaristore.data.local.AppDatabase
import com.example.sarisaristore.data.repository.GCashRepository
import com.example.sarisaristore.data.repository.ProductActivityRepository
import com.example.sarisaristore.data.repository.ProductRepository
import com.example.sarisaristore.data.repository.SettingsRepository
import com.example.sarisaristore.data.repository.UtangRepository
import com.example.sarisaristore.security.PasscodeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(
    application: Application,
) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "sari_sari_store.db",
    ).addMigrations(
        AppDatabase.MIGRATION_1_4,
        AppDatabase.MIGRATION_2_4,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
    )
        .build()

    private val imageStorageManager = ImageStorageManager(application)
    private val backupPreferences = BackupPreferences(application)
    private val passcodeManager = PasscodeManager()
    private val productActivityRepository = ProductActivityRepository(database.productActivityLogDao())

    val settingsRepository = SettingsRepository(database.appSettingsDao(), passcodeManager)
    val productRepository = ProductRepository(
        productDao = database.productDao(),
        productActivityRepository = productActivityRepository,
        imageStorageManager = imageStorageManager,
    )
    val gcashRepository = GCashRepository(
        gcashTransactionDao = database.gcashTransactionDao(),
        imageStorageManager = imageStorageManager,
    )
    val utangRepository = UtangRepository(
        database = database,
        utangDao = database.utangDao(),
    )
    val imageManager: ImageStorageManager = imageStorageManager
    val backupManager = AppBackupManager(
        context = application,
        database = database,
        imageStorageManager = imageStorageManager,
        backupPreferences = backupPreferences,
        ioContext = Dispatchers.IO,
    )

    init {
        applicationScope.launch {
            settingsRepository.ensureInitialized()
        }
    }
}
