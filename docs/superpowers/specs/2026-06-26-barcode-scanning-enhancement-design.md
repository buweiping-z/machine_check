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
- **Channel 1 (custom binarization + ZXing):** Handles PCB etched codes. We own the full preprocessing pipeline (Otsu/Sauvola binarization → invert → ZXing `DataMatrixReader`), so there's no black-box interference.
- **Channels race in parallel.** First to decode wins; the other is cancelled.
- **Macro focus** via Camera2Interop `CONTROL_AF_MODE_MACRO` with fallback.

## Architecture

### File Changes

```
app/src/main/java/com/machine_check/inspection/
├── ui/components/
│   ├── QrCodeScanner.kt          ← MODIFY: shell only (permissions, preview, lifecycle)
│   ├── BarcodeAnalyzer.kt        ← NEW: ImageAnalysis.Analyzer, multi-channel orchestration
│   └── BinarizationPipeline.kt   ← NEW: Otsu/Sauvola binarization + invert, pure Kotlin
├── utils/
│   └── ImageUtils.kt             ← MODIFY: add YUV→Bitmap conversion helper
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
    └─► Channel 1: raw YUV → yuvToGrayscaleBitmap()
            │
            ├─► Sub-channel A: Otsu binarize → invert → ZXing DataMatrixReader
            └─► Sub-channel B: Sauvola binarize → invert → ZXing DataMatrixReader
                  ↑ coroutine race (async + select), first wins
                  Thread: dedicated single-thread executor
                  
    ⇒ Any channel decodes first → cancel siblings → onBarcodeScanned(rawValue)
    ⇒ Dedup: 500ms cooldown on lastScannedCode
```

### Threading Model

```
CameraX analysis thread (system)
  └─ BarcodeAnalyzer.analyze(imageProxy)
       ├─ yuvToBitmap() — synchronous on analysis thread (~2ms)
       ├─ launch(mlKitExecutor)  { channel 0: ML Kit process }
       └─ launch(binarizeExecutor) {
              race {
                async { otsuInvert(bitmap) }
                async { sauvolaInvert(bitmap, window=15) }
              }
              → winnerBitmap → ZXing decode → callback
          }
```

### Macro Focus Configuration

```kotlin
Camera2Interop.Extender<ImageAnalysis>(imageAnalysis).apply {
    setCaptureRequestOption(
        CaptureRequest.CONTROL_AF_MODE,
        CaptureRequest.CONTROL_AF_MODE_MACRO
    )
}
// Fallback if MACRO not supported:
//   CONTROL_AF_MODE_AUTO + LENS_FOCUS_DISTANCE = 0.0f
```

Resolution increased from `1280×720` to `1920×1080` for ~120px coverage of 5mm codes.

## Binarization Algorithms

### Otsu (Global Threshold)
- Finds optimal threshold `t` that minimizes intra-class variance
- Fast, O(n) single pass after histogram
- Best when: uniform illumination (consistent factory lighting)

### Sauvola (Adaptive Threshold)
- `T(x,y) = mean(x,y) * (1 + k * (stddev(x,y)/R - 1))`
- Window size: 15×15, k=0.5, R=128
- Handles: uneven illumination, copper trace noise, shadow gradients
- Slower but robust — runs in parallel with Otsu, only pays cost when Otsu fails

### Invert
- After binarization: `white ↔ black` swap (255→0, 0→255)
- Trivial pixel op, effectively zero cost

## ZXing Integration

```kotlin
// Dependency (added to app/build.gradle.kts)
implementation("com.google.zxing:core:3.5.3")

// Decode flow
val reader = DataMatrixReader()
val luminance = RGBLuminanceSource(bitmap.width, bitmap.height, bitmap.pixels)
val binaryBitmap = BinaryBitmap(HybridBinarizer(luminance))
val result = reader.decode(binaryBitmap) // returns Result or throws NotFoundException
```

Only `DataMatrixReader` is needed for Channel 1 — QR codes are handled by ML Kit in Channel 0. Reader instances are created once and reused (thread-safe for read-only operations).

## Validation

### Manual Testing
1. Scan printed QR code → should decode via Channel 0 (ML Kit) with no regression
2. Scan printed Data Matrix code → should decode via Channel 0
3. Scan PCB etched 5mm Data Matrix code under factory lighting → should decode via Channel 1 (ZXing)
4. Scan PCB etched code with uneven/shadowed lighting → Sauvola path should succeed if Otsu fails
5. Rapidly alternate between printed and PCB codes → no manual switching, auto-detect

### Unit Testing
- `BinarizationPipelineTest`: verify Otsu threshold calculation, Sauvola output, invert correctness against known test images
- `BarcodeAnalyzerTest`: verify channel selection logic, dedup window behavior

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| ML Kit ignores pre-inverted images | Channel 1 uses ZXing, not ML Kit — full control |
| Otsu fails on PCB with copper noise | Sauvola runs in parallel as fallback |
| Macro focus not supported on device | `CONTROL_AF_MODE_AUTO` + min focus distance fallback |
| 3 concurrent ML Kit/Zxing instances too heavy | Single-thread executors, only 1 frame in flight (STRATEGY_KEEP_ONLY_LATEST) |
