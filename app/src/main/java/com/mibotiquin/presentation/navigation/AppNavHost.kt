package com.mibotiquin.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.mibotiquin.MiBotiquinApplication
import com.mibotiquin.presentation.ui.components.CreateCabinetDialog
import com.mibotiquin.presentation.ui.components.DeleteCabinetDialog
import com.mibotiquin.presentation.ui.screen.home.HomeScreen
import com.mibotiquin.presentation.ui.screen.home.HomeViewModel
import com.mibotiquin.presentation.ui.screen.scanner.ScannerScreen
import com.mibotiquin.presentation.ui.screen.scanner.ScannerViewModel
import com.mibotiquin.presentation.ui.screen.settings.SettingsScreen
import com.mibotiquin.presentation.ui.screen.setup.SetupScreen

object AppDestinations {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val SETTINGS = "settings"
    const val DEEP_LINK_SCAN_URI = "mibotiquin://scan"
    const val KEY_SCANNED_CN = "scanned_cn"
    const val KEY_SCANNED_NAME = "scanned_name"
    const val KEY_SCANNED_EXPIRY = "scanned_expiry"
}

@Composable
fun AppNavHost(viewModelFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val container = MiBotiquinApplication.container(context)
    val startDestination = if (container.preferences.isFirstRun) "setup" else AppDestinations.HOME

    // UNA sola instancia del HomeViewModel compartida entre Home y Settings (scope Activity)
    val activity = context as? ComponentActivity
    val homeViewModel: HomeViewModel = if (activity != null) {
        viewModel(viewModelStoreOwner = activity, factory = viewModelFactory)
    } else {
        viewModel(factory = viewModelFactory)
    }

    NavHost(navController, startDestination = startDestination) {

        composable(route = "setup") {
            SetupScreen(
                onComplete = {
                    container.preferences.isFirstRun = false
                    navController.navigate(AppDestinations.HOME) {
                        popUpTo("setup") { inclusive = true }
                    }
                },
                preferences = container.preferences
            )
        }

        composable(route = AppDestinations.HOME) { entry ->
            val scannedCn by entry.savedStateHandle
                .getStateFlow<String?>(AppDestinations.KEY_SCANNED_CN, null)
                .collectAsStateWithLifecycle()
            val scannedName by entry.savedStateHandle
                .getStateFlow<String?>(AppDestinations.KEY_SCANNED_NAME, null)
                .collectAsStateWithLifecycle()
            val scannedExpiry by entry.savedStateHandle
                .getStateFlow<String?>(AppDestinations.KEY_SCANNED_EXPIRY, null)
                .collectAsStateWithLifecycle()

            HomeScreen(
                onOpenScanner = { navController.navigate(AppDestinations.SCANNER) },
                onOpenSettings = { navController.navigate(AppDestinations.SETTINGS) },
                scannedCn = scannedCn,
                scannedName = scannedName,
                scannedExpiry = scannedExpiry,
                onScanConsumed = {
                    entry.savedStateHandle.remove<String>(AppDestinations.KEY_SCANNED_CN)
                    entry.savedStateHandle.remove<String>(AppDestinations.KEY_SCANNED_NAME)
                    entry.savedStateHandle.remove<String>(AppDestinations.KEY_SCANNED_EXPIRY)
                },
                viewModel = homeViewModel
            )
        }

        composable(route = AppDestinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = homeViewModel
            )
        }

        composable(
            route = AppDestinations.SCANNER,
            deepLinks = listOf(navDeepLink { uriPattern = AppDestinations.DEEP_LINK_SCAN_URI })
        ) {
            val scannerViewModel: ScannerViewModel = viewModel(factory = viewModelFactory)
            ScannerScreen(
                onScanComplete = { cn, name, expiry ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        AppDestinations.KEY_SCANNED_CN, cn
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        AppDestinations.KEY_SCANNED_NAME, name
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        AppDestinations.KEY_SCANNED_EXPIRY, expiry
                    )
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
                viewModel = scannerViewModel
            )
        }
    }

    // ---- Diálogos globales (visibles desde cualquier pantalla MENOS Setup) ----

    val currentDestination by navController.currentBackStackEntryAsState()
    val inSetup = currentDestination?.destination?.route == "setup"

    val showCreateCabinet by homeViewModel.showCreateCabinet.collectAsStateWithLifecycle()
    val cabinetToDelete by homeViewModel.cabinetToDelete.collectAsStateWithLifecycle()
    val transferEvent by homeViewModel.transferEvent.collectAsStateWithLifecycle()

    if (showCreateCabinet && !inSetup) {
        CreateCabinetDialog(
            // Obligatorio si no hay botiquín activo (no depende del valor stale de cabinets)
            isMandatory = homeViewModel.activeCabinet.value == null,
            onCreate = homeViewModel::createCabinet,
            onDismiss = { homeViewModel.onShowCreateCabinet(false) }
        )
    }

    cabinetToDelete?.let { cabinet ->
        DeleteCabinetDialog(
            cabinet = cabinet,
            onConfirm = { homeViewModel.deleteCabinet(cabinet.id) },
            onDismiss = homeViewModel::onDismissDeleteCabinet
        )
    }

    LaunchedEffect(transferEvent) {
        transferEvent?.let {
            if (!inSetup) {
                android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            }
            homeViewModel.onTransferEventShown()
        }
    }
}
