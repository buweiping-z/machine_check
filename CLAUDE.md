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
