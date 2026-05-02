package com.example.sarisaristore.ui.feature.utang

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sarisaristore.data.local.entity.CustomerEntity
import com.example.sarisaristore.data.local.entity.UtangEntryEntity
import com.example.sarisaristore.data.local.model.UtangEntryStatus
import com.example.sarisaristore.data.repository.UtangRepository
import com.example.sarisaristore.ui.components.AppDialog
import com.example.sarisaristore.ui.components.EmptyStateCard
import com.example.sarisaristore.ui.components.MainScreenContentPadding
import com.example.sarisaristore.ui.components.ScreenLayout
import com.example.sarisaristore.ui.components.rememberCurrencyText
import com.example.sarisaristore.ui.components.rememberDateText
import com.example.sarisaristore.ui.components.rememberTimeText
import com.example.sarisaristore.util.CurrencyUtils
import com.example.sarisaristore.util.appContainer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val MultiWhitespaceRegex = "\\s+".toRegex()

data class UtangCustomerUiState(
    val customer: CustomerEntity? = null,
    val activeEntries: List<UtangEntryEntity> = emptyList(),
    val paidEntries: List<UtangEntryEntity> = emptyList(),
    val activeTotalCentavos: Long = 0,
    val isNewEntryDialogVisible: Boolean = false,
    val editingEntryId: Long? = null,
    val newEntryNote: String = "",
    val newEntryAmountInput: String = "",
    val isSavingEntry: Boolean = false,
    val dialogErrorMessage: String? = null,
    val screenErrorMessage: String? = null,
    val markingPaidEntryId: Long? = null,
)

internal fun formatBorrowedItemLines(noteText: String): List<String> {
    val items = noteText
        .lineSequence()
        .map { line -> line.trim().replace(MultiWhitespaceRegex, " ") }
        .filter { line -> line.isNotEmpty() }
        .toList()

    if (items.isNotEmpty()) {
        return items
    }

    val fallback = noteText.trim().replace(MultiWhitespaceRegex, " ")
    return if (fallback.isEmpty()) emptyList() else listOf(fallback)
}

