package com.example.sarisaristore.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupManagerFileNamingTest {

    @Test
    fun `latest backup file name is recognized`() {
        assertTrue(isManagedBackupFileName(LATEST_BACKUP_FILE_NAME))
    }

    @Test
    fun `legacy timestamped backup file name is recognized`() {
        assertTrue(isManagedBackupFileName("sari_store_backup_20260407_181530.zip"))
    }

    @Test
    fun `temporary and rollback backup file names are recognized`() {
        assertTrue(isManagedBackupFileName("sari_store_backup_pending_123456.zip"))
        assertTrue(isManagedBackupFileName("sari_store_backup_previous_123456.zip"))
    }

    @Test
    fun `unrelated zip files are not treated as app-managed backups`() {
        assertFalse(isManagedBackupFileName("backup.zip"))
        assertFalse(isManagedBackupFileName("sari_store_backup_20260407.zip"))
        assertFalse(isManagedBackupFileName("inventory_export.zip"))
    }
}
