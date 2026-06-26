# Android 点检 App — Design Spec

**Date:** 2026-06-07
**Status:** Approved

## Overview

Android equipment inspection app. Workers scan QR codes for employee ID and device model, fetch inspection templates from a backend API, fill out a dynamic form, and submit results. Built with Kotlin + Jetpack Compose + Material 3.

## Tech Stack (from readme.txt)

- Kotlin 2.0.21, Jetpack Compose, Material 3
- AGP 9.2.1, compileSdk 35, minSdk 26
- CameraX 1.4.2 + ML Kit Barcode Scanning 17.3.0
- Retrofit 2.9.0 + Gson + OkHttp Logging Interceptor
- DataStore (Preferences), Coroutines, ViewModel + StateFlow

## Navigation

NavHost with two main routes:

- `"scan"` — ScanScreen: handles both employee ID capture and device model scanning
- `"inspection/{deviceModel}"` — InspectionScreen: dynamic form rendered from API templates

ScanScreen has two internal states (not separate NavHost entries):
1. Employee ID step (pre-filled from DataStore, editable + QR scannable)
2. Device model step (QR scan → on success, navigate to inspection screen)

## Package Structure

Base package: `com.machine_check.inspection` (refactor from current `com.example.machine_check`)

```
com.machine_check.inspection/
├── MainActivity.kt                     // NavHost host
├── data/
│   ├── models/                          // InspectionTemplate, FullInspectionRequest, InspectionResultItem
│   ├── network/ApiService.kt           // Retrofit interface
│   ├── network/RetrofitClient.kt       // Retrofit singleton
│   └── repository/InspectionRepository.kt  // Wraps API calls with Result type
├── ui/
│   ├── scan/ScanScreen.kt              // Two-step scan composable
│   ├── scan/ScanViewModel.kt           // Employee ID state, scan results
│   ├── inspection/InspectionScreen.kt  // Dynamic form composable
│   ├── inspection/InspectionViewModel.kt // Template fetch + form state + submit
│   └── components/QrCodeScanner.kt     // CameraX + ML Kit wrapper composable
└── utils/PreferencesManager.kt         // DataStore for employee ID persistence
```

## Data Flow

```
CameraX/ML Kit → barcode → ScanViewModel
                              ↓
Retrofit GET /templates/{model} → InspectionViewModel → Compose UI
Retrofit POST /submit-full ← InspectionViewModel ← Compose UI
DataStore ↔ PreferencesManager (employeeId read/write)
```

## UI States Per Screen

### ScanScreen
- Employee ID: TextField (editable) + scan QR button → CameraX overlay
- Device model: TextField (editable) + scan QR button → CameraX overlay
- "Enter inspection" button navigates to inspection screen

### InspectionScreen
- Loading: CircularProgressIndicator while fetching templates
- Error: Error card with message + retry button
- Empty: "该设备型号暂无点检模板" message
- Form: Dynamic list of inspection items
- Submitting: Button shows CircularProgressIndicator
- Submit success: Clear form, navigate back to scan

## Form Rendering (by itemType)

- **normal_abnormal**: Two toggle buttons ("正常" green / "异常" red). Default unselected. Required.
- **numeric**: OutlinedTextField (decimal keyboard). Compare to [normalMin, normalMax] — green border if valid, red if out of range. Show unit suffix if present.
- **Both**: Optional remark OutlinedTextField below each item.

## Validation Rules

- normal_abnormal: must have a selection (正常 or 异常)
- numeric: must have a non-empty value within [normalMin, normalMax]
- On submit: highlight first invalid field, show Snackbar error

## API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/Inspection/templates/{deviceModel}` | Fetch inspection templates |
| POST | `/api/Inspection/submit-full` | Submit completed inspection |

Base URL: `http://10.0.2.2:5039` (emulator → host localhost)

## Data Models (from readme.txt)

```kotlin
data class InspectionTemplate(
    val id: Int,
    val deviceModel: String,
    val itemName: String,
    val itemType: String,       // "normal_abnormal" or "numeric"
    val unit: String?,
    val normalMin: Double?,
    val normalMax: Double?,
    val sortOrder: Int
)

data class FullInspectionRequest(
    val employeeId: String,
    val deviceModel: String,
    val results: List<InspectionResultItem>
)

data class InspectionResultItem(
    val itemName: String,
    val resultValue: String,
    val isNormal: Boolean,
    val remark: String
)
```

## Error Handling

- Network unavailable: Error state with retry button
- Camera permission denied: Rationale dialog + link to app settings
- API returns empty template list: Informational message
- Submit failure: Snackbar error, keep form data intact
- Submit success: Dialog/notification, clear form, return to scan screen