class UtangCustomerViewModel(
    private val customerId: Long,
    private val utangRepository: UtangRepository,
) : ViewModel() {
    private val state = MutableStateFlow(UtangCustomerUiState())

    val uiState: StateFlow<UtangCustomerUiState> = combine(
        state,
        utangRepository.observeCustomer(customerId),
        utangRepository.observeActiveEntries(customerId),
        utangRepository.observePaidEntries(customerId),
    ) { current, customer, activeEntries, paidEntries ->
        current.copy(
            customer = customer,
            activeEntries = activeEntries,
            paidEntries = paidEntries,
            activeTotalCentavos = activeEntries.sumOf { it.amountCentavos ?: 0L },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UtangCustomerUiState(),
    )

    fun showNewEntryDialog() {
        state.update {
            it.copy(
                isNewEntryDialogVisible = true,
                editingEntryId = null,
                newEntryNote = "",
                newEntryAmountInput = "",
                dialogErrorMessage = null,
                screenErrorMessage = null,
            )
        }
    }

    fun dismissNewEntryDialog() {
        state.update {
            it.copy(
                isNewEntryDialogVisible = false,
                editingEntryId = null,
                newEntryNote = "",
                newEntryAmountInput = "",
                isSavingEntry = false,
                dialogErrorMessage = null,
            )
        }
    }

    fun showEditEntryDialog(entry: UtangEntryEntity) {
        state.update {
            it.copy(
                isNewEntryDialogVisible = true,
                editingEntryId = entry.id,
                newEntryNote = entry.noteText,
                newEntryAmountInput = entry.amountCentavos
                    ?.toBigDecimal()
                    ?.movePointLeft(2)
                    ?.toPlainString()
                    .orEmpty(),
                dialogErrorMessage = null,
                screenErrorMessage = null,
            )
        }
    }

    fun onNewEntryNoteChange(value: String) {
        state.update { it.copy(newEntryNote = value, dialogErrorMessage = null) }
    }

    fun onNewEntryAmountChange(value: String) {
        state.update { it.copy(newEntryAmountInput = value, dialogErrorMessage = null) }
    }

    fun saveEntry() {
        val current = state.value
        if (current.newEntryNote.isBlank()) {
            state.update { it.copy(dialogErrorMessage = "Enter the utang note.") }
            return
        }
        val amountCentavos = if (current.newEntryAmountInput.isBlank()) {
            null
        } else {
            CurrencyUtils.parseToCentavos(current.newEntryAmountInput)
        }
        if (current.newEntryAmountInput.isNotBlank() && (amountCentavos == null || amountCentavos <= 0)) {
            state.update { it.copy(dialogErrorMessage = "Enter a valid utang price.") }
            return
        }

        viewModelScope.launch {
            state.update {
                it.copy(
                    isSavingEntry = true,
                    dialogErrorMessage = null,
                    screenErrorMessage = null,
                )
            }
            runCatching {
                val editingEntryId = current.editingEntryId
                if (editingEntryId == null) {
                    utangRepository.addEntry(
                        customerId = customerId,
                        noteText = current.newEntryNote,
                        amountCentavos = amountCentavos,
                    )
                } else {
                    utangRepository.updateEntry(
                        entryId = editingEntryId,
                        noteText = current.newEntryNote,
                        amountCentavos = amountCentavos,
                    )
                }
            }.onSuccess {
                state.update {
                    it.copy(
                        isNewEntryDialogVisible = false,
                        editingEntryId = null,
                        newEntryNote = "",
                        newEntryAmountInput = "",
                        isSavingEntry = false,
                        dialogErrorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                state.update {
                    it.copy(
                        isSavingEntry = false,
                        dialogErrorMessage = throwable.message ?: "Could not save utang entry.",
                    )
                }
            }
        }
    }

    fun markEntryAsPaid(entryId: Long) {
        if (state.value.markingPaidEntryId != null) {
            return
        }

        viewModelScope.launch {
            state.update { it.copy(markingPaidEntryId = entryId, screenErrorMessage = null) }
            runCatching {
                utangRepository.markEntryAsPaid(entryId)
            }.onSuccess {
                state.update { it.copy(markingPaidEntryId = null) }
            }.onFailure { throwable ->
                state.update {
                    it.copy(
                        markingPaidEntryId = null,
                        screenErrorMessage = throwable.message ?: "Could not update utang entry.",
                    )
                }
            }
        }
    }

    fun markEntryAsUnpaid(entryId: Long) {
        if (state.value.markingPaidEntryId != null) {
            return
        }

        viewModelScope.launch {
            state.update { it.copy(markingPaidEntryId = entryId, screenErrorMessage = null) }
            runCatching {
                utangRepository.markEntryAsUnpaid(entryId)
            }.onSuccess {
                state.update { it.copy(markingPaidEntryId = null) }
            }.onFailure { throwable ->
                state.update {
                    it.copy(
                        markingPaidEntryId = null,
                        screenErrorMessage = throwable.message ?: "Could not update utang entry.",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(customerId: Long, utangRepository: UtangRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    UtangCustomerViewModel(
                        customerId = customerId,
                        utangRepository = utangRepository,
                    )
                }
            }
    }
}

@Composable
fun UtangCustomerRoute(
    customerId: Long,
    onBack: () -> Unit,
) {
    val container = LocalContext.current.appContainer()
    val viewModel: UtangCustomerViewModel = viewModel(
        key = "utang_customer_$customerId",
        factory = UtangCustomerViewModel.factory(customerId, container.utangRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isNewEntryDialogVisible) {
        NewUtangEntryDialog(
            isEditing = uiState.editingEntryId != null,
            noteText = uiState.newEntryNote,
            amountInput = uiState.newEntryAmountInput,
            errorMessage = uiState.dialogErrorMessage,
            isSaving = uiState.isSavingEntry,
            onNoteChange = viewModel::onNewEntryNoteChange,
            onAmountChange = viewModel::onNewEntryAmountChange,
            onDismiss = viewModel::dismissNewEntryDialog,
            onConfirm = viewModel::saveEntry,
        )
    }

    ScreenLayout(
        title = "Utang",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = MainScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val customer = uiState.customer
            if (customer == null) {
                item {
                    EmptyStateCard(
                        title = "Customer not found",
                        message = "This customer record is no longer available.",
                    )
                }
                return@LazyColumn
            }

            item {
                CustomerHeaderCard(
                    customer = customer,
                    activeCount = uiState.activeEntries.size,
                    paidCount = uiState.paidEntries.size,
                    activeTotalCentavos = uiState.activeTotalCentavos,
                )
            }
            item {
                Button(
                    onClick = viewModel::showNewEntryDialog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("New Utang")
                }
            }
            uiState.screenErrorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                SectionHeader(
                    title = "Active Utang",
                    subtitle = if (uiState.activeEntries.size == 1) {
                        "1 unpaid entry"
                    } else {
                        "${uiState.activeEntries.size} unpaid entries"
                    },
                )
            }
            if (uiState.activeEntries.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No active utang",
                        message = "Create a new note-style utang entry when this customer borrows again.",
                    )
                }
            } else {
                items(uiState.activeEntries, key = { it.id }) { entry ->
                    UtangEntryCard(
                        entry = entry,
                        isMarkingPaid = uiState.markingPaidEntryId == entry.id,
                        showMarkPaidButton = true,
                        onEdit = { viewModel.showEditEntryDialog(entry) },
                        onMarkPaid = { viewModel.markEntryAsPaid(entry.id) },
                    )
                }
            }
            item {
                SectionHeader(
                    title = "Paid History",
                    subtitle = if (uiState.paidEntries.size == 1) {
                        "1 paid entry"
                    } else {
                        "${uiState.paidEntries.size} paid entries"
                    },
                )
            }
            if (uiState.paidEntries.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No paid history yet",
                        message = "Entries marked as paid will move here and stay separate from active utang.",
                    )
                }
            } else {
                items(uiState.paidEntries, key = { it.id }) { entry ->
                    SwipeablePaidEntryCard(
                        entry = entry,
                        isMarkingPaid = uiState.markingPaidEntryId == entry.id,
                        onMarkUnpaid = { viewModel.markEntryAsUnpaid(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerHeaderCard(
    customer: CustomerEntity,
    activeCount: Int,
    paidCount: Int,
    activeTotalCentavos: Long,
) {
    val activeTotalText = rememberCurrencyText(activeTotalCentavos)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Utang summary",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Active and paid entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryBadge(
                    label = "Active",
                    value = activeCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                SummaryBadge(
                    label = "Paid",
                    value = paidCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                SummaryBadge(
                    label = "Total",
                    value = activeTotalText,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UtangEntryCard(
    entry: UtangEntryEntity,
    isMarkingPaid: Boolean,
    showMarkPaidButton: Boolean,
    onEdit: (() -> Unit)? = null,
    onMarkPaid: () -> Unit,
) {
    val borrowedItems = remember(entry.noteText) {
        formatBorrowedItemLines(entry.noteText)
    }
    val amountCentavos = entry.amountCentavos
    val amountText = if (amountCentavos != null) {
        rememberCurrencyText(amountCentavos)
    } else {
        null
    }
    val createdAtDateText = rememberDateText(entry.createdAt)
    val createdAtTimeText = rememberTimeText(entry.createdAt)
    val paidAt = entry.paidAt
    val paidAtText = if (paidAt != null) {
        "${rememberDateText(paidAt)} ${rememberTimeText(paidAt)}"
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EntrySection(title = "Borrowed Items") {
                borrowedItems.forEach { item ->
                    BorrowedItemRow(item = item)
                }
            }
            EntrySection(title = "Status + Price") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusPill(status = entry.status)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Price",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = amountText ?: "No amount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.amountCentavos != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            EntrySection(title = "Date / Time") {
                MetadataLine(label = "Date", value = createdAtDateText)
                MetadataLine(label = "Time", value = createdAtTimeText)
                paidAtText?.let { resolvedPaidAtText ->
                    MetadataLine(
                        label = "Paid",
                        value = resolvedPaidAtText,
                    )
                }
            }
            if (showMarkPaidButton) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onEdit?.invoke() },
                            enabled = !isMarkingPaid && onEdit != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = " Edit",
                            )
                        }
                        FilledTonalButton(
                            onClick = onMarkPaid,
                            enabled = !isMarkingPaid,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isMarkingPaid) "Updating..." else "Mark Paid")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntrySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun BorrowedItemRow(
    item: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    shape = CircleShape,
                ),
        )
        Text(
            text = item,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusPill(
    status: UtangEntryStatus,
) {
    val backgroundColor = if (status == UtangEntryStatus.UNPAID) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (status == UtangEntryStatus.UNPAID) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, shape = MaterialTheme.shapes.large)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (status == UtangEntryStatus.UNPAID) "Unpaid" else "Paid",
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetadataLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NewUtangEntryDialog(
    isEditing: Boolean,
    noteText: String,
    amountInput: String,
    errorMessage: String?,
    isSaving: Boolean,
    onNoteChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(
        title = if (isEditing) "Edit utang entry" else "New utang entry",
        message = "Keep the note in item lines so the card stays easy to scan later.",
        onDismissRequest = onDismiss,
        confirmLabel = if (isSaving) {
            "Saving..."
        } else if (isEditing) {
            "Update"
        } else {
            "Save"
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmEnabled = !isSaving,
        dismissEnabled = !isSaving,
        content = {
            OutlinedTextField(
                value = noteText,
                onValueChange = onNoteChange,
                label = { Text("What did the customer borrow?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = amountInput,
                onValueChange = onAmountChange,
                label = { Text("Price (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun SwipeablePaidEntryCard(
    entry: UtangEntryEntity,
    isMarkingPaid: Boolean,
    onMarkUnpaid: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Only 1 button (60dp) + 8dp right padding + 12dp spacing from card
    val actionWidthPx = with(density) { 80.dp.toPx() } 
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Actions Layer (Underneath)
        Row(
            modifier = Modifier
                .padding(end = 8.dp)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryActionButton(
                label = "Unpaid",
                icon = Icons.Default.Restore,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    onMarkUnpaid()
                    scope.launch { offsetX.animateTo(0f) }
                },
            )
        }

        // Foreground Layer (Shifts)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(actionWidthPx) {
                    detectTapGestures(
                        onTap = {
                            if (offsetX.value < 0f) {
                                scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                            }
                        },
                        onLongPress = {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = -actionWidthPx,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    )
                }
        ) {
            UtangEntryCard(
                entry = entry,
                isMarkingPaid = isMarkingPaid,
                showMarkPaidButton = false,
                onEdit = null,
                onMarkPaid = {},
            )
        }
    }
}

@Composable
private fun EntryActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(60.dp)
            .clickable(onClick = onClick),
        color = backgroundColor,
        contentColor = contentColor,
        shape = androidx.compose.foundation.shape.CircleShape,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
