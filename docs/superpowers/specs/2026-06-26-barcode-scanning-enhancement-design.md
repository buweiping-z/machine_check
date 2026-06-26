# Barcode Scanning Enhancement — PCB Laser-Etched Data Matrix Support

**Date:** 2026-06-26  
**Status:** Approved, pending implementation

## Problem

The current `QrCodeScanner` only handles dark-on-light barcodes (printed QR/DM codes on paper). PCB substrates have laser-etched Data Matrix codes that are **light-on-dark** (bright code on dark copper/FR4 background). ML Kit's internal binarization pipeline is a black box — feeding it pre-inverted images may conflict with its own normalization, producing unpredictable results. Additionally, the etched codes are as small as 5mm×5mm, requiring macro focus.

The scanner must handle **three modes** seamlessly, with automatic detection (no manual mode switching):
1. Printed QR codes (employee ID, device model)
2. Printed Data Matrix codes
3. PCB laser-etched Data Matrix codes (inverted contrast)

## Approach

**Hybrid ML Kit + ZXing architecture with dual-channel parallel processing.**

- **Channel 0 (ML Kit, raw YUV):** Handles printed codes natively — ML Kit's strength.
- **Channel 1 (custom binarization + ZXing):** Handles PCB etched codes. We own the full preprocessing pipeline (Otsu/Sauvola binarization → invert → ZXing `DataMatrixReader` via `IdentityBinarizer` that passes through our pre-processed bitmap without re-binarizing).
- **Channels race in parallel.** First to decode wins; the other is cancelled.
- **Macro focus** via Camera2Interop `CONTROL_AF_MODE_MACRO` with correct fallback to minimum focus distance.

## Architecture

### File Changes

```
app/src/main/java/com/machine_check/inspection/
├── ui/components/
│   ├── QrCodeScanner.kt          ← MODIFY: shell only (permissions, preview, lifecycle)
│   ├── BarcodeAnalyzer.kt        ← NEW: ImageAnalysis.Analyzer, multi-channel orchestration
│   ├── BinarizationPipeline.kt   ← NEW: Otsu/Sauvola binarization + invert, pure Kotlin
│   └── IdentityBinarizer.kt      ← NEW: ZXing custom Binarizer (passthrough, no re-binarize)
├── utils/
│   └── ImageUtils.kt             ← MODIFY: add Y-plane extraction helper
└── build.gradle.kts (app)         ← MODIFY: add ZXing core dependency
```

### Data Flow Per Frame

```
CameraX ImageProxy (YUV_420_888)
    │
    ├─► Channel 0: raw YUV → InputImage.fromMediaImage() → ML Kit BarcodeScanner
    │       Formats: QR_CODE | DATA_MATRIX
    │       Thread: single-thread executor (existing)
    │
    └─► Channel 1: Y plane extracted directly → IntArray(grayscale)
            │       ↑ extractGrayscaleFromYuv() — no ARGB conversion
            │
            ├─► Sub-channel A: Otsu binarize → invert → BitMatrix → ZXing DataMatrixReader
            └─► Sub-channel B: Sauvola binarize → invert → BitMatrix → ZXing DataMatrixReader
                  ↑ coroutine race (async + select), first wins
                  Thread: 2-thread pool (true parallel race)

    ⇒ Any channel decodes first → cancel siblings → onBarcodeScanned(rawValue)
    ⇒ Dedup: 500ms cooldown on lastScannedCode
```

### Threading Model

```
CameraX analysis thread (system)
  └─ BarcodeAnalyzer.analyze(imageProxy)
       ├─ extractGrayscaleFromYuv() — synchronous on analysis thread (<0.5ms)
       ├─ launch(mlKitExecutor)  { channel 0: ML Kit process }
       └─ launch(binarizeExecutor) {        ← 2-thread pool
              race {
                async { otsuBinarize + invert → BitMatrix → decode() }
                async { sauvolaBinarize + invert → BitMatrix → decode() }
              }
              → winner result → callback
          }
```

`binarizeExecutor` = `Executors.newFixedThreadPool(2)` — both Otsu and Sauvola run truly in parallel, not serialized on a single thread.

### Macro Focus Configuration

```kotlin
// Query device capabilities once at init
val characteristics = cameraManager.getCameraCharacteristics(cameraId)
val minFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)

// Apply macro focus
Camera2Interop.Extender<ImageAnalysis>(imageAnalysis).apply {
    setCaptureRequestOption(
        CaptureRequest.CONTROL_AF_MODE,
        CaptureRequest.CONTROL_AF_MODE_MACRO
    )
}
// Fallback if MACRO not supported:
//   CONTROL_AF_MODE_AUTO
//   + LENS_FOCUS_DISTANCE = minFocus (NOT 0.0f — 0.0f = infinity in Camera2 API)
```

Resolution increased from `1280×720` to `1920×1080` for ~120px coverage of 5mm codes.

## Binarization Algorithms

### Y-Plane Extraction (no ARGB conversion)

CameraX delivers `YUV_420_888`. The Y plane IS the grayscale image. Extract it directly:

