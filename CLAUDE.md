# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android 点检 (equipment inspection) app — a camera-based QR code scanning and inspection form submission tool for factory/workshop use. Built with Kotlin + Jetpack Compose + Material 3. Currently in **initial scaffold stage** (Android Studio default template, not yet implemented).

## Build & Test Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests (JVM, fast)
./gradlew :app:testDebugUnitTest

# Run a single unit test
./gradlew :app:testDebugUnitTest --tests "com.example.machine_check.ExampleUnitTest"

# Run instrumented tests (requires emulator/device)
./gradlew :app:connectedDebugAndroidTest

# Clean build
./gradlew clean
```

## Architecture

- **UI layer**: Jetpack Compose with Material 3. Single `MainActivity` with `setContent`. Compose theme in `ui/theme/` (supports dynamic color on Android 12+).
- **State management**: ViewModel + StateFlow (MVVM pattern, per the requirements).
- **Networking**: Retrofit 2.9.0 + Gson + OkHttp logging interceptor. Backend at `http://10.0.2.2:5039` (emulator → host localhost). For real devices, use the host's LAN IP instead.
- **Camera/Scanning**: CameraX with ML Kit Barcode Scanning for QR code capture.
- **Local storage**: DataStore (Preferences) for persisting employee ID.

## Key Dependencies & Versions

- AGP 9.2.1, Kotlin 2.0.21, Compose BOM 2024.12.01
- compileSdk 35, minSdk 26, targetSdk 35
- CameraX 1.4.2, ML Kit Barcode Scanning 17.3.0
- Version catalog at `gradle/libs.versions.toml`

## Important: Package Name Discrepancy

- **Current code** uses `com.example.machine_check` (Android Studio default).
- **Requirements spec** (`readme.txt`) specifies `com.machine_check.inspection` and the directory layout `app/src/main/java/com/machine_check/inspection/`.
- When implementing, decide which package to use and refactor accordingly. The `readme.txt` layout under `data/`, `ui/`, and `utils/` sub-packages is the target structure.

## API Endpoints (from readme.txt)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/Inspection/templates/{deviceModel}` | Fetch inspection templates for a device model |
| POST | `/api/Inspection/submit-full` | Submit a completed inspection record |

## Full Requirements

The complete specification is in `readme.txt` (Chinese). It covers: QR code scanning flow (employee ID → device model), dynamic form generation from API templates (normal/abnormal toggle buttons + numeric ranged inputs), validation, and submission with loading/error states. Do not re-summarize here to avoid drift; read `readme.txt` directly for the authoritative spec.

## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. When in doubt, invoke the skill.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Save progress → invoke /context-save
- Resume context → invoke /context-restore
- Author a backlog-ready spec/issue → invoke /spec

## Lessons Learned: Barcode Scanning Enhancement (2026-06-27)

### Camera / Focus
- **Camera2Interop 微距对焦不可靠.** 在不同设备和 CameraX 版本上兼容性差. `Camera2Interop.Extender` 在 CameraX 1.4.2 作用于 Builder 而非 UseCase, 但即使 API 正确, 真机上也导致画面模糊. **结论: 默认连续自动对焦更适合生产环境.**
- `setTargetResolution` 在 CameraX 1.4.2 被标记为 deprecated, 但仍可用.

### Binarization (Otsu/Sauvola)
- **反极性是核心 Bug.** PCB 刻印码是亮码暗底, Data Matrix 的 L 型查找模式需要 TRUE. 初始实现 `pixels <= threshold → true` 把暗底设为 true, 导致查找模式失败. **修正: `pixels > threshold → true`** (亮码 → true).
- **Sauvola 均匀区域退化.** 标准 Sauvola 公式会把均匀暗区抑制为背景. 添加 `stdDev < 1e-10` 守卫, 退化到均值阈值.
- **Otsu 边界条件.** `<= bestT` 比 `< bestT` 更符合 Otsu 统计算法 (背景定义为 `sum[0..t]`). 极端情况 (half 0/half 255) 下 `<` 会导致空 BitMatrix.

### ZXing 集成
- **DataMatrixReader 非线程安全.** 内部维护状态 (MultipleBeanPatternResult). 每次 decode 必须创建新实例. 对象创建开销可忽略.
- **HybridBinarizer 会覆盖预处理结果.** ZXing 内部会重新二值化, 摧毁精心准备的二值化+取反图像. **必须用 IdentityBinarizer** 透传 BitMatrix.
- `kotlinx.coroutines.*` 通配符不包含 `selects` 子包, 需要显式 `import kotlinx.coroutines.selects.select`.

### ML Kit
- **ImageProxy 生命周期竞争.** `mlKitScanner.process()` 返回异步 Task, 协程不等它完成就释放 ImageProxy. ML Kit 可能读已释放的 buffer. **必须用 `suspendCancellableCoroutine` 等待 Task 完成.**
- ML Kit 回调可能在任意线程, `MutableStateFlow.update` 需要主线程调度.

### Y-Plane 提取
- YUV_420_888 的 Y 平面就是灰度图, 直接提取 IntArray 避免 ARGB 中转. 处理 `pixelStride==1` (快速路径) 和 `>1` (通用路径) 两种情况.
- `ByteBuffer.get(int index)` 用绝对索引, 免疫 buffer position 状态问题.

### 整体架构
- ML Kit + ZXing 双通道并行是正确的设计. 通道 0 (ML Kit 原图) 处理纸质码, 通道 1 (自控二值化 + ZXing) 处理 PCB 刻印码. 竞速先到先得.
