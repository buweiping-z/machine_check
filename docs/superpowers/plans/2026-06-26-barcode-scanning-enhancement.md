# Barcode Scanning Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add hybrid ML Kit + ZXing dual-channel barcode scanning with Otsu/Sauvola binarization pipeline, IdentityBinarizer, macro focus, and Y-plane direct extraction for PCB laser-etched Data Matrix support — all with automatic mode detection.

**Architecture:** Dual-channel parallel processing. Channel 0 (ML Kit, raw YUV) handles printed QR/DM codes. Channel 1 (custom Otsu/Sauvola binarization → invert → ZXing DataMatrixReader via IdentityBinarizer passthrough) handles PCB etched codes. Channels race; first result wins. Macro focus via Camera2Interop with correct minimum-focus-distance fallback.

**Tech Stack:** Kotlin, CameraX 1.4.2, ML Kit Barcode Scanning 17.3.0, ZXing Core 3.5.3, Jetpack Compose, kotlinx.coroutines

## Global Constraints

- `app/build.gradle.kts`: add `implementation("com.google.zxing:core:3.5.3")` (hard-code version like existing deps)
- Resolution: `1920×1080` (up from `1280×720`)
- ML Kit formats: `Barcode.FORMAT_QR_CODE | Barcode.FORMAT_DATA_MATRIX` (currently only FORMAT_DATA_MATRIX)
- Threading: ML Kit on single-thread executor; Otsu/Sauvola on `Executors.newFixedThreadPool(2)`
- Sauvola: windowSize=15, k=0.5, R=128.0; center 50% crop for performance
- Dedup: 500ms cooldown on lastScannedCode
- Macro focus: CONTROL_AF_MODE_MACRO; fallback CONTROL_AF_MODE_AUTO + LENS_INFO_MINIMUM_FOCUS_DISTANCE (NOT 0.0f)
- DataMatrixReader: NEW instance per decode call (NOT thread-safe)
- Interface unchanged: `onBarcodeScanned: (String) -> Unit`, `isActive: Boolean`

## File Map

```
app/
├── build.gradle.kts                                    ← MODIFY: add ZXing dep
└── src/
    ├── main/java/com/machine_check/inspection/
    │   ├── utils/
    │   │   └── ImageUtils.kt                           ← MODIFY: add extractGrayscaleFromYuv()
    │   └── ui/components/
    │       ├── IdentityBinarizer.kt                    ← NEW: GrayscaleLuminanceSource + IdentityBinarizer
    │       ├── BinarizationPipeline.kt                 ← NEW: Otsu + Sauvola binarization
    │       ├── BarcodeAnalyzer.kt                      ← NEW: dual-channel orchestrator
    │       └── QrCodeScanner.kt                        ← MODIFY: use BarcodeAnalyzer + macro focus
    └── test/java/com/machine_check/inspection/
        └── ui/components/
            ├── IdentityBinarizerTest.kt                ← NEW
            └── BinarizationPipelineTest.kt             ← NEW
```

---

### Task 1: Add ZXing dependency

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: ZXing `core:3.5.3` on classpath for all subsequent tasks

- [ ] **Step 1: Add dependency**

In `app/build.gradle.kts`, after `implementation("com.google.mlkit:barcode-scanning:17.3.0")` add:

```kotlin
    // ========== ZXing 条码解码 ==========
    implementation("com.google.zxing:core:3.5.3")
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep -i zxing`

Expected: `com.google.zxing:core:3.5.3` appears.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: add ZXing core 3.5.3 dependency

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Add Y-plane extraction to ImageUtils

**Files:**
- Modify: `app/src/main/java/com/machine_check/inspection/utils/ImageUtils.kt`

**Interfaces:**
- Produces: `ImageUtils.extractGrayscaleFromYuv(image: Image): IntArray?`

- [ ] **Step 1: Add function**

Add these imports to ImageUtils.kt:

```kotlin
import android.media.Image
import java.nio.ByteBuffer
```

Add this function inside `object ImageUtils { ... }` after `enhanceContrast()`:

