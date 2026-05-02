package com.example.sarisaristore.ui.feature.gcash

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sarisaristore.camera.ImageStorageManager
import com.example.sarisaristore.camera.ImageType
import com.example.sarisaristore.data.local.model.GCashTransactionType
import com.example.sarisaristore.data.repository.GCashRepository
import com.example.sarisaristore.ui.components.ImagePreviewCard
import com.example.sarisaristore.ui.components.ScreenContentColumn
import com.example.sarisaristore.ui.components.ScreenLayout
import com.example.sarisaristore.ui.components.SignatureCaptureDialog
import com.example.sarisaristore.util.CurrencyUtils
import com.example.sarisaristore.util.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileOutputStream

data class GCashFormUiState(
    val editingTransactionId: Long? = null,
    val transactionType: GCashTransactionType = GCashTransactionType.CASH_IN,
    val amountInput: String = "",
    val serviceFeeInput: String = "",
    val customerName: String = "",
    val note: String = "",
    val receiptImagePath: String? = null,
    val signatureImagePath: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveCompleted: Boolean = false,
)

class GCashFormViewModel(
    private val transactionId: Long?,
    private val gcashRepository: GCashRepository,
    private val imageStorageManager: ImageStorageManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GCashFormUiState(editingTransactionId = transactionId))
    val uiState: StateFlow<GCashFormUiState> = _uiState.asStateFlow()
    private var persistedSignatureImagePath: String? = null
    private var persistedReceiptImagePath: String? = null

    init {
        if (transactionId != null) {
            loadTransaction(transactionId)
        }
    }

    private fun loadTransaction(transactionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val transaction = gcashRepository.getTransaction(transactionId)
            if (transaction == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Transaction not found.",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    transactionType = transaction.type,
                    amountInput = transaction.amountCentavos.toBigDecimal().movePointLeft(2).toPlainString(),
                    serviceFeeInput = transaction.serviceFeeCentavos.toBigDecimal().movePointLeft(2).toPlainString(),
                    customerName = transaction.customerName.orEmpty(),
                    note = transaction.note.orEmpty(),
                    receiptImagePath = transaction.receiptImagePath,
                    signatureImagePath = transaction.signatureImagePath,
                    isLoading = false,
                )
            }
            persistedReceiptImagePath = transaction.receiptImagePath
            persistedSignatureImagePath = transaction.signatureImagePath
        }
    }

    fun onTransactionTypeChange(value: GCashTransactionType) {
        val currentSignaturePath = _uiState.value.signatureImagePath
        if (
            value != GCashTransactionType.CASH_OUT &&
            !currentSignaturePath.isNullOrBlank() &&
            currentSignaturePath != persistedSignatureImagePath
        ) {
            viewModelScope.launch {
                imageStorageManager.deleteImage(currentSignaturePath)
            }
        }
        _uiState.update {
            it.copy(
                transactionType = value,
                signatureImagePath = if (value == GCashTransactionType.CASH_OUT) {
                    it.signatureImagePath
                } else {
                    null
                },
                errorMessage = null,
            )
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amountInput = value, errorMessage = null) }
    }

    fun onServiceFeeChange(value: String) {
        _uiState.update { it.copy(serviceFeeInput = value, errorMessage = null) }
    }

    fun onCustomerNameChange(value: String) {
        _uiState.update { it.copy(customerName = value, errorMessage = null) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value, errorMessage = null) }
    }

    fun onReceiptCaptured(path: String) {
        replaceReceiptImage(path)
    }

    fun onReceiptUploaded(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                imageStorageManager.importImage(uri, ImageType.RECEIPT)
            }.onSuccess { path ->
                replaceReceiptImage(path)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "Could not upload receipt.")
                }
            }
        }
    }

    fun onSignatureCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            runCatching {
                imageStorageManager.saveBitmap(bitmap = bitmap, imageType = ImageType.SIGNATURE)
            }.onSuccess { path ->
                val previousPath = _uiState.value.signatureImagePath
                if (!previousPath.isNullOrBlank() &&
                    previousPath != persistedSignatureImagePath &&
                    previousPath != path
                ) {
                    imageStorageManager.deleteImage(previousPath)
                }
                _uiState.update { it.copy(signatureImagePath = path, errorMessage = null) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "Could not save signature.")
                }
            }
        }
    }

    fun saveTransaction() {
        val current = _uiState.value
        val amountCentavos = CurrencyUtils.parseToCentavos(current.amountInput)
        val feeCentavos = CurrencyUtils.parseToCentavos(current.serviceFeeInput).orZero()
        val signatureImagePath = current.signatureImagePath.takeIf {
            current.transactionType == GCashTransactionType.CASH_OUT
        }
        if (amountCentavos == null || amountCentavos <= 0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid amount.") }
            return
        }
        if (current.receiptImagePath.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Add a receipt photo before saving.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                if (transactionId == null) {
                    gcashRepository.createTransaction(
                        type = current.transactionType,
                        amountCentavos = amountCentavos,
                        serviceFeeCentavos = feeCentavos,
                        customerName = current.customerName,
                        receiptImagePath = current.receiptImagePath,
                        signatureImagePath = signatureImagePath,
                        note = current.note,
                    )
                } else {
                    gcashRepository.updateTransaction(
                        transactionId = transactionId,
                        type = current.transactionType,
                        amountCentavos = amountCentavos,
                        serviceFeeCentavos = feeCentavos,
                        customerName = current.customerName,
                        receiptImagePath = current.receiptImagePath,
                        signatureImagePath = signatureImagePath,
                        note = current.note,
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saveCompleted = true) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = throwable.message ?: "Could not save transaction.",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            transactionId: Long?,
            gcashRepository: GCashRepository,
            imageStorageManager: ImageStorageManager,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    GCashFormViewModel(transactionId, gcashRepository, imageStorageManager)
                }
            }
    }

    private fun replaceReceiptImage(newPath: String) {
        val previousPath = _uiState.value.receiptImagePath
        if (!previousPath.isNullOrBlank() &&
            previousPath != persistedReceiptImagePath &&
            previousPath != newPath
        ) {
            viewModelScope.launch {
                imageStorageManager.deleteImage(previousPath)
            }
        }
        _uiState.update { it.copy(receiptImagePath = newPath, errorMessage = null) }
    }
}

