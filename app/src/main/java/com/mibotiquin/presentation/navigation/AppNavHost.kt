package com.mibotiquin.presentation.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.mibotiquin.presentation.ui.components.UpdateAvailableDialog
import com.mibotiquin.presentation.ui.components.UpdateDownloadingDialog
import com.mibotiquin.presentation.ui.components.UpdateErrorDialog
import com.mibotiquin.presentation.ui.components.UpdateReadyDialog
import com.mibotiquin.presentation.ui.screen.home.HomeScreen
import com.mibotiquin.presentation.ui.screen.home.HomeViewModel
import com.mibotiquin.presentation.ui.screen.scanner.ScannerScreen
import com.mibotiquin.presentation.ui.screen.scanner.ScannerViewModel
import com.mibotiquin.presentation.ui.screen.settings.SettingsScreen
import com.mibotiquin.presentation.ui.screen.setup.SetupScreen
import com.mibotiquin.ui.UpdateState

object AppDestinations {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val SETTINGS = "settings"
    const val DEEP_LINK_SCAN_URI = "mibotiquin://scan"
    const val KEY_SCANNED_CN = "scanned_cn"
    const val KEY_SCANNED_NAME = "scanned_name"
    const val KEY_SCANNED_EXPIRY = "scanned_expiry"
    const val KEY_SCANNED_PROSPECTO_URL = "scanned_prospecto_url"
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
            val scannedProspectoUrl by entry.savedStateHandle
                .getStateFlow<String?>(AppDestinations.KEY_SCANNED_PROSPECTO_URL, null)
                .collectAsStateWithLifecycle()

            HomeScreen(
                onOpenScanner = { navController.navigate(AppDestinations.SCANNER) },
                onOpenSettings = { navController.navigate(AppDestinations.SETTINGS) },
                scannedCn = scannedCn,
                scannedName = scannedName,
                scannedExpiry = scannedExpiry,
                scannedProspectoUrl = scannedProspectoUrl,
                onScanConsumed = {
                    entry.savedStateHandle.remove<String>(AppDestinations.KEY_SCANNED_CN)
                    entry.savedStateHandle.remove<String>(AppDestinations.KEY_SCANNED_NAME)
                    entry.savedStateHandle.remove<String>(AppDestinations.KEY_SCANNED_EXPIRY)
                    entry.savedStateHandle.remove<String>(AppDestinations.KEY_SCANNED_PROSPECTO_URL)
                },
                viewModel = homeViewModel
            )
        }

        composable(route = AppDestinations.SETTINGS) {
            val transferViewModel: com.mibotiquin.presentation.ui.screen.settings.LocalTransferViewModel =
                viewModel(factory = viewModelFactory)
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = homeViewModel,
                transferViewModel = transferViewModel
            )
        }

        composable(
            route = AppDestinations.SCANNER,
            deepLinks = listOf(navDeepLink { uriPattern = AppDestinations.DEEP_LINK_SCAN_URI })
        ) {
            val scannerViewModel: ScannerViewModel = viewModel(factory = viewModelFactory)
            ScannerScreen(
                onScanComplete = { cn, name, expiry, prospectoUrl ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        AppDestinations.KEY_SCANNED_CN, cn
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        AppDestinations.KEY_SCANNED_NAME, name
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        AppDestinations.KEY_SCANNED_EXPIRY, expiry
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        AppDestinations.KEY_SCANNED_PROSPECTO_URL, prospectoUrl
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

    // Fichero entrante (share/open json) → importar al llegar
    val incomingImportUri by container.incomingImportUri.collectAsStateWithLifecycle()
    LaunchedEffect(incomingImportUri) {
        incomingImportUri?.let { uriString ->
            homeViewModel.importCabinet(android.net.Uri.parse(uriString))
            container.incomingImportUri.value = null
        }
    }

    // ---- Diálogos de actualización (GLOBALES: visibles desde cualquier pantalla) ----

    val updateState by homeViewModel.updateState.collectAsStateWithLifecycle()

    // Permiso especial "instalar apps desconocidas" (API 26+): al volver de Ajustes, instala
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val st = homeViewModel.updateState.value
        if (st is UpdateState.ReadyToInstall && !homeViewModel.needsInstallPermission()) {
            homeViewModel.installApk(st.file)
        }
    }

    when (val state = updateState) {
        is UpdateState.Available -> {
            UpdateAvailableDialog(
                release = state.release,
                onUpdate = { homeViewModel.startUpdate(state.release) },
                onDismiss = homeViewModel::dismissUpdateDialog
            )
        }
        is UpdateState.Preparing -> {
            UpdateDownloadingDialog(
                progress = 0,
                preparing = true,
                onCancel = homeViewModel::cancelUpdate
            )
        }
        is UpdateState.Downloading -> {
            UpdateDownloadingDialog(
                progress = state.progress,
                onCancel = homeViewModel::cancelUpdate
            )
        }
        is UpdateState.ReadyToInstall -> {
            UpdateReadyDialog(
                onInstall = {
                    val file = state.file
                    if (homeViewModel.needsInstallPermission()) {
                        // Abrir ajustes de "fuentes desconocidas"; al volver, el launcher instala
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                        ).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        installPermissionLauncher.launch(intent)
                    } else {
                        homeViewModel.installApk(file)
                    }
                },
                onDismiss = homeViewModel::dismissUpdateDialog
            )
        }
        is UpdateState.Error -> {
            UpdateErrorDialog(
                message = state.message,
                onDismiss = homeViewModel::dismissUpdateError
            )
        }
        else -> Unit
    }
}
