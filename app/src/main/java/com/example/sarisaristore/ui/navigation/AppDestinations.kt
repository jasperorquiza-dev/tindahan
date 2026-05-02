package com.example.sarisaristore.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.sarisaristore.camera.ImageType

object AppDestinations {
    const val STARTUP = "startup"
    const val UNLOCK = "unlock"
    const val HOME = "home"
    const val PRODUCTS = "products"
    const val GCASH = "gcash"
    const val UTANG = "utang"
    const val SETTINGS = "settings"
    const val PRODUCT_FORM = "product_form"
    const val GCASH_FORM = "gcash_form"
    const val UTANG_CUSTOMER = "utang_customer"
    const val CAMERA = "camera"

    const val PRODUCT_FORM_ARG = "productId"
    const val GCASH_FORM_ARG = "transactionId"
    const val UTANG_CUSTOMER_ARG = "customerId"
    const val CAMERA_IMAGE_TYPE_ARG = "imageType"
    val bottomRoutes = setOf(HOME, PRODUCTS, GCASH, UTANG, SETTINGS)

    val productFormPattern = "$PRODUCT_FORM?$PRODUCT_FORM_ARG={$PRODUCT_FORM_ARG}"
    val gcashFormPattern = "$GCASH_FORM?$GCASH_FORM_ARG={$GCASH_FORM_ARG}"
    val utangCustomerPattern = "$UTANG_CUSTOMER/{$UTANG_CUSTOMER_ARG}"
    val cameraPattern = "$CAMERA/{$CAMERA_IMAGE_TYPE_ARG}"
    fun productFormRoute(productId: Long? = null): String =
        if (productId == null) {
            PRODUCT_FORM
        } else {
            "$PRODUCT_FORM?$PRODUCT_FORM_ARG=$productId"
        }

    fun gcashFormRoute(transactionId: Long? = null): String =
        if (transactionId == null) {
            GCASH_FORM
        } else {
            "$GCASH_FORM?$GCASH_FORM_ARG=$transactionId"
        }

    fun utangCustomerRoute(customerId: Long): String = "$UTANG_CUSTOMER/$customerId"

    fun cameraRoute(imageType: ImageType): String = "$CAMERA/${imageType.name}"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(AppDestinations.HOME, "Home", Icons.Default.Home),
    BottomNavItem(AppDestinations.PRODUCTS, "Products", Icons.Default.Inventory2),
    BottomNavItem(AppDestinations.GCASH, "GCash", Icons.Default.AccountBalanceWallet),
    BottomNavItem(AppDestinations.UTANG, "Utang", Icons.Default.Person),
    BottomNavItem(AppDestinations.SETTINGS, "Settings", Icons.Default.Settings),
)