private fun Long?.orZero(): Long = this ?: 0L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GCashFormRoute(
    transactionId: Long?,
    savedStateHandle: SavedStateHandle,
    onBack: () -> Unit,
    onCaptureReceipt: () -> Unit,
) {
    val container = LocalContext.current.appContainer()
    var showSignatureDialog by remember { mutableStateOf(false) }
    val viewModel: GCashFormViewModel = viewModel(
        key = "gcash_form_$transactionId",
        factory = GCashFormViewModel.factory(
            transactionId = transactionId,
            gcashRepository = container.gcashRepository,
            imageStorageManager = container.imageManager,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(viewModel::onReceiptUploaded)
    }
    val cameraResult by savedStateHandle
        .getStateFlow<String?>(ImageType.RECEIPT.resultKey, null)
        .collectAsStateWithLifecycle()

    LaunchedEffect(cameraResult) {
        cameraResult?.let { path ->
            viewModel.onReceiptCaptured(path)
            savedStateHandle[ImageType.RECEIPT.resultKey] = null
        }
    }

    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            onBack()
        }
    }

    if (showSignatureDialog) {
        SignatureCaptureDialog(
            onDismiss = { showSignatureDialog = false },
            onSave = { bitmap ->
                viewModel.onSignatureCaptured(bitmap)
                showSignatureDialog = false
            },
        )
    }

    ScreenLayout(
        title = if (transactionId == null) "Add Transaction" else "Edit Transaction",
    ) { paddingValues ->
        ScreenContentColumn(
            paddingValues = paddingValues,
            includeBottomInset = true,
            content = {
                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (transactionId == null) "New GCash record" else "Update GCash record",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Save amount, fee, customer name, reference, receipt photo, and an optional signature here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.transactionType == GCashTransactionType.CASH_IN,
                        onClick = { viewModel.onTransactionTypeChange(GCashTransactionType.CASH_IN) },
                        label = { Text("Cash In") },
                    )
                    FilterChip(
                        selected = uiState.transactionType == GCashTransactionType.CASH_OUT,
                        onClick = { viewModel.onTransactionTypeChange(GCashTransactionType.CASH_OUT) },
                        label = { Text("Cash Out") },
                    )
                }
                ImagePreviewCard(
                    title = "Receipt photo",
                    imagePath = uiState.receiptImagePath,
                    primaryButtonLabel = if (uiState.receiptImagePath.isNullOrBlank()) "Capture photo" else "Retake photo",
                    previewEnabled = !uiState.receiptImagePath.isNullOrBlank(),
                    secondaryButtonLabel = "Upload photo",
                    onPrimaryClick = onCaptureReceipt,
                    onSecondaryClick = { receiptPickerLauncher.launch("image/*") },
                )
                OutlinedTextField(
                    value = uiState.amountInput,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.serviceFeeInput,
                    onValueChange = viewModel::onServiceFeeChange,
                    label = { Text("Service fee") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.customerName,
                    onValueChange = viewModel::onCustomerNameChange,
                    label = { Text("Customer name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text("Reference (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.transactionType == GCashTransactionType.CASH_OUT) {
                    Button(
                        onClick = { showSignatureDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (uiState.signatureImagePath.isNullOrBlank()) {
                                "Add signature"
                            } else {
                                "Change signature"
                            },
                        )
                    }
                }
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = viewModel::saveTransaction,
                    enabled = !uiState.isLoading && !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSaving) "Saving..." else "Save transaction")
                }
            },
        )
    }
}
