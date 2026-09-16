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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mibotiquin.R
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
    val cimaResult by viewModel.cimaResult.collectAsStateWithLifecycle()
    val dmExpiry by viewModel.dataMatrixExpiry.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (viewModel.useCamera && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (step) {
            ScanStep.ReadCn -> {
                if (hasCameraPermission) {
                    CameraPreview(
                        lifecycleOwner = lifecycleOwner,
                        cameraExecutor = cameraExecutor,
                        isDataMatrix = false,
                        onBarcode = { raw, isDm -> viewModel.onBarcodeScanned(raw, isDm) },
                        onCameraReady = { camera = it },
                        isTorchOn = isTorchOn
                    )
                    ScanOverlay()
                    ScannerControls(
                        isTorchOn = isTorchOn,
                        onTorchToggle = {
                            isTorchOn = !isTorchOn
                            camera?.cameraControl?.enableTorch(isTorchOn)
                        },
                        onBack = onBack,
                        hintText = "Encuadra el CN de la esquina de la caja",
                        actionText = "Introducir CN a mano",
                        onAction = viewModel::onManualCnRequested
                    )
                } else {
                    CameraDisabledState(onBack = onBack)
                }
            }

            ScanStep.ManualCn -> {
                ManualCnStep(
                    onConfirm = viewModel::onCnConfirmed,
                    onCancel = {
                        if (viewModel.useCamera) viewModel.onManualCnCancelled() else onBack()
                    }
                )
            }

            ScanStep.ConsultingCima -> {
                ConsultingStep()
            }

            ScanStep.CnNotFound -> {
                CnNotFoundStep(
                    cn = cn,
                    onManualMedication = {
                        // Formulario manual completo en Home (nombre + fecha a mano)
                        onScanComplete(cn ?: "", null, null)
                    },
                    onBack = { viewModel.reset() }
                )
            }

            ScanStep.ReadDataMatrix -> {
                DataMatrixStep(
                    medicamentoName = (cimaResult as? CimaLookupResult.Success)?.medicamento?.nombre,
                    dmExpiry = dmExpiry,
                    onScanDataMatrix = { /* la cámara ya está activa; se activa a continuación */ },
                    onManualDate = {
                        // Continuar sin caducidad (el sheet la pedirá manual)
                        viewModel.skipDataMatrix()
                        onScanComplete(cn ?: "", (cimaResult as? CimaLookupResult.Success)?.medicamento?.nombre, null)
                    },
                    onContinue = {
                        onScanComplete(
                            cn ?: "",
                            (cimaResult as? CimaLookupResult.Success)?.medicamento?.nombre,
                            dmExpiry
                        )
                    },
                    onBack = { viewModel.reset() }
                )
            }
        }

        // Cámara DataMatrix activa dentro de ReadDataMatrix cuando el usuario pulsa escanear
        if (step == ScanStep.ReadDataMatrix && hasCameraPermission && viewModel.useCamera) {
            DataMatrixCameraLayer(
                lifecycleOwner = lifecycleOwner,
                cameraExecutor = cameraExecutor,
                onBarcode = viewModel::onDataMatrixScanned
            )
        }
    }
}

// ---- Capa de cámara ----

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraExecutor: java.util.concurrent.Executor,
    isDataMatrix: Boolean,
    onBarcode: (String, Boolean) -> Unit,
    onCameraReady: (androidx.camera.core.Camera?) -> Unit,
    isTorchOn: Boolean
) {
    val context = LocalContext.current
    val analyzer = remember(isDataMatrix) { BarcodeAnalyzer(isDataMatrix, onBarcode) }

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
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                onCameraReady(cam)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

@Composable
private fun DataMatrixCameraLayer(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraExecutor: java.util.concurrent.Executor,
    onBarcode: (String) -> Unit
) {
    // Reusa el preview general: el analyzer del paso CN ya pasó a DataMatrix
    // mediante reset(); aquí solo mostramos el overlay correspondiente
    ScanOverlay(label = "Escanea el DataMatrix (caducidad y lote)")
}

// ---- Overlay y controles ----

@Composable
private fun ScanOverlay(label: String? = null) {
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
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    )
            )
            label?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
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
    hintText: String,
    actionText: String,
    onAction: () -> Unit
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = hintText,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(actionText, color = Color.White)
            }
        }
    }
}

// ---- Pasos ----

@Composable
private fun ManualCnStep(
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
            modifier = Modifier.padding(vertical = 8.dp)
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            Button(onClick = { onConfirm(input) }, enabled = valid) {
                Text("Consultar")
            }
        }
    }
}

@Composable
private fun ConsultingStep() {
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
private fun CnNotFoundStep(
    cn: String?,
    onManualMedication: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CN ${cn ?: ""} no encontrado",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = "No se ha encontrado este código en CIMA. Puedes introducir el medicamento a mano.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(onClick = onManualMedication, modifier = Modifier.padding(top = 16.dp)) {
            Text("Introducir medicamento a mano")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Volver a escanear")
        }
    }
}

@Composable
private fun DataMatrixStep(
    medicamentoName: String?,
    dmExpiry: String?,
    onScanDataMatrix: () -> Unit,
    onManualDate: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Producto encontrado",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = medicamentoName ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        dmExpiry?.let {
            Text(
                text = "Caducidad: ${formatYearMonth(it)}",
                style = MaterialTheme.typography.titleLarge
            )
        }
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Continuar")
        }
        OutlinedButton(
            onClick = onManualDate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Introducir fecha a mano")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Volver a escanear")
        }
    }
}

@Composable
private fun CameraDisabledState(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.scanner_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.scanner_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onBack) { Text("Volver") }
    }
}

private fun formatYearMonth(yyyyMinusMM: String): String {
    return try {
        val parts = yyyyMinusMM.split("-")
        "${parts[1].padStart(2, '0')}/${parts[0]}"
    } catch (_: Exception) {
        yyyyMinusMM
    }
}