package com.example.qrcode.util

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * ML Kit 二维码分析器，实现 CameraX [ImageAnalysis.Analyzer]。
 *
 * 仅识别 [Barcode.FORMAT_QR_CODE]。识别成功回调 [onResult]，同一二维码在回调处理期间不会重复触发。
 */
class BarcodeAnalyzer(
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @Volatile
    private var busy = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (busy) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        busy = true
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val raw = barcode.rawValue
                    if (!raw.isNullOrEmpty()) {
                        onResult(raw)
                        break
                    }
                }
            }
            .addOnCompleteListener {
                busy = false
                imageProxy.close()
            }
    }
}
