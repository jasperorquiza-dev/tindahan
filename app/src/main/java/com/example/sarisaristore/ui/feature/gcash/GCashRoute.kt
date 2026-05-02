package com.example.sarisaristore.ui.feature.gcash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sarisaristore.data.local.entity.GCashTransactionEntity
import com.example.sarisaristore.data.local.model.GCashTransactionType
import com.example.sarisaristore.data.repository.DateRange
import com.example.sarisaristore.data.repository.GCashRepository
import com.example.sarisaristore.ui.components.AppDialogSurface
import com.example.sarisaristore.ui.components.ConfirmationDialog
import com.example.sarisaristore.ui.components.EmptyStateCard
import com.example.sarisaristore.ui.components.LocalPreviewImage
import com.example.sarisaristore.ui.components.LocalThumbnailImage
import com.example.sarisaristore.ui.components.MainScreenContentPadding
import com.example.sarisaristore.ui.components.PullRefreshContainer
import com.example.sarisaristore.ui.components.rememberCurrencyText
import com.example.sarisaristore.ui.components.rememberTimestampText
import com.example.sarisaristore.ui.components.ScreenLayout
import com.example.sarisaristore.ui.components.SummaryCard
import com.example.sarisaristore.util.CurrencyUtils
import com.example.sarisaristore.util.DateTimeUtils
import com.example.sarisaristore.util.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GCashTransactionFilter(val label: String) {
    ALL("All"),
    CASH_IN("Cash In"),
    CASH_OUT("Cash Out"),
}

enum class GCashDateFilter(val label: String) {
    ALL("All"),
    LAST_1_HOUR("1 hr"),
    LAST_5_HOURS("5 hrs"),
    LAST_1_DAY("1 day"),
    LAST_7_DAYS("7 days"),
    LAST_30_DAYS("30 days"),
}

