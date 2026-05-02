package com.example.sarisaristore.ui.feature.utang

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sarisaristore.data.local.model.UtangCustomerSummary
import com.example.sarisaristore.data.repository.UtangRepository
import com.example.sarisaristore.ui.components.EmptyStateCard
import com.example.sarisaristore.ui.components.MainScreenContentPadding
import com.example.sarisaristore.ui.components.AppDialog
import com.example.sarisaristore.ui.components.ScreenLayout
import com.example.sarisaristore.ui.components.rememberTimestampText
import com.example.sarisaristore.util.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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

enum class CustomerDialogMode {
    ADD,
    EDIT,
}

data class UtangUiState(
    val searchQuery: String = "",
    val customers: List<UtangCustomerSummary> = emptyList(),
    val customerDialogMode: CustomerDialogMode? = null,
    val editingCustomerId: Long? = null,
    val customerNameInput: String = "",
    val isSavingCustomer: Boolean = false,
    val deleteCustomerId: Long? = null,
    val deleteCustomerName: String = "",
    val isDeletingCustomer: Boolean = false,
    val dialogErrorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class UtangViewModel(
    private val utangRepository: UtangRepository,
) : ViewModel() {
    private val state = MutableStateFlow(UtangUiState())
    private val customerSummaries = state
        .map { it.searchQuery.trim() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            utangRepository.observeCustomerSummaries(query)
        }

    val uiState: StateFlow<UtangUiState> = combine(
        state,
        customerSummaries,
    ) { current, customers ->
        current.copy(customers = customers)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UtangUiState(),
    )

    fun onSearchQueryChange(value: String) {
        state.update { it.copy(searchQuery = value) }
    }

    fun showAddCustomerDialog() {
        state.update {
            it.copy(
                customerDialogMode = CustomerDialogMode.ADD,
                editingCustomerId = null,
                customerNameInput = "",
                dialogErrorMessage = null,
            )
        }
    }

    fun showEditCustomerDialog(customer: UtangCustomerSummary) {
        state.update {
            it.copy(
                customerDialogMode = CustomerDialogMode.EDIT,
                editingCustomerId = customer.id,
                customerNameInput = customer.name,
                dialogErrorMessage = null,
            )
        }
    }

    fun dismissCustomerDialog() {
        state.update {
            it.copy(
                customerDialogMode = null,
                editingCustomerId = null,
                customerNameInput = "",
                isSavingCustomer = false,
                dialogErrorMessage = null,
            )
        }
    }

    fun onCustomerNameChange(value: String) {
        state.update { it.copy(customerNameInput = value, dialogErrorMessage = null) }
    }

    fun saveCustomer() {
        val current = state.value
        if (current.customerNameInput.isBlank()) {
            state.update { it.copy(dialogErrorMessage = "Enter a customer name.") }
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isSavingCustomer = true, dialogErrorMessage = null) }
            runCatching {
                when (current.customerDialogMode) {
                    CustomerDialogMode.ADD -> utangRepository.addCustomer(current.customerNameInput)
                    CustomerDialogMode.EDIT -> {
                        val customerId = current.editingCustomerId ?: error("Customer not found.")
                        utangRepository.updateCustomer(customerId, current.customerNameInput)
                    }

                    null -> Unit
                }
            }.onSuccess {
                state.update {
                    it.copy(
                        customerDialogMode = null,
                        editingCustomerId = null,
                        customerNameInput = "",
                        isSavingCustomer = false,
                        dialogErrorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                state.update {
                    it.copy(
                        isSavingCustomer = false,
                        dialogErrorMessage = throwable.message ?: "Could not save customer.",
                    )
                }
            }
        }
    }

    fun showDeleteCustomerDialog(customer: UtangCustomerSummary) {
        state.update {
            it.copy(
                deleteCustomerId = customer.id,
                deleteCustomerName = customer.name,
                dialogErrorMessage = null,
            )
        }
    }

    fun dismissDeleteCustomerDialog() {
        state.update {
            it.copy(
                deleteCustomerId = null,
                deleteCustomerName = "",
                isDeletingCustomer = false,
                dialogErrorMessage = null,
            )
        }
    }

    fun deleteCustomer() {
        val current = state.value
        val customerId = current.deleteCustomerId ?: return

        viewModelScope.launch {
            state.update { it.copy(isDeletingCustomer = true, dialogErrorMessage = null) }
            runCatching {
                utangRepository.deleteCustomer(customerId)
            }.onSuccess {
                state.update {
                    it.copy(
                        deleteCustomerId = null,
                        deleteCustomerName = "",
                        isDeletingCustomer = false,
                        dialogErrorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                state.update {
                    it.copy(
                        isDeletingCustomer = false,
                        dialogErrorMessage = throwable.message ?: "Could not delete customer.",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(utangRepository: UtangRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UtangViewModel(utangRepository = utangRepository)
            }
        }
    }
}

@Composable
fun UtangRoute(
    onOpenCustomer: (Long) -> Unit,
) {
    val container = LocalContext.current.appContainer()
    val viewModel: UtangViewModel = viewModel(
        factory = UtangViewModel.factory(container.utangRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.customerDialogMode?.let { mode ->
        CustomerNameDialog(
            mode = mode,
            customerName = uiState.customerNameInput,
            errorMessage = uiState.dialogErrorMessage,
            isSaving = uiState.isSavingCustomer,
            onCustomerNameChange = viewModel::onCustomerNameChange,
            onDismiss = viewModel::dismissCustomerDialog,
            onConfirm = viewModel::saveCustomer,
        )
    }

    if (uiState.deleteCustomerId != null) {
        DeleteCustomerDialog(
            customerName = uiState.deleteCustomerName,
            errorMessage = uiState.dialogErrorMessage,
            isDeleting = uiState.isDeletingCustomer,
            onDismiss = viewModel::dismissDeleteCustomerDialog,
            onConfirm = viewModel::deleteCustomer,
        )
    }

    ScreenLayout(title = "Utang") { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = MainScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                UtangOverviewCard()
            }
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    label = { Text("Search customer") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = viewModel::showAddCustomerDialog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Add Customer",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (uiState.customers.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No customers yet",
                        message = "Add a customer, open their page, then save note-style utang entries there.",
                    )
                }
            } else {
                items(uiState.customers, key = { it.id }) { customer ->
                    SwipeableCustomerSummaryCard(
                        customer = customer,
                        onOpen = { onOpenCustomer(customer.id) },
                        onEdit = { viewModel.showEditCustomerDialog(customer) },
                        onDelete = { viewModel.showDeleteCustomerDialog(customer) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UtangOverviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Customers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Track customer credit in one place.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun SwipeableCustomerSummaryCard(
    customer: UtangCustomerSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Desired reveal offset: 8dp padding + 60dp button + 12dp spacing + 60dp button + 12dp card gap = 152dp
    val actionWidthPx = with(density) { 152.dp.toPx() }
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
            CustomerActionButton(
                label = "Edit",
                icon = Icons.Default.Edit,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    onEdit()
                    scope.launch { offsetX.animateTo(0f) }
                },
            )
            CustomerActionButton(
                label = "Delete",
                icon = Icons.Default.Delete,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {
                    onDelete()
                    scope.launch { offsetX.animateTo(0f) }
                },
            )
        }

        // Foreground Layer (Shifts)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
        ) {
            CustomerSummaryCard(
                customer = customer,
                onClick = {
                    val currentOffset = offsetX.value
                    if (currentOffset == 0f) {
                        onOpen()
                    } else {
                        scope.launch { 
                            offsetX.animateTo(0f, animationSpec = spring())
                        }
                    }
                },
                onLongClick = {
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
    }
}

@Composable
private fun CustomerActionButton(
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
        shape = CircleShape,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomerSummaryCard(
    customer: UtangCustomerSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val updatedAtText = rememberTimestampText(customer.updatedAt)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(28.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = customer.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (customer.activeUnpaidCount == 1) {
                        "1 active unpaid entry"
                    } else {
                        "${customer.activeUnpaidCount} active unpaid entries"
                    },
                    color = if (customer.activeUnpaidCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Updated $updatedAtText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CustomerNameDialog(
    mode: CustomerDialogMode,
    customerName: String,
    errorMessage: String?,
    isSaving: Boolean,
    onCustomerNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(
        title = if (mode == CustomerDialogMode.ADD) "Add customer" else "Edit customer",
        message = if (mode == CustomerDialogMode.ADD) {
            "Create a customer card first, then add utang entries under their profile."
        } else {
            "Update the customer name shown across their utang history."
        },
        onDismissRequest = onDismiss,
        confirmLabel = if (isSaving) "Saving..." else "Save",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmEnabled = !isSaving,
        dismissEnabled = !isSaving,
        content = {
            OutlinedTextField(
                value = customerName,
                onValueChange = onCustomerNameChange,
                label = { Text("Customer name") },
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
private fun DeleteCustomerDialog(
    customerName: String,
    errorMessage: String?,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(
        title = "Delete customer",
        message = "Delete $customerName from Utang? This also removes their active utang and paid history.",
        onDismissRequest = onDismiss,
        confirmLabel = if (isDeleting) "Deleting..." else "Delete",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmEnabled = !isDeleting,
        dismissEnabled = !isDeleting,
        confirmIsDestructive = true,
        content = {
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