```kotlin
    /**
     * 从 YUV_420_888 Image 的 Y 平面直接提取灰度像素数组
     * 避免 YUV→ARGB Bitmap→灰度 的浪费转换
     */
    fun extractGrayscaleFromYuv(image: Image): IntArray? {
        val yPlane = image.planes.getOrNull(0) ?: return null
        val yBuffer: ByteBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)

        if (pixelStride == 1) {
            for (y in 0 until height) {
                val offset = y * rowStride
                for (x in 0 until width) {
                    pixels[y * width + x] = yBuffer.get(offset + x).toInt() and 0xFF
                }
            }
        } else {
            for (y in 0 until height) {
                val rowOffset = y * rowStride
                for (x in 0 until width) {
                    pixels[y * width + x] = yBuffer.get(rowOffset + x * pixelStride).toInt() and 0xFF
                }
            }
        }
        return pixels
    }
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/machine_check/inspection/utils/ImageUtils.kt
git commit -m "feat: add Y-plane direct extraction to ImageUtils

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Create GrayscaleLuminanceSource and IdentityBinarizer

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/components/IdentityBinarizer.kt`
- Create: `app/src/test/java/com/machine_check/inspection/ui/components/IdentityBinarizerTest.kt`

**Interfaces:**
- Produces:
  - `GrayscaleLuminanceSource(pixels: IntArray, width: Int, height: Int)` extends ZXing `LuminanceSource`
  - `IdentityBinarizer(source: LuminanceSource, prebuiltMatrix: BitMatrix)` extends ZXing `Binarizer`, `getBlackMatrix()` returns prebuiltMatrix unchanged

- [ ] **Step 1: Write unit test**

Create `app/src/test/java/com/machine_check/inspection/ui/components/IdentityBinarizerTest.kt`:

```kotlin
package com.machine_check.inspection.ui.components

import com.google.zxing.BinaryBitmap
import com.google.zxing.common.BitMatrix
import org.junit.Assert.*
import org.junit.Test

class IdentityBinarizerTest {

    @Test
    fun `getBlackMatrix returns prebuilt matrix unchanged`() {
        val matrix = BitMatrix(10, 10)
        matrix.set(5, 5)
        val pixels = IntArray(100) { 128 }
        val source = GrayscaleLuminanceSource(pixels, 10, 10)
        val binarizer = IdentityBinarizer(source, matrix)
        val result = BinaryBitmap(binarizer).blackMatrix
        assertSame(matrix, result)
        assertTrue(result.get(5, 5))
        assertFalse(result.get(0, 0))
    }

    @Test
    fun `getBlackRow matches prebuilt matrix`() {
        val matrix = BitMatrix(8, 8)
        matrix.set(1, 3); matrix.set(3, 3); matrix.set(5, 3); matrix.set(7, 3)
        val pixels = IntArray(64) { 128 }
        val source = GrayscaleLuminanceSource(pixels, 8, 8)
        val row = IdentityBinarizer(source, matrix).getBlackRow(3, null)
        assertNotNull(row)
        assertTrue(row!!.get(1)); assertTrue(row.get(3))
        assertTrue(row.get(5)); assertTrue(row.get(7))
        assertFalse(row.get(0)); assertFalse(row.get(2))
    }

    @Test
    fun `createBinarizer returns new IdentityBinarizer with same matrix`() {
        val matrix = BitMatrix(4, 4)
        val pixels = IntArray(16) { 128 }
        val source = GrayscaleLuminanceSource(pixels, 4, 4)
        val original = IdentityBinarizer(source, matrix)
        val rotated = original.createBinarizer(source) as IdentityBinarizer
        assertSame(matrix, rotated.getBlackMatrix())
    }

    @Test
    fun `GrayscaleLuminanceSource reports correct dimensions`() {
        val source = GrayscaleLuminanceSource(IntArray(48) { 0 }, 8, 6)
        assertEquals(8, source.width)
        assertEquals(6, source.height)
    }

    @Test
    fun `GrayscaleLuminanceSource getRow returns correct values`() {
        val pixels = IntArray(16) { it * 10 }
        val source = GrayscaleLuminanceSource(pixels, 4, 4)
        val row = source.getRow(2, null)
        assertEquals(80.toByte(), row!![0])
        assertEquals(90.toByte(), row[1])
        assertEquals(100.toByte(), row[2])
        assertEquals(110.toByte(), row[3])
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "com.machine_check.inspection.ui.components.IdentityBinarizerTest"`

