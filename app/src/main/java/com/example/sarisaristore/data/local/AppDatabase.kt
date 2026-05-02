package com.example.sarisaristore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sarisaristore.data.local.converter.AppTypeConverters
import com.example.sarisaristore.data.local.dao.AppSettingsDao
import com.example.sarisaristore.data.local.dao.GCashTransactionDao
import com.example.sarisaristore.data.local.dao.ProductActivityLogDao
import com.example.sarisaristore.data.local.dao.ProductDao
import com.example.sarisaristore.data.local.dao.SaleDao
import com.example.sarisaristore.data.local.dao.UtangDao
import com.example.sarisaristore.data.local.entity.AppSettingsEntity
import com.example.sarisaristore.data.local.entity.CustomerEntity
import com.example.sarisaristore.data.local.entity.GCashTransactionEntity
import com.example.sarisaristore.data.local.entity.ProductActivityLogEntity
import com.example.sarisaristore.data.local.entity.ProductEntity
import com.example.sarisaristore.data.local.entity.SaleEntity
import com.example.sarisaristore.data.local.entity.UtangEntryEntity

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        GCashTransactionEntity::class,
        AppSettingsEntity::class,
        ProductActivityLogEntity::class,
        CustomerEntity::class,
        UtangEntryEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun gcashTransactionDao(): GCashTransactionDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun productActivityLogDao(): ProductActivityLogDao
    abstract fun utangDao(): UtangDao

    companion object {
        val MIGRATION_1_4: Migration = object : Migration(1, 4) {
            override fun migrate(database: SupportSQLiteDatabase) = Unit
        }

        val MIGRATION_2_4: Migration = object : Migration(2, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                dropLegacyCreditTables(database)
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                dropLegacyCreditTables(database)
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `utang_customers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `utang_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customerId` INTEGER NOT NULL,
                        `noteText` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `paidAt` INTEGER,
                        FOREIGN KEY(`customerId`) REFERENCES `utang_customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_utang_customers_name` ON `utang_customers` (`name`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_utang_entries_customerId` ON `utang_entries` (`customerId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_utang_entries_status` ON `utang_entries` (`status`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_utang_entries_customerId_status` ON `utang_entries` (`customerId`, `status`)",
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE `utang_entries`
                    ADD COLUMN `amountCentavos` INTEGER
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    ALTER TABLE `gcash_transactions`
                    ADD COLUMN `signatureImagePath` TEXT
                    """.trimIndent(),
                )
            }
        }

        private fun dropLegacyCreditTables(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS `utang_payments`")
            database.execSQL("DROP TABLE IF EXISTS `utang_entry_items`")
            database.execSQL("DROP TABLE IF EXISTS `utang_entries`")
            database.execSQL("DROP TABLE IF EXISTS `utang_customers`")
        }
    }
}
