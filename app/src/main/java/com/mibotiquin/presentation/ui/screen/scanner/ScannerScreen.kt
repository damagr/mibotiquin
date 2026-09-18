package com.mibotiquin.presentation.ui.screen.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mibotiquin.R
import com.mibotiquin.presentation.ui.components.MonthYearPickerDialog
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    onScanComplete: (cn: String, name: String?, expiryYearMonth: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val step by viewModel.step.collectAsStateWithLifecycle()
    val cn by viewModel.cn.collectAsStateWithLifecycle()
    val cimaName by viewModel.cimaName.collectAsStateWithLifecycle()
    val cimaFound by viewModel.cimaFound.collectAsStateWithLifecycle()
    val dmExpiry by viewModel.dmExpiry.collectAsStateWithLifecycle()
    val dmConfirmed by viewModel.dmConfirmed.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var showManualExpiryPicker by remember { mutableStateOf(false) }
    var analyzer by remember { mutableStateOf<BarcodeAnalyzer?>(null) }

    // Cada vez que se entra en una etapa de lectura, reiniciar el detector
    LaunchedEffect(step) {
        if (step == ScanStep.ReadCn || step == ScanStep.ReadDataMatrix) {
            analyzer?.reset()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showPermissionDialog = true
    }

    LaunchedEffect(Unit) {
        if (viewModel.useCamera && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    // DataMatrix confirmado (lectura o manual) → entregar resultado a Home y volver
    LaunchedEffect(dmConfirmed) {
        if (dmConfirmed) {
            onScanComplete(cn ?: "", cimaName, dmExpiry)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Capa de cámara SOLO en etapas de lectura con permiso y cámara activada
        val showCamera = hasCameraPermission && viewModel.useCamera &&
                (step == ScanStep.ReadCn || step == ScanStep.ReadDataMatrix)

        if (showCamera) {
            CameraPreviewLayer(
                lifecycleOwner = lifecycleOwner,
                cameraExecutor = cameraExecutor,
                onBarcode = viewModel::onBarcodeScanned,
                onCameraReady = { camera = it },
                onAnalyzerCreated = { analyzer = it }
            )
            ScanOverlay(
                label = if (step == ScanStep.ReadCn) {
                    "Apunta al código de barras de la caja"
                } else {
                    "Apunta al DataMatrix del envase"
                }
            )
            ScannerControls(
                isTorchOn = isTorchOn,
                onTorchToggle = {
                    isTorchOn = !isTorchOn
                    camera?.cameraControl?.enableTorch(isTorchOn)
                },
                onBack = onBack,
                bottomButtonText = if (step == ScanStep.ReadCn) {
                    "Introducir CN a mano"
                } else {
                    "Introducir fecha a mano"
                },
                onBottomButton = {
                    if (step == ScanStep.ReadCn) {
                        viewModel.onManualCnRequested()
                    } else {
                        showManualExpiryPicker = true
                    }
                }
            )
        }

        // Contenido por etapa (exclusivo: nunca se solapa con la cámara)
        when (step) {
            ScanStep.ReadCn -> {
                if (!viewModel.useCamera) {
                    ManualCnScreen(
                        onConfirm = viewModel::onManualCnEntered,
                        onCancel = onBack
                    )
                }
                // si cámara ON: solo la capa de cámara ya pintada
            }

            ScanStep.ManualCn -> {
                ManualCnScreen(
                    onConfirm = viewModel::onManualCnEntered,
                    onCancel = if (viewModel.useCamera) viewModel::onRescan else onBack
                )
            }

            ScanStep.ConsultingCima -> {
                LoadingScreen()
            }

            ScanStep.CnResult -> {
                CnResultScreen(
                    cn = cn,
                    cimaName = cimaName,
                    cimaFound = cimaFound,
                    onContinue = {
                        if (!viewModel.onContinueAfterCima()) {
                            // Cámara OFF → formulario directo con nombre CIMA
                            onScanComplete(cn ?: "", cimaName, null)
                        }
                        // cámara ON → el VM ya pasó a DataMatrix
                    },
                    onManualProduct = {
                        onScanComplete(cn ?: "", null, null)
                    },
                    onRescan = viewModel::onRescan,
                    onCancel = onBack
                )
            }

            ScanStep.ReadDataMatrix -> Unit // solo capa de cámara ya pintada
        }
    }

    // Diálogo: permiso de cámara denegado
    if (showPermissionDialog) {
        AlertDialog(
            shape = androidx.compose.ui.graphics.RectangleShape,
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.scanner_permission_title)) },
            text = { Text(stringResource(R.string.scanner_permission_message)) },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Conceder permiso") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    viewModel.onManualCnRequested()
                }) { Text("Introducir CN a mano") }
            }
        )
    }

    // Picker de caducidad manual (MM/AAAA) — fallo/salto del DataMatrix
    if (showManualExpiryPicker) {
        MonthYearPickerDialog(
            initial = java.time.YearMonth.now().plusYears(1),
            onConfirm = { ym ->
                showManualExpiryPicker = false
                viewModel.onManualExpiry(ym.year, ym.monthValue)
            },
            onDismiss = { showManualExpiryPicker = false }
        )
    }
}

