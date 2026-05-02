package com.example.sarisaristore.ui.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.sarisaristore.data.local.entity.ProductEntity
import com.example.sarisaristore.data.repository.ProductRepository
import com.example.sarisaristore.ui.components.EmptyStateCard
import com.example.sarisaristore.ui.components.LocalThumbnailImage
import com.example.sarisaristore.ui.components.MainScreenContentPadding
import com.example.sarisaristore.ui.components.PullRefreshContainer
import com.example.sarisaristore.ui.components.rememberCurrencyText
import com.example.sarisaristore.ui.components.rememberDateText
import com.example.sarisaristore.ui.components.rememberTimeText
import com.example.sarisaristore.ui.components.ScreenLayout
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

private val HomeSectionSpacing = 12.dp

data class HomeUiState(
    val searchQuery: String = "",
    val products: List<ProductEntity> = emptyList(),
    val totalProducts: Int = 0,
    val isRefreshing: Boolean = false,
    val lastRefreshedAt: Long = System.currentTimeMillis(),
)

private data class HomeQuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val iconTint: Color,
    val iconContainer: Color,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    productRepository: ProductRepository,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    private val products = state
        .map { it.searchQuery.trim() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            productRepository.observeProducts(query, null)
        }

    val uiState: StateFlow<HomeUiState> = combine(
        state,
        products,
    ) { current, products ->
        current.copy(
            products = products.take(8),
            totalProducts = products.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun onSearchQueryChange(value: String) {
        state.update { it.copy(searchQuery = value) }
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

    companion object {
        fun factory(productRepository: ProductRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(productRepository = productRepository)
            }
        }
    }
}

@Composable
fun HomeRoute(
    storeTitle: String,
    onAddProduct: () -> Unit,
    onNewGCashTransaction: () -> Unit,
    onOpenUtang: () -> Unit,
    onViewProducts: () -> Unit,
    onEditProduct: (Long) -> Unit,
) {
    val container = LocalContext.current.appContainer()
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(productRepository = container.productRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val actionItems = remember(
        onAddProduct,
        onNewGCashTransaction,
        onOpenUtang,
        colorScheme.primary,
        colorScheme.primaryContainer,
        colorScheme.secondary,
        colorScheme.secondaryContainer,
        colorScheme.tertiary,
    ) {
        listOf(
            HomeQuickAction(
                label = "Add Product",
                icon = Icons.Default.AddCircle,
                onClick = onAddProduct,
                iconTint = colorScheme.primary,
                iconContainer = colorScheme.primaryContainer,
            ),
            HomeQuickAction(
                label = "Add GCash",
                icon = Icons.Default.AccountBalanceWallet,
                onClick = onNewGCashTransaction,
                iconTint = colorScheme.secondary,
                iconContainer = colorScheme.secondaryContainer,
            ),
            HomeQuickAction(
                label = "Utang",
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                onClick = onOpenUtang,
                iconTint = colorScheme.tertiary,
                iconContainer = colorScheme.primaryContainer.copy(alpha = 0.65f),
            ),
        )
    }

    ScreenLayout(title = storeTitle) { paddingValues ->
        PullRefreshContainer(
            refreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = MainScreenContentPadding,
                verticalArrangement = Arrangement.spacedBy(HomeSectionSpacing),
            ) {
                item {
                    HomeOverviewCard(
                        totalProducts = uiState.totalProducts,
                        lastRefreshedAt = uiState.lastRefreshedAt,
                    )
                }
                item {
                    HomeQuickActionsSection(actions = actionItems)
                }
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        placeholder = { Text("Search products") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                }
                item {
                    HomeSectionHeader(
                        title = "Quick products",
                        subtitle = "Tap a product to edit its details and price.",
                        actionLabel = "View all",
                        onActionClick = onViewProducts,
                    )
                }
                if (uiState.products.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No products yet",
                            message = "Add your first item with photo, name, and price per piece.",
                        )
                    }
                } else {
                    items(uiState.products, key = { it.id }) { product ->
                        HomeProductCard(
                            product = product,
                            onClick = { onEditProduct(product.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeOverviewCard(
    totalProducts: Int,
    lastRefreshedAt: Long,
) {
    val refreshedAtTime = rememberTimeText(lastRefreshedAt)
    val refreshedAtDate = rememberDateText(lastRefreshedAt)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        BoxWithConstraints {
            val stackedMetrics = maxWidth < 360.dp

            Column(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Snapshot",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Catalog and records at a glance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (stackedMetrics) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OverviewMetricCard(
                            icon = Icons.Default.Inventory2,
                            label = "Products",
                            value = totalProducts.toString(),
                            supportingText = "Items in your catalog",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OverviewMetricCard(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = "Updated",
                            value = refreshedAtTime,
                            supportingText = refreshedAtDate,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OverviewMetricCard(
                            icon = Icons.Default.Inventory2,
                            label = "Products",
                            value = totalProducts.toString(),
                            supportingText = "Items in your catalog",
                            modifier = Modifier.weight(1f),
                        )
                        OverviewMetricCard(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = "Updated",
                            value = refreshedAtTime,
                            supportingText = refreshedAtDate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeQuickActionsSection(
    actions: List<HomeQuickAction>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader(
            title = "Quick actions",
            subtitle = "Shortcuts for the most common tasks.",
        )
        HomeQuickActionsGrid(actions = actions)
    }
}

@Composable
private fun HomeQuickActionsGrid(
    actions: List<HomeQuickAction>,
) {
    BoxWithConstraints {
        val columns = if (maxWidth < 360.dp) 2 else 3
        val rows = actions.chunked(columns)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowActions.forEach { action ->
                        HomeQuickActionCard(
                            action = action,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeQuickActionCard(
    action: HomeQuickAction,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = action.onClick)
            .defaultMinSize(minHeight = 88.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(action.iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = action.iconTint,
                )
            }
            Text(
                text = action.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun HomeProductCard(
    product: ProductEntity,
    onClick: () -> Unit,
) {
    val priceText = rememberCurrencyText(product.priceCentavos)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductThumbnail(
                imagePath = product.imagePath,
                productName = product.name,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                product.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = priceText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Per piece",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductThumbnail(
    imagePath: String?,
    productName: String,
) {
    if (!imagePath.isNullOrBlank()) {
        LocalThumbnailImage(
            imagePath = imagePath,
            contentDescription = productName,
            imageSizePx = 160,
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(18.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