```kotlin
fun extractGrayscaleFromYuv(image: ImageProxy): IntArray {
    val yBuffer = image.planes[0].buffer       // Y plane = luminance
    val rowStride = image.planes[0].rowStride
    val pixelStride = image.planes[0].pixelStride
    val width = image.width
    val height = image.height

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val gray = yBuffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
            pixels[y * width + x] = gray
        }
    }
    return pixels
}
```

This avoids the wasteful YUV → ARGB Bitmap → gray extract round-trip. Cost: <0.5ms vs ~2ms for the Bitmap path.

### Otsu (Global Threshold)
- Input: `IntArray` grayscale pixels
- Finds optimal threshold `t` that minimizes intra-class variance
- Output: binarized `BitMatrix` (0/1), then inverted (0↔1)
- Fast, O(n) single pass after histogram

### Sauvola (Adaptive Threshold)
- Input: `IntArray` grayscale pixels
- `T(x,y) = mean(x,y) * (1 + k * (stddev(x,y)/R - 1))`
- Window size: 15×15, k=0.5, R=128
- Output: binarized `BitMatrix` (0/1), then inverted (0↔1)
- **Performance note:** Pure Kotlin Sauvola on 1920×1080 with 15×15 window is CPU-intensive. Mitigations:
  - Process only the **center 50% crop** of the frame (assumes user centers the code)
  - If real-world performance is still insufficient, future optimization path: JNI/C++ or RenderScript

### Invert
- After binarization: swap 0↔1 in the `BitMatrix`
- Done inside the binarization functions, no separate pass

## ZXing Integration

### Dependency

```kotlin
// app/build.gradle.kts
implementation("com.google.zxing:core:3.5.3")
```

### IdentityBinarizer — Skip ZXing's Internal Re-binarization

This is the critical piece. ZXing's `HybridBinarizer` would re-process our carefully prepared binary image — destroying the inversion we just did. Instead, implement a custom `Binarizer` that passes through our `BitMatrix` unchanged:

```kotlin
class IdentityBinarizer(source: LuminanceSource, private val prebuiltMatrix: BitMatrix) 
    : Binarizer(source) {
    
    override fun getBlackRow(y: Int, row: BitArray?): BitArray {
        val array = row ?: BitArray(prebuiltMatrix.width)
        for (x in 0 until prebuiltMatrix.width) {
            if (prebuiltMatrix.get(x, y)) array.set(x)
            else array.clear(x)
        }
        return array
    }

    override fun getBlackMatrix(): BitMatrix = prebuiltMatrix

    override fun createBinarizer(source: LuminanceSource): Binarizer {
        return IdentityBinarizer(source, prebuiltMatrix)
    }
}
```

### Decode Flow (per attempt, creates fresh reader instance)

```kotlin
fun decode(matrix: BitMatrix): String? {
    // ZXing DataMatrixReader is NOT thread-safe — create new instance per call
    val reader = DataMatrixReader()
    val source = BitMatrixLuminanceSource(matrix)
    val bitmap = BinaryBitmap(IdentityBinarizer(source, matrix))
    return try {
        val result = reader.decode(bitmap)
        result.text
    } catch (e: NotFoundException) {
        null
    }
}
```

### Key Thread-Safety Rule

**`DataMatrixReader` instances are never reused.** Each decode attempt creates a fresh instance. The object allocation cost is negligible (~hundreds of bytes, no heavy init).

## Validation

### Manual Testing
1. Scan printed QR code → should decode via Channel 0 (ML Kit) with no regression
2. Scan printed Data Matrix code → should decode via Channel 0
3. Scan PCB etched 5mm Data Matrix code under factory lighting → should decode via Channel 1 (ZXing)
4. Scan PCB etched code with uneven/shadowed lighting → Sauvola path should succeed if Otsu fails
5. Rapidly alternate between printed and PCB codes → no manual switching, auto-detect

### Unit Testing
- `BinarizationPipelineTest`: verify Otsu threshold calculation, Sauvola output, invert correctness against known test images
- `IdentityBinarizerTest`: verify `getBlackMatrix()` returns the same matrix passed in
- `BarcodeAnalyzerTest`: verify channel selection logic, dedup window behavior

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| ML Kit ignores pre-inverted images | Channel 1 uses ZXing + IdentityBinarizer — full control, no black-box re-processing |
| ZXing `HybridBinarizer` re-binarizes our output | `IdentityBinarizer` passes through our `BitMatrix` unchanged |
| `DataMatrixReader` not thread-safe | New instance per decode call |
| Otsu fails on PCB with copper noise | Sauvola runs in parallel as fallback |
| Macro focus: `0.0f` = infinity, not macro | Use `LENS_INFO_MINIMUM_FOCUS_DISTANCE` from camera characteristics |
| Single-thread executor serializes Otsu/Sauvola | 2-thread pool for true parallel race |
| YUV→ARGB→gray wastes CPU | Extract Y plane directly |
| Sauvola on 1080p is CPU-heavy | Center 50% crop; note JNI as future optimization path |
