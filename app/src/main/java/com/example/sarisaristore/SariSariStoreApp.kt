package com.example.sarisaristore

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sarisaristore.camera.ImageType
import com.example.sarisaristore.data.local.entity.AppSettingsEntity
import com.example.sarisaristore.ui.feature.gcash.GCashRoute
import com.example.sarisaristore.ui.feature.gcash.GCashFormRoute
import com.example.sarisaristore.ui.feature.home.HomeRoute
import com.example.sarisaristore.ui.feature.productform.ProductFormRoute
import com.example.sarisaristore.ui.feature.products.ProductsRoute
import com.example.sarisaristore.ui.feature.settings.SettingsRoute
import com.example.sarisaristore.ui.feature.utang.UtangCustomerRoute
import com.example.sarisaristore.ui.feature.utang.UtangRoute
import com.example.sarisaristore.ui.feature.unlock.CameraCaptureRoute
import com.example.sarisaristore.ui.feature.unlock.StartupRoute
import com.example.sarisaristore.ui.feature.unlock.UnlockRoute
import com.example.sarisaristore.ui.navigation.AppDestinations
import com.example.sarisaristore.ui.navigation.bottomNavItems
import com.example.sarisaristore.ui.theme.SariSariTheme
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private const val FADE_DURATION = 200
private const val SLIDE_DURATION = 280

private val tabEnter = fadeIn(animationSpec = tween(FADE_DURATION))
private val tabExit = fadeOut(animationSpec = tween(FADE_DURATION))
private val pushEnter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(SLIDE_DURATION)) + fadeIn(animationSpec = tween(SLIDE_DURATION))
private val pushExit = slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(SLIDE_DURATION)) + fadeOut(animationSpec = tween(SLIDE_DURATION))
private val popEnter = slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(SLIDE_DURATION)) + fadeIn(animationSpec = tween(SLIDE_DURATION))
private val popExit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(SLIDE_DURATION)) + fadeOut(animationSpec = tween(SLIDE_DURATION))

