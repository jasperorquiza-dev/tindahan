package com.example.sarisaristore.ui.feature.productform

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
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
import com.example.sarisaristore.data.repository.ProductRepository
import com.example.sarisaristore.ui.components.ImagePreviewCard
import com.example.sarisaristore.ui.components.ScreenContentColumn
import com.example.sarisaristore.ui.components.ScreenLayout
import com.example.sarisaristore.util.CurrencyUtils
import com.example.sarisaristore.util.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductFormUiState(
    val name: String = "",
    val priceInput: String = "",
    val categoryInput: String = "",
    val categorySuggestions: List<String> = emptyList(),
    val imagePath: String? = null,
    val existingStockQuantity: Int = 1,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveCompleted: Boolean = false,
)

class ProductFormViewModel(
    private val productId: Long?,
    private val productRepository: ProductRepository,
    private val imageStorageManager: ImageStorageManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProductFormUiState(isEditing = productId != null))
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()
    private var persistedImagePath: String? = null

    init {
        observeCategorySuggestions()
        if (productId != null) {
            loadProduct(productId)
        }
    }

    private fun observeCategorySuggestions() {
        viewModelScope.launch {
            productRepository.observeCategories().collect { categories ->
                _uiState.update {
                    it.copy(categorySuggestions = categories.normalizedCategorySuggestions())
                }
            }
        }
    }

    private fun loadProduct(productId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val product = productRepository.getProduct(productId)
            if (product == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Product not found.",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    name = product.name,
                    priceInput = product.priceCentavos.toBigDecimal().movePointLeft(2).toPlainString(),
                    categoryInput = product.category.orEmpty(),
                    imagePath = product.imagePath,
                    existingStockQuantity = product.stockQuantity,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            persistedImagePath = product.imagePath
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun onPriceChange(value: String) {
        _uiState.update { it.copy(priceInput = value, errorMessage = null) }
    }

    fun onCategoryChange(value: String) {
        _uiState.update { it.copy(categoryInput = value, errorMessage = null) }
    }

    fun onCategorySuggestionSelected(category: String) {
        _uiState.update { it.copy(categoryInput = category, errorMessage = null) }
    }

    fun onImageCaptured(path: String) {
        replaceImage(path)
    }

    fun onImageUploaded(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                imageStorageManager.importImage(uri, ImageType.PRODUCT)
            }.onSuccess { path ->
                replaceImage(path)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "Could not upload image.")
                }
            }
        }
    }

    fun saveProduct() {
        val state = _uiState.value
        val priceCentavos = CurrencyUtils.parseToCentavos(state.priceInput)
        when {
            state.name.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Product name is required.") }
                return
            }

            priceCentavos == null || priceCentavos < 0 -> {
                _uiState.update { it.copy(errorMessage = "Enter a valid price per piece.") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            if (productId == null) {
                productRepository.addProduct(
                    name = state.name,
                    imagePath = state.imagePath,
                    priceCentavos = priceCentavos,
                    category = state.categoryInput,
                )
            } else {
                productRepository.updateProduct(
                    productId = productId,
                    name = state.name,
                    imagePath = state.imagePath,
                    priceCentavos = priceCentavos,
                    stockQuantity = state.existingStockQuantity,
                    category = state.categoryInput,
                )
            }
            _uiState.update { it.copy(isSaving = false, saveCompleted = true) }
        }
    }

    companion object {
        fun factory(
            productId: Long?,
            productRepository: ProductRepository,
            imageStorageManager: ImageStorageManager,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ProductFormViewModel(productId, productRepository, imageStorageManager)
                }
            }
    }

    private fun replaceImage(newPath: String) {
        val oldPath = _uiState.value.imagePath
        if (!oldPath.isNullOrBlank() && oldPath != persistedImagePath && oldPath != newPath) {
            viewModelScope.launch {
                imageStorageManager.deleteImage(oldPath)
            }
        }
        _uiState.update { it.copy(imagePath = newPath, errorMessage = null) }
    }
}

private fun List<String>.normalizedCategorySuggestions(): List<String> =
    asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .toList()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductFormRoute(
    productId: Long?,
    savedStateHandle: SavedStateHandle,
    onBack: () -> Unit,
    onCaptureImage: () -> Unit,
) {
    val container = LocalContext.current.appContainer()
    val viewModel: ProductFormViewModel = viewModel(
        key = "product_form_$productId",
        factory = ProductFormViewModel.factory(
            productId = productId,
            productRepository = container.productRepository,
            imageStorageManager = container.imageManager,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(viewModel::onImageUploaded)
    }
    val cameraResult by savedStateHandle
        .getStateFlow<String?>(ImageType.PRODUCT.resultKey, null)
        .collectAsStateWithLifecycle()

    LaunchedEffect(cameraResult) {
        cameraResult?.let { path ->
            viewModel.onImageCaptured(path)
            savedStateHandle[ImageType.PRODUCT.resultKey] = null
        }
    }

    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            onBack()
        }
    }

    ScreenLayout(
        title = if (uiState.isEditing) "Edit Product" else "Add Product",
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
                            text = if (uiState.isEditing) "Update product details" else "Create a new product",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Save the photo, name, and price for one piece of the product.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ImagePreviewCard(
                    title = "Product photo",
                    imagePath = uiState.imagePath,
                    primaryButtonLabel = if (uiState.imagePath.isNullOrBlank()) "Capture photo" else "Retake photo",
                    previewEnabled = !uiState.imagePath.isNullOrBlank(),
                    secondaryButtonLabel = "Upload photo",
                    onPrimaryClick = onCaptureImage,
                    onSecondaryClick = { imagePickerLauncher.launch("image/*") },
                )
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Product name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.priceInput,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text("Price per piece") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.categoryInput,
                    onValueChange = viewModel::onCategoryChange,
                    label = { Text("Category (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (uiState.categorySuggestions.isNotEmpty()) {
                    val selectedCategory = uiState.categoryInput.trim()
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Suggested categories",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            uiState.categorySuggestions.forEach { category ->
                                FilterChip(
                                    selected = category.equals(selectedCategory, ignoreCase = true),
                                    onClick = { viewModel.onCategorySuggestionSelected(category) },
                                    label = { Text(category) },
                                )
                            }
                        }
                    }
                }
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = viewModel::saveProduct,
                    enabled = !uiState.isLoading && !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSaving) "Saving..." else "Save product")
                }
            },
        )
    }
}
