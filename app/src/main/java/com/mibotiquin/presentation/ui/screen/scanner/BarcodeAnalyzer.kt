package com.mibotiquin.presentation.ui.screen.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Analyzer multi-formato: EAN-13/EAN-8/UPC-A/UPC-E (para CN) + DataMatrix (caducidad).
 * El callback recibe el raw and si es DataMatrix.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (raw: String, isDataMatrix: Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_CODE_128
            )
            .build()
    )

    @Volatile
    private var hasDetected = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasDetected) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.let { barcode ->
                    hasDetected = true
                    val isDm = barcode.format == Barcode.FORMAT_DATA_MATRIX
                    barcode.rawValue?.let { onBarcodeDetected(it, isDm) }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun reset() {
        hasDetected = false
    }
}