@Composable
fun SariSariStoreApp(
    container: AppContainer,
) {
    var isSettingsReady by remember(container) { mutableStateOf(false) }

    LaunchedEffect(container) {
        container.settingsRepository.ensureInitialized()
        isSettingsReady = true
    }
    val settings by container.settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettingsEntity.default())

    SariSariTheme(themeMode = settings.themeMode) {
        val navController = rememberNavController()
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = currentBackStackEntry?.destination
        val showBottomBar = currentDestination?.route in AppDestinations.bottomRoutes
        val navigateToTopLevelDestination: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { destination ->
                                destination.route == item.route
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navigateToTopLevelDestination(item.route)
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                alwaysShowLabel = false,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestinations.STARTUP,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { tabEnter },
                exitTransition = { tabExit },
                popEnterTransition = { tabEnter },
                popExitTransition = { tabExit },
            ) {
                composable(AppDestinations.STARTUP) {
                    StartupRoute(
                        passcodeEnabled = settings.passcodeEnabled,
                        isReady = isSettingsReady,
                        onNavigate = { destination ->
                            navController.navigate(destination) {
                                popUpTo(AppDestinations.STARTUP) {
                                    inclusive = true
                                }
                            }
                        },
                    )
                }
                composable(AppDestinations.UNLOCK) {
                    UnlockRoute(
                        onUnlocked = {
                            navController.navigate(AppDestinations.HOME) {
                                popUpTo(AppDestinations.UNLOCK) {
                                    inclusive = true
                                }
                            }
                        },
                    )
                }
                composable(AppDestinations.HOME) {
                    HomeRoute(
                        storeTitle = settings.storeTitle,
                        onAddProduct = { navController.navigate(AppDestinations.productFormRoute()) },
                        onNewGCashTransaction = { navController.navigate(AppDestinations.gcashFormRoute()) },
                        onOpenUtang = { navigateToTopLevelDestination(AppDestinations.UTANG) },
                        onViewProducts = { navigateToTopLevelDestination(AppDestinations.PRODUCTS) },
                        onEditProduct = { productId ->
                            navController.navigate(AppDestinations.productFormRoute(productId))
                        },
                    )
                }
                composable(AppDestinations.PRODUCTS) {
                    ProductsRoute(
                        onAddProduct = { navController.navigate(AppDestinations.productFormRoute()) },
                        onEditProduct = { productId ->
                            navController.navigate(AppDestinations.productFormRoute(productId))
                        },
                    )
                }
                composable(
                    route = AppDestinations.productFormPattern,
                    arguments = listOf(
                        androidx.navigation.navArgument(AppDestinations.PRODUCT_FORM_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                    enterTransition = { pushEnter },
                    exitTransition = { pushExit },
                    popEnterTransition = { popEnter },
                    popExitTransition = { popExit },
                ) { backStackEntry ->
                    val productId = backStackEntry.arguments?.getString(AppDestinations.PRODUCT_FORM_ARG)
                        ?.toLongOrNull()
                    ProductFormRoute(
                        productId = productId,
                        savedStateHandle = backStackEntry.savedStateHandle,
                        onBack = { navController.popBackStack() },
                        onCaptureImage = {
                            navController.navigate(AppDestinations.cameraRoute(ImageType.PRODUCT))
                        },
                    )
                }
                composable(AppDestinations.GCASH) {
                    GCashRoute(
                        onAddTransaction = { navController.navigate(AppDestinations.gcashFormRoute()) },
                        onEditTransaction = { transactionId ->
                            navController.navigate(AppDestinations.gcashFormRoute(transactionId))
                        },
                    )
                }
                composable(AppDestinations.UTANG) {
                    UtangRoute(
                        onOpenCustomer = { customerId ->
                            navController.navigate(AppDestinations.utangCustomerRoute(customerId))
                        },
                    )
                }
                composable(
                    route = AppDestinations.gcashFormPattern,
                    arguments = listOf(
                        androidx.navigation.navArgument(AppDestinations.GCASH_FORM_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                    enterTransition = { pushEnter },
                    exitTransition = { pushExit },
                    popEnterTransition = { popEnter },
                    popExitTransition = { popExit },
                ) { backStackEntry ->
                    val transactionId = backStackEntry.arguments?.getString(AppDestinations.GCASH_FORM_ARG)
                        ?.toLongOrNull()
                    GCashFormRoute(
                        transactionId = transactionId,
                        savedStateHandle = backStackEntry.savedStateHandle,
                        onBack = { navController.popBackStack() },
                        onCaptureReceipt = {
                            navController.navigate(AppDestinations.cameraRoute(ImageType.RECEIPT))
                        },
                    )
                }
                composable(
                    route = AppDestinations.utangCustomerPattern,
                    arguments = listOf(
                        androidx.navigation.navArgument(AppDestinations.UTANG_CUSTOMER_ARG) {
                            type = NavType.StringType
                        },
                    ),
                    enterTransition = { pushEnter },
                    exitTransition = { pushExit },
                    popEnterTransition = { popEnter },
                    popExitTransition = { popExit },
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getString(AppDestinations.UTANG_CUSTOMER_ARG)
                        ?.toLongOrNull()
                    if (customerId != null) {
                        UtangCustomerRoute(
                            customerId = customerId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(AppDestinations.SETTINGS) {
                    SettingsRoute()
                }
                composable(
                    route = AppDestinations.cameraPattern,
                    arguments = listOf(
                        androidx.navigation.navArgument(AppDestinations.CAMERA_IMAGE_TYPE_ARG) {
                            type = NavType.StringType
                        },
                    ),
                    enterTransition = { pushEnter },
                    exitTransition = { pushExit },
                    popEnterTransition = { popEnter },
                    popExitTransition = { popExit },
                ) { backStackEntry ->
                    val imageType = backStackEntry.arguments
                        ?.getString(AppDestinations.CAMERA_IMAGE_TYPE_ARG)
                        ?.let(ImageType::valueOf)
                        ?: ImageType.PRODUCT
                    CameraCaptureRoute(
                        imageType = imageType,
                        onClose = { navController.popBackStack() },
                        onImageCaptured = { path ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(imageType.resultKey, path)
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}