Expected: compilation error (classes not defined yet)

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/machine_check/inspection/ui/components/IdentityBinarizer.kt`:

```kotlin
package com.machine_check.inspection.ui.components

import com.google.zxing.Binarizer
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitArray
import com.google.zxing.common.BitMatrix

/**
 * 将灰度 IntArray 包装为 ZXing LuminanceSource
 * 仅用于满足 Binarizer 构造函数签名，实际像素数据不被 ZXing 用于二值化
 */
class GrayscaleLuminanceSource(
    private val pixels: IntArray,
    width: Int,
    height: Int
) : LuminanceSource(width, height) {

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val result = row ?: ByteArray(width)
        val offset = y * width
        for (x in 0 until width) result[x] = pixels[offset + x].toByte()
        return result
    }

    override fun getMatrix(): ByteArray {
        val result = ByteArray(width * height)
        for (i in pixels.indices) result[i] = pixels[i].toByte()
        return result
    }

    override fun isCropSupported(): Boolean = false
}

/**
 * 身份二值化器 — 跳过 ZXing 内部二值化，直接透传预处理好的 BitMatrix
 * 防止 HybridBinarizer 覆盖 Otsu/Sauvola 的二值化+取反结果
 */
class IdentityBinarizer(
    source: LuminanceSource,
    private val prebuiltMatrix: BitMatrix
) : Binarizer(source) {

    override fun getBlackRow(y: Int, row: BitArray?): BitArray {
        val array = row ?: BitArray(prebuiltMatrix.width)
        array.clear()
        for (x in 0 until prebuiltMatrix.width) {
            if (prebuiltMatrix.get(x, y)) array.set(x)
        }
        return array
    }

    override fun getBlackMatrix(): BitMatrix = prebuiltMatrix

    override fun createBinarizer(source: LuminanceSource): Binarizer {
        return IdentityBinarizer(source, prebuiltMatrix)
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.machine_check.inspection.ui.components.IdentityBinarizerTest"`

Expected: BUILD SUCCESSFUL, 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/machine_check/inspection/ui/components/IdentityBinarizer.kt
git add app/src/test/java/com/machine_check/inspection/ui/components/IdentityBinarizerTest.kt
git commit -m "feat: add IdentityBinarizer to bypass ZXing re-binarization

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Create BinarizationPipeline (Otsu + Sauvola)

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/components/BinarizationPipeline.kt`
- Create: `app/src/test/java/com/machine_check/inspection/ui/components/BinarizationPipelineTest.kt`

**Interfaces:**
- Produces:
  - `BinarizationPipeline.otsuBinarize(pixels: IntArray, width: Int, height: Int): BitMatrix` — Otsu → binarize → invert
  - `BinarizationPipeline.sauvolaBinarize(pixels: IntArray, width: Int, height: Int, windowSize: Int = 15, k: Double = 0.5, R: Double = 128.0): BitMatrix` — Sauvola → binarize → invert, center 50% crop

- [ ] **Step 1: Write unit test**

Create `app/src/test/java/com/machine_check/inspection/ui/components/BinarizationPipelineTest.kt`:

```kotlin
package com.machine_check.inspection.ui.components

import org.junit.Assert.*
import org.junit.Test

class BinarizationPipelineTest {

    @Test
    fun `otsuBinarize outputs correct dimensions`() {
        val pixels = IntArray(200) { if (it < 100) 0 else 200 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 20, 10)
        assertEquals(20, result.width)
        assertEquals(10, result.height)
    }

    @Test
    fun `otsuBinarize inverts — dark input becomes true`() {
        val pixels = IntArray(100) { 0 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 10, 10)
        assertTrue("dark pixel should become true after invert", result.get(5, 5))
    }

    @Test
    fun `otsuBinarize inverts — bright input becomes false`() {
        val pixels = IntArray(100) { 255 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 10, 10)
        assertFalse("bright pixel should become false after invert", result.get(5, 5))
    }

    @Test
    fun `otsuBinarize separates black and white regions`() {
        val pixels = IntArray(100) { if (it % 10 < 5) 0 else 255 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 10, 10)
        assertTrue(result.get(1, 5))   // left (dark) → true after invert
        assertFalse(result.get(7, 5))  // right (bright) → false after invert
    }

    @Test
    fun `otsuBinarize handles uniform image without crashing`() {
        val pixels = IntArray(100) { 128 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 10, 10)
        assertNotNull(result)
    }

    @Test
    fun `sauvolaBinarize outputs center crop dimensions`() {
        val pixels = IntArray(1600) { 128 }
        val result = BinarizationPipeline.sauvolaBinarize(pixels, 40, 40)
        assertEquals(20, result.width)   // 40 / 2
        assertEquals(20, result.height)
    }

    @Test
    fun `sauvolaBinarize inverts — dark input becomes true`() {
        val pixels = IntArray(1600) { 10 }
        val result = BinarizationPipeline.sauvolaBinarize(pixels, 40, 40)
        assertTrue(result.get(10, 10))
    }

    @Test
    fun `sauvolaBinarize handles minimum size`() {
        val pixels = IntArray(100) { 128 }
        val result = BinarizationPipeline.sauvolaBinarize(pixels, 10, 10, windowSize = 3)
        assertTrue(result.width > 0 && result.height > 0)
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "com.machine_check.inspection.ui.components.BinarizationPipelineTest"`

Expected: compilation error

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/machine_check/inspection/ui/components/BinarizationPipeline.kt`:

```kotlin
package com.machine_check.inspection.ui.components

import com.google.zxing.common.BitMatrix
import kotlin.math.sqrt

/**
 * 二值化处理管道 — Otsu 全局阈值 + Sauvola 自适应阈值
 * 均内置取反操作，为 ZXing DataMatrixReader 准备适合 PCB 刻印码的 BitMatrix
 * 纯 Kotlin，无 Android 依赖，可直接单元测试
 */
object BinarizationPipeline {

    /**
     * Otsu 全局阈值二值化 + 取反
     * 最大化类间方差寻找阈值，光照均匀时效果最好
     */
    fun otsuBinarize(pixels: IntArray, width: Int, height: Int): BitMatrix {
        val total = width * height

        // 256 级灰度直方图
        val histogram = IntArray(256)
        for (p in pixels) histogram[p.coerceIn(0, 255)]++

        // 寻找最大类间方差的阈值
        var bestT = 128
        var maxVar = 0.0
        var sumTotal = 0L
        for (i in 0..255) sumTotal += i.toLong() * histogram[i]

        var wBg = 0
        var sumBg = 0L
        for (t in 0..255) {
            wBg += histogram[t]
            if (wBg == 0 || wBg == total) continue
            sumBg += t.toLong() * histogram[t]
            val wFg = total - wBg
            val sumFg = sumTotal - sumBg
            val meanBg = sumBg.toDouble() / wBg
            val meanFg = sumFg.toDouble() / wFg
            val variance = wBg.toDouble() * wFg * (meanBg - meanFg) * (meanBg - meanFg)
            if (variance > maxVar) { maxVar = variance; bestT = t }
        }

        // 二值化: 暗(<t)=0, 亮(>=t)=1 → 取反: 暗→true, 亮→false
        val matrix = BitMatrix(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] < bestT) matrix.set(x, y)
            }
        }
        return matrix
    }

    /**
     * Sauvola 自适应阈值二值化 + 取反
     * 逐像素局部窗口计算，适应光照不均场景
     * 仅处理中心 50% 区域（性能优化，假设条码对准中心）
     */
    fun sauvolaBinarize(
        pixels: IntArray, width: Int, height: Int,
        windowSize: Int = 15, k: Double = 0.5, R: Double = 128.0
    ): BitMatrix {
        val cropX = width / 4
        val cropY = height / 4
        val cropW = width / 2
        val cropH = height / 2
        val halfWin = windowSize / 2

        val matrix = BitMatrix(cropW, cropH)

        for (cy in 0 until cropH) {
            for (cx in 0 until cropW) {
                val ox = cropX + cx
                val oy = cropY + cy

                var sum = 0.0; var sumSq = 0.0; var count = 0
                val wl = (ox - halfWin).coerceAtLeast(0)
                val wt = (oy - halfWin).coerceAtLeast(0)
                val wr = (ox + halfWin).coerceAtMost(width - 1)
                val wb = (oy + halfWin).coerceAtMost(height - 1)

                for (wy in wt..wb) {
                    val rowOff = wy * width
                    for (wx in wl..wr) {
                        val v = pixels[rowOff + wx].toDouble()
                        sum += v; sumSq += v * v; count++
                    }
                }
                val mean = sum / count
                val variance = (sumSq / count) - mean * mean
                val stdDev = sqrt(variance.coerceAtLeast(0.0))
                val threshold = mean * (1.0 + k * (stdDev / R - 1.0))

                if (pixels[oy * width + ox] < threshold) matrix.set(cx, cy)
            }
        }
        return matrix
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.machine_check.inspection.ui.components.BinarizationPipelineTest"`

Expected: BUILD SUCCESSFUL, 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/machine_check/inspection/ui/components/BinarizationPipeline.kt
git add app/src/test/java/com/machine_check/inspection/ui/components/BinarizationPipelineTest.kt
git commit -m "feat: add BinarizationPipeline with Otsu and Sauvola

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Create BarcodeAnalyzer (multi-channel orchestrator)

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/components/BarcodeAnalyzer.kt`

**Interfaces:**
- Consumes: ImageUtils (Task 2), IdentityBinarizer + GrayscaleLuminanceSource (Task 3), BinarizationPipeline (Task 4)
- Produces: `BarcodeAnalyzer(onBarcodeScanned)` implements `ImageAnalysis.Analyzer`, `isActive: AtomicBoolean`

- [ ] **Step 1: Implement**

Create `app/src/main/java/com/machine_check/inspection/ui/components/BarcodeAnalyzer.kt`:

```kotlin
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

    private val mlKitExecutor = Executors.newSingleThreadExecutor()
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
                mlKitScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.takeIf { it.isNotEmpty() }?.let { reportResult(it) }
                        }
                    }
                    .addOnCompleteListener { /* done */ }
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
                mlKitJob.cancel(); zxingJob.join()
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
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (rawValue == lastScannedCode && (now - lastScanTime) < dedupCooldownMs) return
            lastScannedCode = rawValue; lastScanTime = now
        }
        onBarcodeScanned(rawValue)
    }

    fun close() {
        isActive.set(false)
        analysisScope.cancel()
        mlKitExecutor.shutdown()
        binarizeExecutor.shutdown()
        mlKitScanner.close()
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/machine_check/inspection/ui/components/BarcodeAnalyzer.kt
git commit -m "feat: add BarcodeAnalyzer dual-channel parallel scanner

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Refactor QrCodeScanner to use BarcodeAnalyzer + macro focus

**Files:**
- Modify: `app/src/main/java/com/machine_check/inspection/ui/components/QrCodeScanner.kt`

**Interfaces:**
- Consumes: BarcodeAnalyzer (Task 5)
- Produces: same Composable API — `QrCodeScanner(onBarcodeScanned, isActive, modifier)` unchanged

- [ ] **Step 1: Rewrite QrCodeScanner.kt**

Replace entire content of `app/src/main/java/com/machine_check/inspection/ui/components/QrCodeScanner.kt`:

```kotlin
package com.machine_check.inspection.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * 条码扫描组件 — 自动识别三种条码类型：
 * 1. 纸质打印 QR 码（暗码亮底）
 * 2. 纸质打印 Data Matrix 码（暗码亮底）
 * 3. PCB 基板激光刻印 Data Matrix 码（亮码暗底，5mm×5mm）
 *
 * 内部使用 ML Kit + ZXing 双通道并行处理，无需手动切换模式。
 * 自动启用微距对焦以识别 5mm 尺寸的小码。
 */
@Composable
fun QrCodeScanner(
    onBarcodeScanned: (String) -> Unit,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) showPermissionDeniedDialog = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("需要相机权限") },
            text = { Text("请在系统设置中授予相机权限以使用扫码功能") },
            confirmButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) { Text("确定") }
            }
        )
    }

    if (!hasCameraPermission) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("需要相机权限才能扫码，请授予权限后重试")
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val analyzer = BarcodeAnalyzer(onBarcodeScanned)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1920, 1080))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    // ---- 微距对焦 ----
                    try {
                        val cameraManager = ctx.getSystemService(android.hardware.camera2.CameraManager::class.java)
                        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: "0"
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                        val minFocus = characteristics.get(
                            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
                        ) ?: 0.0f

                        // 优先尝试微距模式
                        Camera2Interop.Extender<ImageAnalysis>(imageAnalysis).apply {
                            setCaptureRequestOption(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_MACRO
                            )
                        }

                        // 降级：AUTO + 最小对焦距离 (NOT 0.0f, which is infinity)
                        // 如果设备支持 MACRO，此设置会覆盖上面的 Extender 调用
                        // 实际运行时由 HAL 选择最佳对焦模式
                        if (minFocus > 0.0f) {
                            Camera2Interop.Extender<ImageAnalysis>(imageAnalysis).apply {
                                setCaptureRequestOption(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_AUTO
                                )
                                setCaptureRequestOption(
                                    CaptureRequest.LENS_FOCUS_DISTANCE,
                                    minFocus
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // 微距不可用，使用默认对焦
                    }

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx), analyzer)

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, imageAnalysis
                        )
                    } catch (_: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        Text(
            text = "将条码置于取景框内",
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            color = Color.White
        )
    }
}
```

**Important:** The macro focus block has a logic issue — if the device supports MACRO, we set MACRO, then unconditionally overwrite with AUTO + minFocus (if minFocus > 0). The intent is to prefer MACRO if available. Fix: only apply the AUTO fallback when MACRO is not supported. The cleanest approach is to try MACRO first, and if the camera characteristics indicate MACRO is available, keep it; otherwise use AUTO + minFocus.

**Corrected macro focus block** (replace the `// ---- 微距对焦 ----` section above):

```kotlin
                    // ---- 微距对焦 ----
                    try {
                        val cameraManager = ctx.getSystemService(
                            android.hardware.camera2.CameraManager::class.java
                        )
                        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: "0"
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                        val minFocus = characteristics.get(
                            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
                        ) ?: 0.0f
                        val availableAfModes = characteristics.get(
                            CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
                        ) ?: IntArray(0)

                        val extender = Camera2Interop.Extender<ImageAnalysis>(imageAnalysis)

                        if (availableAfModes.contains(CaptureRequest.CONTROL_AF_MODE_MACRO)) {
                            // 设备支持微距：直接使用
                            extender.setCaptureRequestOption(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_MACRO
                            )
                        } else if (minFocus > 0.0f) {
                            // 降级：AUTO + 最小对焦距离（0.0f = 无穷远，必须用实际值）
                            extender.setCaptureRequestOption(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO
                            )
                            extender.setCaptureRequestOption(
                                CaptureRequest.LENS_FOCUS_DISTANCE,
                                minFocus
                            )
                        }
                    } catch (_: Exception) {
                        // 微距不可用，使用默认对焦
                    }
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/machine_check/inspection/ui/components/QrCodeScanner.kt
git commit -m "feat: refactor QrCodeScanner with BarcodeAnalyzer + macro focus

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Integration verification

**Files:**
- No new files — build and manual verification

- [ ] **Step 1: Full build**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL, all tests PASS (IdentityBinarizerTest 5 tests + BinarizationPipelineTest 8 tests)

- [ ] **Step 3: Manual verification checklist**

Install debug APK on a device and verify:

1. **Printed QR code** → Scan a QR code → should decode normally (no regression)
2. **Printed Data Matrix** → Scan a printed DM code → should decode
3. **PCB etched 5mm DM code** → Hold close to camera → should decode via ZXing channel
4. **PCB etched code, uneven lighting** → Shadow part of the frame → Sauvola path should handle it
5. **Mode switching** → Rapidly alternate between printed and PCB codes → auto-detect, no manual toggle needed
6. **Dedup** → Hold a code steady → should NOT fire repeated callbacks

- [ ] **Step 4: Commit (if any fixes from verification)**

```bash
git add -A
git commit -m "test: integration verification passed

Co-Authored-By: Claude <noreply@anthropic.com>"
```