// ---- Componentes ----

@Composable
private fun CameraPreviewLayer(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraExecutor: java.util.concurrent.Executor,
    onBarcode: (String, Boolean) -> Unit,
    onCameraReady: (androidx.camera.core.Camera?) -> Unit,
    onAnalyzerCreated: (BarcodeAnalyzer) -> Unit
) {
    val context = LocalContext.current
    val analyzer = remember { BarcodeAnalyzer(onBarcodeDetected = onBarcode) }
    DisposableEffect(analyzer) {
        onAnalyzerCreated(analyzer)
        onDispose { /* cleanup handled by DisposableEffect de la App */ }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, analyzer) }

                cameraProvider.unbindAll()
                onCameraReady(
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

@Composable
private fun ScanOverlay(label: String?) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size((260 * pulse).dp, (140 * pulse).dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            )
            label?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerControls(
    isTorchOn: Boolean,
    onTorchToggle: () -> Unit,
    onBack: () -> Unit,
    bottomButtonText: String,
    onBottomButton: () -> Unit
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = statusBarPadding + 8.dp, start = 16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White
            )
        }
        IconButton(
            onClick = onTorchToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarPadding + 8.dp, end = 16.dp)
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = if (isTorchOn) {
                    stringResource(R.string.scanner_torch_off)
                } else {
                    stringResource(R.string.scanner_torch_on)
                },
                tint = Color.White
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Button(
                onClick = onBottomButton,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(bottomButtonText)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = "Consultando CIMA…",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ManualCnScreen(
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val valid = input.length == 6 && input.all { it.isDigit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Introduce el CN",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Código Nacional de la esquina de la caja (6 dígitos)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) input = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) { Text("Cancelar") }
            Button(
                onClick = { onConfirm(input) },
                enabled = valid,
                modifier = Modifier.weight(1f)
            ) { Text("Consultar CIMA") }
        }
    }
}

@Composable
private fun CnResultScreen(
    cn: String?,
    cimaName: String?,
    cimaFound: Boolean,
    onContinue: () -> Unit,
    onManualProduct: () -> Unit,
    onRescan: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cimaFound) {
            Text(
                text = "Medicamento encontrado",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = cimaName ?: "",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "CN: ${cn ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "CN ${cn ?: ""} no encontrado",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = "No está en la base de datos CIMA. Puedes introducirlo a mano.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            if (cimaFound) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) { Text("Continuar") }
            } else {
                Button(
                    onClick = onManualProduct,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) { Text("Introducir producto a mano") }
            }
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) { Text("Volver a escanear") }
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) { Text("Cancelar") }
        }
    }
}
