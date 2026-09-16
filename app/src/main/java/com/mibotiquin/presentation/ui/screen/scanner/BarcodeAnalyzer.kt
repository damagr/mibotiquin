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
 * Analyzer de códigos: CN (EAN-13 del envase español) y DataMatrix GS1 (caducidad/lote).
 * El modo (isDataMatrix) decide qué formatos se buscan y qué callback se dispara.
 */
class BarcodeAnalyzer(
    private val isDataMatrix: Boolean,
    private val onBarcodeDetected: (raw: String, isDataMatrix: Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        if (isDataMatrix) {
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX)
                .build()
        } else {
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E
                )
                .build()
        }
    )

    private var hasDetected = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasDetected) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close(); return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { code ->
                    hasDetected = true
                    onBarcodeDetected(code, isDataMatrix)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun reset() {
        hasDetected = false
    }
}
