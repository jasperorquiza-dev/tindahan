package com.example.sarisaristore.data.backup

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackupPreferencesState(
    val folderUri: String? = null,
    val folderDisplayName: String? = null,
    val lastBackupFileName: String? = null,
    val lastBackupAt: Long? = null,
)

class BackupPreferences(
    context: Context,
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "backup_preferences",
        Context.MODE_PRIVATE,
    )
    private val state = MutableStateFlow(readState())

    fun observeState(): StateFlow<BackupPreferencesState> = state.asStateFlow()

    fun getState(): BackupPreferencesState = state.value

    fun setBackupFolder(uri: String, displayName: String?) {
        sharedPreferences.edit()
            .putString(KEY_FOLDER_URI, uri)
            .putString(KEY_FOLDER_NAME, displayName)
            .apply()
        refresh()
    }

    fun updateLastBackup(fileName: String, createdAt: Long) {
        sharedPreferences.edit()
            .putString(KEY_LAST_BACKUP_FILE_NAME, fileName)
            .putLong(KEY_LAST_BACKUP_AT, createdAt)
            .apply()
        refresh()
    }

    private fun refresh() {
        state.value = readState()
    }

    private fun readState(): BackupPreferencesState = BackupPreferencesState(
        folderUri = sharedPreferences.getString(KEY_FOLDER_URI, null),
        folderDisplayName = sharedPreferences.getString(KEY_FOLDER_NAME, null),
        lastBackupFileName = sharedPreferences.getString(KEY_LAST_BACKUP_FILE_NAME, null),
        lastBackupAt = sharedPreferences.takeIf { it.contains(KEY_LAST_BACKUP_AT) }
            ?.getLong(KEY_LAST_BACKUP_AT, 0L),
    )

    private companion object {
        const val KEY_FOLDER_URI = "backup_folder_uri"
        const val KEY_FOLDER_NAME = "backup_folder_name"
        const val KEY_LAST_BACKUP_FILE_NAME = "last_backup_file_name"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"
    }
}
