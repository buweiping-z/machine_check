package com.machine_check.inspection.ui.components

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.datamatrix.DataMatrixReader
import com.machine_check.inspection.utils.ImageUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 双通道条码分析器
 * 通道 0: ML Kit 原图 → 纸质打印 QR/DM 码
 * 通道 1: Y平面提取 → Otsu/Sauvola 并行竞速 → ZXing → PCB 刻印 DM 码
 * 两通道并行竞速，先到先得，500ms 去重冷却
 */
@OptIn(ExperimentalGetImage::class)
class BarcodeAnalyzer(
    private val onBarcodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    val isActive = AtomicBoolean(true)

    private val binarizeExecutor = Executors.newFixedThreadPool(2)

    @Volatile private var lastScannedCode: String? = null
    @Volatile private var lastScanTime: Long = 0L
    private val dedupCooldownMs = 500L

    private val mlKitScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE or Barcode.FORMAT_DATA_MATRIX)
            .build()
    )

    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun analyze(imageProxy: ImageProxy) {
        if (!isActive.get()) { imageProxy.close(); return }

        val now = System.currentTimeMillis()
        if (now - lastScanTime < dedupCooldownMs) { imageProxy.close(); return }

        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        // 通道 0: ML Kit
        val mlKitJob = analysisScope.launch {
            try {
                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                suspendCancellableCoroutine<Unit> { cont ->
                    mlKitScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                barcode.rawValue?.takeIf { it.isNotEmpty() }?.let { reportResult(it) }
                            }
                        }
                        .addOnCompleteListener {
                            if (cont.isActive) cont.resume(Unit) {}
                        }
                }
            } catch (_: Exception) { }
        }

        // 通道 1: ZXing
        val zxingJob = analysisScope.launch(binarizeExecutor.asCoroutineDispatcher()) {
            try {
                val pixels = ImageUtils.extractGrayscaleFromYuv(mediaImage) ?: return@launch
                val w = imageProxy.width; val h = imageProxy.height

                val result = withTimeoutOrNull(1500L) {
                    coroutineScope {
                        val dOtsu = async {
                            try { decodeDataMatrix(BinarizationPipeline.otsuBinarize(pixels, w, h)) }
                            catch (_: Exception) { null }
                        }
                        val dSauvola = async {
                            try { decodeDataMatrix(BinarizationPipeline.sauvolaBinarize(pixels, w, h)) }
                            catch (_: Exception) { null }
                        }
                        val winner = withTimeout(1200L) { select { dOtsu.onAwait { it }; dSauvola.onAwait { it } } }
                        dOtsu.cancel(); dSauvola.cancel()
                        winner
                    }
                }
                if (result != null) { reportResult(result); mlKitJob.cancel() }
            } catch (_: CancellationException) { }
              catch (_: Exception) { }
        }

        analysisScope.launch {
            try {
                withTimeout(2000L) { mlKitJob.join(); zxingJob.join() }
            } catch (_: TimeoutCancellationException) {
                mlKitJob.cancel(); withTimeout(500L) { zxingJob.join() }
            } finally { imageProxy.close() }
        }
    }

    private fun decodeDataMatrix(matrix: com.google.zxing.common.BitMatrix): String? {
        val reader = DataMatrixReader() // 新实例，非线程安全
        val w = matrix.width; val h = matrix.height
        val dummyPixels = IntArray(w * h) { 128 }
        val source = GrayscaleLuminanceSource(dummyPixels, w, h)
        val bitmap = BinaryBitmap(IdentityBinarizer(source, matrix))
        return try { reader.decode(bitmap).text } catch (_: NotFoundException) { null }
    }

    private fun reportResult(rawValue: String) {
        if (!isActive.get()) return
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (rawValue == lastScannedCode && (now - lastScanTime) < dedupCooldownMs) return
            lastScannedCode = rawValue; lastScanTime = now
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onBarcodeScanned(rawValue)
        }
    }

    fun close() {
        isActive.set(false)
        analysisScope.cancel()
        binarizeExecutor.shutdown()
        mlKitScanner.close()
    }
}
