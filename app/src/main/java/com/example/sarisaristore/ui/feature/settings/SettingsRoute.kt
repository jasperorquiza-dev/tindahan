package com.example.sarisaristore.ui.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.sarisaristore.data.backup.AppBackupManager
import com.example.sarisaristore.data.backup.BackupPreview
import com.example.sarisaristore.data.backup.LATEST_BACKUP_FILE_NAME
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sarisaristore.data.local.model.ThemeMode
import com.example.sarisaristore.data.repository.SettingsRepository
import com.example.sarisaristore.ui.components.AppDialog
import com.example.sarisaristore.ui.components.ScreenContentColumn
import com.example.sarisaristore.ui.components.ScreenLayout
import com.example.sarisaristore.util.DateTimeUtils
import com.example.sarisaristore.util.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val storeTitleInput: String = "My Store",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val passcodeEnabled: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val backupFolderUri: String? = null,
    val backupFolderName: String? = null,
    val lastBackupFileName: String? = null,
    val lastBackupAt: Long? = null,
    val backupErrorMessage: String? = null,
    val isCreatingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val pendingRestoreUri: Uri? = null,
    val pendingRestorePreview: BackupPreview? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupManager: AppBackupManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observeSettings(),
                backupManager.observePreferences(),
            ) { settings, backupState ->
                settings to backupState
            }.collect { (settings, backupState) ->
                _uiState.update {
                    it.copy(
                        storeTitleInput = settings.storeTitle,
                        themeMode = settings.themeMode,
                        passcodeEnabled = settings.passcodeEnabled,
                        backupFolderUri = backupState.folderUri,
                        backupFolderName = backupState.folderDisplayName,
                        lastBackupFileName = backupState.lastBackupFileName,
                        lastBackupAt = backupState.lastBackupAt,
                    )
                }
            }
        }
    }

    fun onStoreTitleChange(value: String) {
        _uiState.update { it.copy(storeTitleInput = value, message = null, errorMessage = null) }
    }

    fun onThemeModeSelected(themeMode: ThemeMode) {
        _uiState.update { it.copy(themeMode = themeMode, errorMessage = null) }
        viewModelScope.launch {
            settingsRepository.updateThemeMode(themeMode)
        }
    }

    fun saveStoreSettings() {
        viewModelScope.launch {
            val resolvedTitle = _uiState.value.storeTitleInput.trim().ifBlank { "My Store" }
            settingsRepository.updateStoreTitle(resolvedTitle)
            _uiState.update {
                it.copy(
                    storeTitleInput = resolvedTitle,
                    message = "Store name updated to \"$resolvedTitle\".",
                    errorMessage = null,
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearBackupError() {
        _uiState.update { it.copy(backupErrorMessage = null) }
    }

    fun saveBackupFolder(uri: Uri, displayName: String?) {
        backupManager.saveBackupFolder(folderUri = uri, displayName = displayName)
        _uiState.update {
            it.copy(
                backupErrorMessage = null,
                message = "Backup folder updated.",
            )
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreatingBackup = true,
                    backupErrorMessage = null,
                )
            }
            runCatching { backupManager.createBackup() }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isCreatingBackup = false,
                            message = "Latest backup saved as ${result.fileName}.",
                            backupErrorMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isCreatingBackup = false,
                            backupErrorMessage = throwable.message ?: "Could not create backup.",
                        )
                    }
                }
        }
    }

    fun prepareRestore(sourceUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoringBackup = true,
                    backupErrorMessage = null,
                    pendingRestoreUri = null,
                    pendingRestorePreview = null,
                )
            }
            runCatching { backupManager.validateBackup(sourceUri) }
                .onSuccess { preview ->
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            pendingRestoreUri = sourceUri,
                            pendingRestorePreview = preview,
                            backupErrorMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            backupErrorMessage = throwable.message ?: "Could not read backup file.",
                        )
                    }
                }
        }
    }

    fun dismissRestoreConfirmation() {
        _uiState.update {
            it.copy(
                isRestoringBackup = false,
                pendingRestoreUri = null,
                pendingRestorePreview = null,
            )
        }
    }

    fun restorePendingBackup() {
        val sourceUri = _uiState.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoringBackup = true,
                    backupErrorMessage = null,
                )
            }
            runCatching { backupManager.restoreBackup(sourceUri) }
                .onSuccess { result ->
                    val message = if (result.missingImageCount > 0) {
                        "Restore completed from ${result.restoredFileName}. ${result.missingImageCount} image file(s) were missing."
                    } else {
                        "Restore completed from ${result.restoredFileName}."
                    }
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            pendingRestoreUri = null,
                            pendingRestorePreview = null,
                            message = message,
                            backupErrorMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            backupErrorMessage = throwable.message ?: "Could not restore backup.",
                        )
                    }
                }
        }
    }

    fun setPasscode(passcode: String, confirmPasscode: String) {
        when {
            !passcode.matches(Regex("""\d{4}""")) ->
                _uiState.update { it.copy(errorMessage = "Passcode must be 4 digits.") }

            passcode != confirmPasscode ->
                _uiState.update { it.copy(errorMessage = "Passcodes do not match.") }

            else -> {
                viewModelScope.launch {
                    settingsRepository.enablePasscode(passcode)
                    _uiState.update {
                        it.copy(
                            passcodeEnabled = true,
                            message = "Passcode updated.",
                            errorMessage = null,
                        )
                    }
                }
            }
        }
    }

    fun disablePasscode() {
        viewModelScope.launch {
            settingsRepository.disablePasscode()
            _uiState.update {
                it.copy(
                    passcodeEnabled = false,
                    message = "Passcode disabled.",
                    errorMessage = null,
                )
            }
        }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            backupManager: AppBackupManager,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SettingsViewModel(
                        settingsRepository = settingsRepository,
                        backupManager = backupManager,
                    )
                }
            }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsRoute() {
    val container = LocalContext.current.appContainer()
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settingsRepository = container.settingsRepository,
            backupManager = container.backupManager,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPasscodeDialog by remember { mutableStateOf(false) }
    val selectBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val folderName = DocumentFile.fromTreeUri(context, uri)?.name
        viewModel.saveBackupFolder(uri = uri, displayName = folderName)
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModel.prepareRestore(uri)
    }

    if (showPasscodeDialog) {
        PasscodeDialog(
            title = if (uiState.passcodeEnabled) "Change passcode" else "Enable passcode",
            onDismiss = { showPasscodeDialog = false },
            onConfirm = { pin, confirm ->
                viewModel.setPasscode(pin, confirm)
                showPasscodeDialog = false
            },
        )
    }

    uiState.message?.let { message ->
        AppDialog(
            title = "Success",
            message = message,
            onDismissRequest = viewModel::clearMessage,
            confirmLabel = "OK",
            onConfirm = viewModel::clearMessage,
            dismissLabel = null,
        )
    }

    uiState.pendingRestorePreview?.let { preview ->
        AppDialog(
            title = "Restore backup",
            message = "This may replace the current app data. Continue only if this backup is the one you want to restore.",
            onDismissRequest = viewModel::dismissRestoreConfirmation,
            confirmLabel = if (uiState.isRestoringBackup) "Restoring..." else "Restore",
            onConfirm = viewModel::restorePendingBackup,
            onDismiss = viewModel::dismissRestoreConfirmation,
            confirmEnabled = !uiState.isRestoringBackup,
            dismissEnabled = !uiState.isRestoringBackup,
            confirmIsDestructive = true,
            content = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = preview.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Products ${preview.productCount}, GCash ${preview.gcashTransactionCount}, Utang ${preview.utangCustomerCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }

    ScreenLayout(title = "Settings") { paddingValues ->
        ScreenContentColumn(
            paddingValues = paddingValues,
            content = {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Store", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Set the store name shown on the home screen.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = uiState.storeTitleInput,
                            onValueChange = viewModel::onStoreTitleChange,
                            label = { Text("Store title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = viewModel::saveStoreSettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save store name")
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Theme changes apply right away.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeMode.entries.forEach { themeMode ->
                                FilterChip(
                                    selected = uiState.themeMode == themeMode,
                                    onClick = { viewModel.onThemeModeSelected(themeMode) },
                                    label = {
                                        Text(
                                            when (themeMode) {
                                                ThemeMode.SYSTEM -> "Match phone"
                                                ThemeMode.LIGHT -> "Light"
                                                ThemeMode.DARK -> "Dark"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Passcode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (uiState.passcodeEnabled) {
                                "A 4-digit passcode is required when opening the app."
                            } else {
                                "Passcode is off."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { showPasscodeDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (uiState.passcodeEnabled) "Change passcode" else "Enable passcode")
                        }
                        if (uiState.passcodeEnabled) {
                            OutlinedButton(
                                onClick = viewModel::disablePasscode,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Disable passcode")
                            }
                        }
                    }
                }

                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Backup & Restore",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Manual backup only. The app keeps one latest backup ZIP in the folder you choose.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Selected backup folder",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = uiState.backupFolderName ?: "No folder selected",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    uiState.backupFolderUri?.let { folderUri ->
                                        Text(
                                            text = folderUri,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = "Latest file: $LATEST_BACKUP_FILE_NAME",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = uiState.lastBackupAt?.let { lastBackupAt ->
                                            "Last backup: ${DateTimeUtils.formatTimestamp(lastBackupAt)}"
                                        } ?: "Last backup: Not created yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { selectBackupFolderLauncher.launch(null) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isCreatingBackup && !uiState.isRestoringBackup,
                                ) {
                                    Text("Select Folder")
                                }
                                Button(
                                    onClick = viewModel::createBackup,
                                    modifier = Modifier.weight(1f),
                                    enabled = uiState.backupFolderUri != null &&
                                        !uiState.isCreatingBackup &&
                                        !uiState.isRestoringBackup,
                                ) {
                                    Text(if (uiState.isCreatingBackup) "Creating..." else "Create Backup")
                                }
                            }
                            OutlinedButton(
                                onClick = { restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isCreatingBackup && !uiState.isRestoringBackup,
                            ) {
                                Text(
                                    if (uiState.isRestoringBackup && uiState.pendingRestorePreview == null) {
                                        "Checking backup..."
                                    } else {
                                        "Restore Backup"
                                    },
                                )
                            }
                            Text(
                                text = "The latest backup stays available offline in the selected folder as long as $LATEST_BACKUP_FILE_NAME remains there.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            uiState.backupErrorMessage?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Tindahan")
                        Text(
                            "Clean product pricing and GCash tracking for one device, even offline.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "Developed by JasperOO",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            },
        )
    }
}

@Composable
private fun PasscodeDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    AppDialog(
        title = title,
        message = "Use a 4-digit PIN to protect the app when it opens.",
        onDismissRequest = onDismiss,
        confirmLabel = "Save",
        onConfirm = { onConfirm(pin, confirmPin) },
        content = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                label = { Text("4-digit passcode") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) },
                label = { Text("Confirm passcode") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
    )
}