data class GCashListUiState(
    val selectedTransactionFilter: GCashTransactionFilter = GCashTransactionFilter.ALL,
    val selectedDateFilter: GCashDateFilter = GCashDateFilter.ALL,
    val transactions: List<GCashTransactionEntity> = emptyList(),
    val visibleAmountCentavos: Long = 0,
    val visibleFeeCentavos: Long = 0,
    val isRefreshing: Boolean = false,
    val lastRefreshedAt: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class GCashViewModel(
    private val gcashRepository: GCashRepository,
) : ViewModel() {
    private val state = MutableStateFlow(
        GCashListUiState(
            isRefreshing = false,
            lastRefreshedAt = System.currentTimeMillis(),
        ),
    )
    private val transactions = state
        .map { it.selectedDateFilter }
        .distinctUntilChanged()
        .flatMapLatest { selectedDateFilter ->
            gcashRepository.observeTransactions(selectedDateFilter.toRange())
        }

    val uiState: StateFlow<GCashListUiState> = combine(
        state,
        transactions,
    ) { current, transactions ->
        val filteredTransactions = transactions.filterBy(current.selectedTransactionFilter)
        current.copy(
            transactions = filteredTransactions,
            visibleAmountCentavos = filteredTransactions.sumOf { it.amountCentavos },
            visibleFeeCentavos = filteredTransactions.sumOf { it.serviceFeeCentavos },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GCashListUiState(),
    )

    fun onTransactionFilterSelected(filter: GCashTransactionFilter) {
        state.update { it.copy(selectedTransactionFilter = filter) }
    }

    fun onDateFilterSelected(filter: GCashDateFilter) {
        state.update { it.copy(selectedDateFilter = filter) }
    }

    fun refresh() {
        if (state.value.isRefreshing) {
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isRefreshing = true) }
            delay(250)
            state.update {
                it.copy(
                    isRefreshing = false,
                    lastRefreshedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            gcashRepository.deleteTransaction(transactionId)
        }
    }

    companion object {
        fun factory(gcashRepository: GCashRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    GCashViewModel(gcashRepository)
                }
            }
    }
}

private fun GCashDateFilter.toRange(): DateRange = when (this) {
    GCashDateFilter.ALL -> DateRange(null, null)
    GCashDateFilter.LAST_1_HOUR -> DateTimeUtils.lastHoursRange(1)
    GCashDateFilter.LAST_5_HOURS -> DateTimeUtils.lastHoursRange(5)
    GCashDateFilter.LAST_1_DAY -> DateTimeUtils.lastDaysRange(1)
    GCashDateFilter.LAST_7_DAYS -> DateTimeUtils.lastDaysRange(7)
    GCashDateFilter.LAST_30_DAYS -> DateTimeUtils.lastDaysRange(30)
}

private fun List<GCashTransactionEntity>.filterBy(filter: GCashTransactionFilter): List<GCashTransactionEntity> = when (filter) {
    GCashTransactionFilter.ALL -> this
    GCashTransactionFilter.CASH_IN -> this.filter { it.type == GCashTransactionType.CASH_IN }
    GCashTransactionFilter.CASH_OUT -> this.filter { it.type == GCashTransactionType.CASH_OUT }
}

private fun GCashTransactionType.displayLabel(): String = when (this) {
    GCashTransactionType.CASH_IN -> "Cash In"
    GCashTransactionType.CASH_OUT -> "Cash Out"
}

private fun GCashListUiState.selectionSummary(): String =
    "${selectedTransactionFilter.label} - ${selectedDateFilter.label} - ${transactions.size} ${if (transactions.size == 1) "record" else "records"}"

private fun GCashListUiState.emptyStateMessage(): String =
    if (selectedTransactionFilter == GCashTransactionFilter.ALL && selectedDateFilter == GCashDateFilter.ALL) {
        "Add a transaction and it will show here with its receipt."
    } else {
        "No matching records for the selected filters. Try another day range or transaction type."
    }

@Composable
fun GCashRoute(
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
) {
    val container = LocalContext.current.appContainer()
    val viewModel: GCashViewModel = viewModel(
        factory = GCashViewModel.factory(container.gcashRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastRefreshedText = rememberTimestampText(uiState.lastRefreshedAt)
    val visibleAmountText = rememberCurrencyText(uiState.visibleAmountCentavos)
    val visibleFeeText = rememberCurrencyText(uiState.visibleFeeCentavos)
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 360
    var transactionPendingDelete by remember { mutableStateOf<GCashTransactionEntity?>(null) }

    transactionPendingDelete?.let { transaction ->
        ConfirmationDialog(
            title = "Delete transaction",
            message = "Delete this ${transaction.type.displayLabel().lowercase()} record?",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteTransaction(transaction.id)
                transactionPendingDelete = null
            },
            onDismiss = { transactionPendingDelete = null },
        )
    }

    ScreenLayout(
        title = "GCash",
    ) { paddingValues ->
        PullRefreshContainer(
            refreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = MainScreenContentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Records",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Cash in and cash out history.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = uiState.selectionSummary(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Last refreshed: $lastRefreshedText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Transaction type",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GCashTransactionFilter.entries) { filter ->
                                FilterChip(
                                    selected = uiState.selectedTransactionFilter == filter,
                                    onClick = { viewModel.onTransactionFilterSelected(filter) },
                                    label = { Text(filter.label) },
                                )
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Day range",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GCashDateFilter.entries) { filter ->
                                FilterChip(
                                    selected = uiState.selectedDateFilter == filter,
                                    onClick = { viewModel.onDateFilterSelected(filter) },
                                    label = { Text(filter.label) },
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SummaryCard(
                            label = "Shown amount",
                            value = visibleAmountText,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryCard(
                            label = "Service fees",
                            value = visibleFeeText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Button(onClick = onAddTransaction, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Add Transaction")
                    }
                }
                item {
                    Text(
                        text = "Saved transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (uiState.transactions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No GCash transactions found",
                            message = uiState.emptyStateMessage(),
                        )
                    }
                } else {
                    items(uiState.transactions, key = { it.id }) { transaction ->
                        GCashTransactionCard(
                            transaction = transaction,
                            isCompact = isCompact,
                            onEdit = { onEditTransaction(transaction.id) },
                            onDelete = { transactionPendingDelete = transaction },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GCashTransactionCard(
    transaction: GCashTransactionEntity,
    isCompact: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val amountText = rememberCurrencyText(transaction.amountCentavos)
    val feeText = rememberCurrencyText(transaction.serviceFeeCentavos)
    val createdAtText = rememberTimestampText(transaction.createdAt)
    var previewContent by remember { mutableStateOf<TransactionPreviewContent?>(null) }
    val customerName = transaction.customerName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "Walk-in customer"
    val reference = transaction.note
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val signatureImagePath = transaction.signatureImagePath
        ?.takeIf { transaction.type == GCashTransactionType.CASH_OUT && it.isNotBlank() }
    val receiptStatus = if (signatureImagePath == null) {
        "Receipt saved"
    } else {
        "Receipt + signature"
    }

    fun openPreview() {
        previewContent = TransactionPreviewContent(
            receiptImagePath = transaction.receiptImagePath,
            signatureImagePath = signatureImagePath,
        )
    }

    previewContent?.let { preview ->
        ImagePreviewDialog(
            receiptImagePath = preview.receiptImagePath,
            signatureImagePath = preview.signatureImagePath,
            onDismiss = { previewContent = null },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                TypeBadge(label = transaction.type.displayLabel())
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = customerName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                QuietLabeledValue(
                    label = "Fee",
                    value = feeText,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = createdAtText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = receiptStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            reference?.let {
                Text(
                    text = "Reference: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(onClick = ::openPreview),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    tonalElevation = 1.dp,
                ) {
                    LocalThumbnailImage(
                        imagePath = transaction.receiptImagePath,
                        contentDescription = "Receipt thumbnail",
                        imageSizePx = 192,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                if (isCompact) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompactActionChip(
                                modifier = Modifier.weight(1f),
                                label = "Preview",
                                icon = Icons.Default.Visibility,
                                emphasized = true,
                                onClick = ::openPreview,
                            )
                            CompactActionChip(
                                modifier = Modifier.weight(1f),
                                label = "Edit",
                                icon = Icons.Default.Edit,
                                onClick = onEdit,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompactActionChip(
                                modifier = Modifier.fillMaxWidth(),
                                label = "Delete",
                                icon = Icons.Default.Delete,
                                isDestructive = true,
                                onClick = onDelete,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactActionChip(
                            modifier = Modifier.weight(1f),
                            label = "Preview",
                            icon = Icons.Default.Visibility,
                            emphasized = true,
                            onClick = ::openPreview,
                        )
                        CompactActionChip(
                            modifier = Modifier.weight(1f),
                            label = "Edit",
                            icon = Icons.Default.Edit,
                            onClick = onEdit,
                        )
                        CompactActionChip(
                            modifier = Modifier.weight(1f),
                            label = "Delete",
                            icon = Icons.Default.Delete,
                            isDestructive = true,
                            onClick = onDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun QuietLabeledValue(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CompactActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    isDestructive: Boolean = false,
) {
    val containerColor = when {
        emphasized -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = when {
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        isDestructive -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private data class TransactionPreviewContent(
    val receiptImagePath: String,
    val signatureImagePath: String?,
)

private enum class PreviewMediaType {
    RECEIPT,
    SIGNATURE,
}

@Composable
private fun ImagePreviewDialog(
    receiptImagePath: String,
    signatureImagePath: String?,
    onDismiss: () -> Unit,
) {
    var selectedMediaType by remember(receiptImagePath, signatureImagePath) {
        mutableStateOf(PreviewMediaType.RECEIPT)
    }
    val mediaOptions = remember(signatureImagePath) {
        buildList {
            add(PreviewMediaType.RECEIPT)
            if (!signatureImagePath.isNullOrBlank()) {
                add(PreviewMediaType.SIGNATURE)
            }
        }
    }
    val title = if (selectedMediaType == PreviewMediaType.SIGNATURE) {
        "Signature Preview"
    } else {
        "Receipt Preview"
    }
    val activeImagePath = if (selectedMediaType == PreviewMediaType.SIGNATURE) {
        signatureImagePath ?: receiptImagePath
    } else {
        receiptImagePath
    }

    AppDialogSurface(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close preview",
                )
            }
        }
        if (mediaOptions.size > 1) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    mediaOptions.forEach { option ->
                        val isSelected = selectedMediaType == option
                        val optionLabel = if (option == PreviewMediaType.RECEIPT) {
                            "Receipt"
                        } else {
                            "Signature"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)
                                    },
                                )
                                .clickable { selectedMediaType = option }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = optionLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 420.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                LocalPreviewImage(
                    imagePath = activeImagePath,
                    contentDescription = if (selectedMediaType == PreviewMediaType.SIGNATURE) {
                        "Signature preview"
                    } else {
                        "Receipt preview"
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close")
        }
    }
}
