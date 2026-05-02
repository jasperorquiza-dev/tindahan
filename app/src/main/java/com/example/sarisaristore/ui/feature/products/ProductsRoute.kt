package com.example.sarisaristore.ui.feature.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.sarisaristore.data.local.entity.ProductEntity
import com.example.sarisaristore.data.repository.ProductRepository
import com.example.sarisaristore.ui.components.ConfirmationDialog
import com.example.sarisaristore.ui.components.EmptyStateCard
import com.example.sarisaristore.ui.components.LocalThumbnailImage
import com.example.sarisaristore.ui.components.MainScreenContentPadding
import com.example.sarisaristore.ui.components.PullRefreshContainer
import com.example.sarisaristore.ui.components.rememberCurrencyText
import com.example.sarisaristore.ui.components.rememberTimestampText
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

data class ProductsUiState(
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val categories: List<String> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastRefreshedAt: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProductsViewModel(
    private val productRepository: ProductRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ProductsUiState())
    private val productFilters = combine(
        state.map { it.searchQuery.trim() }.distinctUntilChanged(),
        state.map { it.selectedCategory?.takeIf(String::isNotBlank) }.distinctUntilChanged(),
    ) { query, category ->
        query to category
    }
    private val products = productFilters.flatMapLatest { (query, category) ->
        productRepository.observeProducts(query, category)
    }

    val uiState: StateFlow<ProductsUiState> = combine(
        state,
        productRepository.observeCategories(),
        products,
    ) { current, categories, products ->
        current.copy(categories = categories, products = products)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProductsUiState(),
    )

    fun onSearchQueryChange(value: String) {
        state.update { it.copy(searchQuery = value) }
    }

    fun onCategorySelected(category: String?) {
        state.update { it.copy(selectedCategory = category) }
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

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            productRepository.deleteProduct(productId)
        }
    }

    companion object {
        fun factory(productRepository: ProductRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ProductsViewModel(productRepository)
                }
            }
    }
}

@Composable
fun ProductsRoute(
    onAddProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
) {
    val container = LocalContext.current.appContainer()
    val viewModel: ProductsViewModel = viewModel(
        factory = ProductsViewModel.factory(container.productRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastRefreshedText = rememberTimestampText(uiState.lastRefreshedAt)
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 360
    var productPendingDelete by remember { mutableStateOf<ProductEntity?>(null) }

    productPendingDelete?.let { product ->
        ConfirmationDialog(
            title = "Delete product",
            message = "Delete ${product.name}? This removes it from your catalog.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteProduct(product.id)
                productPendingDelete = null
            },
            onDismiss = { productPendingDelete = null },
        )
    }

    ScreenLayout(
        title = "Products",
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
                                text = "Catalog",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Products, photos, and prices.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        label = { Text("Search product") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedCategory == null,
                                onClick = { viewModel.onCategorySelected(null) },
                                label = { Text("All") },
                            )
                        }
                        items(uiState.categories) { category ->
                            FilterChip(
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.onCategorySelected(category) },
                                label = { Text(category) },
                            )
                        }
                    }
                }
                item {
                    Button(onClick = onAddProduct, modifier = Modifier.fillMaxWidth()) {
                        Text("Add Product")
                    }
                }
                if (uiState.products.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No products yet",
                            message = "Add products with a photo and the selling price per piece.",
                        )
                    }
                } else {
                    items(uiState.products, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            isCompact = isCompact,
                            onEdit = { onEditProduct(product.id) },
                            onDelete = { productPendingDelete = product },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductEntity,
    isCompact: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val priceText = rememberCurrencyText(product.priceCentavos)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        if (isCompact) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProductImage(
                        imagePath = product.imagePath,
                        productName = product.name,
                        onEdit = onEdit,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        product.category?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                            Text(
                            text = "$priceText per piece",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit product")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete product")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProductImage(
                    imagePath = product.imagePath,
                    productName = product.name,
                    onEdit = onEdit,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    product.category?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "$priceText per piece",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit product")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete product")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductImage(
    imagePath: String?,
    productName: String,
    onEdit: () -> Unit,
) {
    if (!imagePath.isNullOrBlank()) {
        LocalThumbnailImage(
            imagePath = imagePath,
            contentDescription = productName,
            imageSizePx = 192,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onEdit),
        )
    } else {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onEdit),
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
