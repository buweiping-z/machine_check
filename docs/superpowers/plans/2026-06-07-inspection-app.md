# 点检 App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete Android inspection app with QR code scanning, dynamic form generation from API templates, and form submission.

**Architecture:** MVVM with Jetpack Compose. Two-screen NavHost (Scan → Inspection). CameraX + ML Kit for barcode scanning. Retrofit for REST API calls. DataStore for local preferences. StateFlow-based UI state management.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.12.01, Material 3, CameraX 1.4.2, ML Kit Barcode 17.3.0, Retrofit 2.9.0, OkHttp 4.12.0, DataStore 1.1.1, Coroutines

---

### Task 1: Update build configuration

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Update namespace and add missing dependencies in `app/build.gradle.kts`**

Replace the entire file content:

```kotlin
plugins {
    id(libs.plugins.android.application.get().pluginId)
    id(libs.plugins.kotlin.compose.get().pluginId)
}

android {
    namespace = "com.machine_check.inspection"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.machine_check.inspection"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // ========== Android 核心 ==========
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ========== Jetpack Compose ==========
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ========== Navigation ==========
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // ========== ViewModel + Compose 集成 ==========
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // ========== DataStore ==========
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ========== CameraX 扫码 ==========
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-mlkit-vision:1.4.2")

    // ========== ML Kit 条码识别 ==========
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // ========== 网络请求 ==========
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ========== 测试 ==========
    testImplementation(libs.junit)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // ========== Debug 工具 ==========
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 2: Add camera permission to `app/src/main/AndroidManifest.xml`**

Replace the entire file content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.CAMERA" />

    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Machine_check">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Machine_check"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 3: Sync project**

Run: Open project in Android Studio and Sync Gradle, or run:
```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew --refresh-dependencies
```

Expected: BUILD SUCCESSFUL (may have warnings about unused deps — that's fine, they'll be used in later tasks)

---

### Task 2: Create data models

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/data/models/Models.kt`

- [ ] **Step 1: Create the models file**

```kotlin
package com.machine_check.inspection.data.models

import com.google.gson.annotations.SerializedName

/**
 * 点检模板（从服务端获取的单条点检项）
 */
data class InspectionTemplate(
    val id: Int,
    val deviceModel: String,
    val itemName: String,
    val itemType: String,          // "normal_abnormal" 或 "numeric"
    val unit: String?,
    val normalMin: Double?,
    val normalMax: Double?,
    val sortOrder: Int
)

/**
 * 提交点检的完整请求体
 */
data class FullInspectionRequest(
    val employeeId: String,
    val deviceModel: String,
    val results: List<InspectionResultItem>
)

/**
 * 单条点检结果
 */
data class InspectionResultItem(
    val itemName: String,
    val resultValue: String,
    val isNormal: Boolean,
    val remark: String
)

/**
 * 服务端提交成功响应
 */
data class SubmitResponse(
    val message: String,
    val success: Boolean
)

/**
 * 服务端错误响应
 */
data class ErrorResponse(
    val message: String?
)
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 3: Create network layer

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/data/network/ApiService.kt`
- Create: `app/src/main/java/com/machine_check/inspection/data/network/RetrofitClient.kt`

- [ ] **Step 1: Create ApiService (Retrofit interface)**

```kotlin
package com.machine_check.inspection.data.network

import com.machine_check.inspection.data.models.FullInspectionRequest
import com.machine_check.inspection.data.models.InspectionTemplate
import com.machine_check.inspection.data.models.SubmitResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 点检 API 接口定义
 */
interface ApiService {

    /** 根据设备型号获取点检模板列表 */
    @GET("/api/Inspection/templates/{deviceModel}")
    suspend fun getTemplates(
        @Path("deviceModel") deviceModel: String
    ): Response<List<InspectionTemplate>>

    /** 提交完整点检记录 */
    @POST("/api/Inspection/submit-full")
    suspend fun submitFullInspection(
        @Body request: FullInspectionRequest
    ): Response<SubmitResponse>
}
```

- [ ] **Step 2: Create RetrofitClient (singleton)**

```kotlin
package com.machine_check.inspection.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端单例
 * 模拟器使用 10.0.2.2 访问宿主机 localhost
 * 真机调试需改为电脑局域网 IP
 */
object RetrofitClient {

    // ========== 配置：修改此处切换环境 ==========
    private const val BASE_URL = "http://10.0.2.2:5039"
    // 真机调试示例: "http://192.168.1.100:5039"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
```

- [ ] **Step 3: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 4: Create PreferencesManager (DataStore)

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/utils/PreferencesManager.kt`

- [ ] **Step 1: Create PreferencesManager**

```kotlin
package com.machine_check.inspection.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 应用级别的 DataStore 扩展属性 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "inspection_prefs")

/**
 * DataStore 偏好设置管理器
 * 用于持久化存储工号等信息
 */
class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_EMPLOYEE_ID = stringPreferencesKey("employee_id")
    }

    /** 获取存储的工号（Flow，实时监听变化） */
    val employeeId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_EMPLOYEE_ID] ?: ""
    }

    /** 保存工号 */
    suspend fun saveEmployeeId(employeeId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EMPLOYEE_ID] = employeeId
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 5: Create InspectionRepository

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/data/repository/InspectionRepository.kt`

- [ ] **Step 1: Create InspectionRepository**

```kotlin
package com.machine_check.inspection.data.repository

import com.machine_check.inspection.data.models.FullInspectionRequest
import com.machine_check.inspection.data.models.InspectionTemplate
import com.machine_check.inspection.data.models.SubmitResponse
import com.machine_check.inspection.data.network.RetrofitClient

/**
 * 点检数据仓库
 * 封装网络请求，统一处理成功/失败结果
 */
class InspectionRepository {

    private val api = RetrofitClient.apiService

    /** 获取指定设备型号的点检模板列表 */
    suspend fun getTemplates(deviceModel: String): Result<List<InspectionTemplate>> {
        return try {
            val response = api.getTemplates(deviceModel)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(
                    Exception("获取模板失败: ${response.code()} ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络连接失败，请检查网络设置", e))
        }
    }

    /** 提交完整点检记录 */
    suspend fun submitInspection(request: FullInspectionRequest): Result<SubmitResponse> {
        return try {
            val response = api.submitFullInspection(request)
            if (response.isSuccessful) {
                Result.success(
                    response.body() ?: SubmitResponse("提交成功", true)
                )
            } else {
                Result.failure(
                    Exception("提交失败: ${response.code()} ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络连接失败，请检查网络设置", e))
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 6: Move theme files to new package

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/theme/Color.kt`
- Create: `app/src/main/java/com/machine_check/inspection/ui/theme/Type.kt`
- Create: `app/src/main/java/com/machine_check/inspection/ui/theme/Theme.kt`
- Delete: `app/src/main/java/com/example/machine_check/` (entire old package directory)

- [ ] **Step 1: Create Color.kt in new package**

```kotlin
package com.machine_check.inspection.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

- [ ] **Step 2: Create Type.kt in new package**

```kotlin
package com.machine_check.inspection.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 3: Create Theme.kt in new package (update package + imports)**

```kotlin
package com.machine_check.inspection.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun MachineCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 4: Delete old package directory**

Run:
```bash
rm -rf "D:\Android\AndroidStudioProjects\machine_check\app\src\main\java\com\example"
rm -rf "D:\Android\AndroidStudioProjects\machine_check\app\src\test\java\com\example"
rm -rf "D:\Android\AndroidStudioProjects\machine_check\app\src\androidTest\java\com\example"
```

- [ ] **Step 5: Build to verify everything compiles**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 7: Create QrCodeScanner composable

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/components/QrCodeScanner.kt`

- [ ] **Step 1: Create QrCodeScanner**

```kotlin
package com.machine_check.inspection.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * QR 码扫描组件
 * 使用 CameraX + ML Kit 进行实时条码识别
 *
 * @param onBarcodeScanned 扫描到条码后的回调，返回条码内容
 * @param isActive 是否激活扫描（false 时暂停分析）
 * @param modifier Modifier
 */
@Composable
fun QrCodeScanner(
    onBarcodeScanned: (String) -> Unit,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    ) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var lastScannedCode by remember { mutableStateOf<String?>(null) }

    // 相机权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            showPermissionDeniedDialog = true
        }
    }

    // 首次加载请求权限
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 权限被拒对话框
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("需要相机权限") },
            text = { Text("请在系统设置中授予相机权限以使用扫码功能") },
            confirmButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("需要相机权限才能扫码，请授予权限后重试")
        }
        return
    }

    // 相机预览 + 条码分析
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // 预览用例
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    // 图像分析用例（条码扫描）
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val barcodeScanner = BarcodeScanning.getClient()
                    val analysisExecutor = Executors.newSingleThreadExecutor()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy: ImageProxy ->
                        if (!isActive) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage, imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (rawValue != null && rawValue != lastScannedCode) {
                                            lastScannedCode = rawValue
                                            onBarcodeScanned(rawValue)
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) {
                        // 绑定失败，忽略
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 扫描框提示
        Text(
            text = "将二维码置于取景框内",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            color = androidx.compose.ui.graphics.Color.White
        )
    }

    // 组件销毁时清理
    DisposableEffect(Unit) {
        onDispose {
            lastScannedCode = null
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 8: Create ScanViewModel

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/scan/ScanViewModel.kt`

- [ ] **Step 1: Create ScanViewModel**

```kotlin
package com.machine_check.inspection.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.machine_check.inspection.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 扫码页面 UI 状态
 */
data class ScanUiState(
    val employeeId: String = "",
    val deviceModel: String = "",
    val currentScanTarget: ScanTarget = ScanTarget.EMPLOYEE_ID,
    val isScanning: Boolean = false,
    val scanResult: String? = null,
    val navigateToInspection: String? = null  // 非 null 时触发导航
)

/**
 * 扫码目标（工号 or 设备型号）
 */
enum class ScanTarget {
    EMPLOYEE_ID,
    DEVICE_MODEL
}

/**
 * 扫码页面 ViewModel
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        // 启动时加载已保存的工号
        viewModelScope.launch {
            val savedEmployeeId = preferencesManager.employeeId.first()
            _uiState.update { it.copy(employeeId = savedEmployeeId) }
        }
    }

    /** 更新工号输入 */
    fun onEmployeeIdChange(employeeId: String) {
        _uiState.update { it.copy(employeeId = employeeId) }
    }

    /** 更新设备型号输入 */
    fun onDeviceModelChange(deviceModel: String) {
        _uiState.update { it.copy(deviceModel = deviceModel) }
    }

    /** 打开扫码器 — 设置扫码目标 */
    fun startScanning(target: ScanTarget) {
        _uiState.update {
            it.copy(
                currentScanTarget = target,
                isScanning = true,
                scanResult = null
            )
        }
    }

    /** 关闭扫码器 */
    fun stopScanning() {
        _uiState.update {
            it.copy(isScanning = false, scanResult = null)
        }
    }

    /** 扫码结果回调 */
    fun onBarcodeScanned(barcode: String) {
        when (_uiState.value.currentScanTarget) {
            ScanTarget.EMPLOYEE_ID -> {
                _uiState.update {
                    it.copy(
                        employeeId = barcode,
                        scanResult = barcode,
                        isScanning = false
                    )
                }
                // 保存工号到 DataStore
                viewModelScope.launch {
                    preferencesManager.saveEmployeeId(barcode)
                }
            }
            ScanTarget.DEVICE_MODEL -> {
                _uiState.update {
                    it.copy(
                        deviceModel = barcode,
                        scanResult = barcode,
                        isScanning = false
                    )
                }
            }
        }
    }

    /** 导航到点检页面 */
    fun navigateToInspection() {
        val deviceModel = _uiState.value.deviceModel.trim()
        if (deviceModel.isNotEmpty()) {
            _uiState.update { it.copy(navigateToInspection = deviceModel) }
        }
    }

    /** 导航完成回调（重置导航状态） */
    fun onNavigationComplete() {
        _uiState.update { it.copy(navigateToInspection = null) }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 9: Create ScanScreen

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/scan/ScanScreen.kt`

- [ ] **Step 1: Create ScanScreen**

```kotlin
package com.machine_check.inspection.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.machine_check.inspection.ui.components.QrCodeScanner

/**
 * 扫码页面
 * 步骤1: 输入/扫描工号 → 步骤2: 输入/扫描设备型号 → 进入点检
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateToInspection: (deviceModel: String) -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 导航触发
    LaunchedEffect(uiState.navigateToInspection) {
        uiState.navigateToInspection?.let { deviceModel ->
            onNavigateToInspection(deviceModel)
            viewModel.onNavigationComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备点检") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (uiState.isScanning) {
            // ========== 扫码全屏模式 ==========
            Box(modifier = Modifier.fillMaxSize()) {
                QrCodeScanner(
                    onBarcodeScanned = { barcode -> viewModel.onBarcodeScanned(barcode) },
                    isActive = uiState.isScanning,
                    modifier = Modifier.fillMaxSize()
                )

                // 扫描目标提示
                Text(
                    text = if (uiState.currentScanTarget == ScanTarget.EMPLOYEE_ID)
                        "请扫描工号二维码" else "请扫描设备型号二维码",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 取消按钮
                Button(
                    onClick = { viewModel.stopScanning() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Text("取消扫码")
                }
            }
        } else {
            // ========== 输入模式 ==========
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ---- 工号区域 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "步骤 1: 员工工号",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.employeeId,
                            onValueChange = { viewModel.onEmployeeIdChange(it) },
                            label = { Text("工号") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.startScanning(ScanTarget.EMPLOYEE_ID)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "扫描工号二维码"
                                    )
                                }
                            }
                        )
                    }
                }

                // ---- 设备型号区域 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "步骤 2: 设备型号",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.deviceModel,
                            onValueChange = { viewModel.onDeviceModelChange(it) },
                            label = { Text("设备型号") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.startScanning(ScanTarget.DEVICE_MODEL)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "扫描设备型号二维码"
                                    )
                                }
                            }
                        )
                    }
                }

                // ---- 扫码结果提示 ----
                if (uiState.scanResult != null && !uiState.isScanning) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "扫描结果: ${uiState.scanResult}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ---- 进入点检按钮 ----
                Button(
                    onClick = { viewModel.navigateToInspection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = uiState.deviceModel.isNotBlank()
                ) {
                    Text(
                        text = "进入点检",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 10: Create InspectionViewModel

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionViewModel.kt`

- [ ] **Step 1: Create InspectionViewModel**

```kotlin
package com.machine_check.inspection.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machine_check.inspection.data.models.FullInspectionRequest
import com.machine_check.inspection.data.models.InspectionResultItem
import com.machine_check.inspection.data.models.InspectionTemplate
import com.machine_check.inspection.data.repository.InspectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 单个点检项的表单状态
 */
data class InspectionItemState(
    val template: InspectionTemplate,
    val selectedNormal: Boolean? = null,    // normal_abnormal 类型: true=正常, false=异常, null=未选
    val numericValue: String = "",          // numeric 类型: 用户输入的数值文本
    val remark: String = "",                // 备注
    val isValid: Boolean = true             // 校验状态 (提交时检查)
)

/**
 * 点检页面 UI 状态
 */
sealed interface InspectionUiState {
    /** 加载中 */
    data object Loading : InspectionUiState
    /** 模板加载成功，渲染表单 */
    data class Form(
        val deviceModel: String,
        val employeeId: String,
        val items: List<InspectionItemState>,
        val isSubmitting: Boolean = false,
        val submitSuccess: Boolean = false,
        val errorMessage: String? = null
    ) : InspectionUiState
    /** 加载失败 */
    data class Error(val message: String) : InspectionUiState
    /** 模板列表为空 */
    data class Empty(val deviceModel: String) : InspectionUiState
}

/**
 * 点检页面 ViewModel
 */
class InspectionViewModel : ViewModel() {

    private val repository = InspectionRepository()

    private val _uiState = MutableStateFlow<InspectionUiState>(InspectionUiState.Loading)
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    /** 加载指定设备型号的点检模板 */
    fun loadTemplates(deviceModel: String, employeeId: String) {
        _uiState.value = InspectionUiState.Loading
        viewModelScope.launch {
            repository.getTemplates(deviceModel).fold(
                onSuccess = { templates ->
                    if (templates.isEmpty()) {
                        _uiState.value = InspectionUiState.Empty(deviceModel)
                    } else {
                        // 按 sortOrder 排序
                        val sortedTemplates = templates.sortedBy { it.sortOrder }
                        val items = sortedTemplates.map { template ->
                            InspectionItemState(template = template)
                        }
                        _uiState.value = InspectionUiState.Form(
                            deviceModel = deviceModel,
                            employeeId = employeeId,
                            items = items
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = InspectionUiState.Error(
                        error.message ?: "未知错误，请重试"
                    )
                }
            )
        }
    }

    /** 切换 normal_abnormal 选项 */
    fun onNormalAbnormalChanged(itemIndex: Int, isNormal: Boolean) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            selectedNormal = isNormal,
            isValid = true  // 清除之前的错误标记
        )
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }

    /** 更新 numeric 数值 */
    fun onNumericValueChanged(itemIndex: Int, value: String) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(
            numericValue = value,
            isValid = true  // 清除之前的错误标记
        )
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }

    /** 更新备注 */
    fun onRemarkChanged(itemIndex: Int, remark: String) {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val updatedItems = state.items.toMutableList()
        updatedItems[itemIndex] = updatedItems[itemIndex].copy(remark = remark)
        _uiState.update { (it as InspectionUiState.Form).copy(items = updatedItems) }
    }

    /** 清除错误提示 */
    fun clearError() {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        _uiState.update { state.copy(errorMessage = null) }
    }

    /** 提交点检 */
    fun submitInspection() {
        val state = _uiState.value as? InspectionUiState.Form ?: return
        val deviceModel = state.deviceModel
        val employeeId = state.employeeId

        // ===== 验证 =====
        val validatedItems = state.items.mapIndexed { index, item ->
            val template = item.template
            val isValid = when (template.itemType) {
                "normal_abnormal" -> item.selectedNormal != null
                "numeric" -> {
                    val numValue = item.numericValue.toDoubleOrNull()
                    numValue != null &&
                    numValue >= (template.normalMin ?: Double.MIN_VALUE) &&
                    numValue <= (template.normalMax ?: Double.MAX_VALUE)
                }
                else -> true
            }
            item.copy(isValid = isValid)
        }

        val hasErrors = validatedItems.any { !it.isValid }
        if (hasErrors) {
            _uiState.update { state.copy(
                items = validatedItems,
                errorMessage = "请完善标红的点检项后再提交"
            ) }
            return
        }

        // ===== 构建请求 =====
        val results = validatedItems.map { item ->
            val template = item.template
            val (resultValue, isNormal) = when (template.itemType) {
                "normal_abnormal" -> {
                    val normal = item.selectedNormal ?: false
                    (if (normal) "正常" else "异常") to normal
                }
                "numeric" -> {
                    // 在范围内，一定是正常
                    item.numericValue to true
                }
                else -> "" to true
            }
            InspectionResultItem(
                itemName = template.itemName,
                resultValue = resultValue,
                isNormal = isNormal,
                remark = item.remark
            )
        }

        val request = FullInspectionRequest(
            employeeId = employeeId,
            deviceModel = deviceModel,
            results = results
        )

        // ===== 发送请求 =====
        _uiState.update { state.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.submitInspection(request).fold(
                onSuccess = {
                    _uiState.update { state.copy(
                        isSubmitting = false,
                        submitSuccess = true,
                        items = state.items  // 保留表单数据到成功提示后清除
                    ) }
                },
                onFailure = { error ->
                    _uiState.update { state.copy(
                        isSubmitting = false,
                        errorMessage = error.message ?: "提交失败，请重试"
                    ) }
                }
            )
        }
    }

    /** 重试加载 */
    fun retry(deviceModel: String, employeeId: String) {
        loadTemplates(deviceModel, employeeId)
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 11: Create InspectionScreen

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionScreen.kt`

- [ ] **Step 1: Create InspectionScreen**

```kotlin
package com.machine_check.inspection.ui.inspection

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * 点检页面
 * 根据设备型号加载模板，动态生成表单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    deviceModel: String,
    employeeId: String,
    onBack: () -> Unit,
    viewModel: InspectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    // 加载模板
    LaunchedEffect(deviceModel) {
        viewModel.loadTemplates(deviceModel, employeeId)
    }

    // 错误 Snackbar
    LaunchedEffect((uiState as? InspectionUiState.Form)?.errorMessage) {
        val msg = (uiState as? InspectionUiState.Form)?.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // 提交成功 Snackbar + 返回
    LaunchedEffect((uiState as? InspectionUiState.Form)?.submitSuccess) {
        if ((uiState as? InspectionUiState.Form).submitSuccess) {
            snackbarHostState.showSnackbar("提交成功！")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("点检 - $deviceModel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            is InspectionUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在加载点检模板...")
                    }
                }
            }

            is InspectionUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.retry(deviceModel, employeeId)
                        }) {
                            Text("重试")
                        }
                    }
                }
            }

            is InspectionUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("该设备型号暂无点检模板")
                }
            }

            is InspectionUiState.Form -> {
                InspectionForm(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

/**
 * 点检表单
 */
@Composable
private fun InspectionForm(
    state: InspectionUiState.Form,
    viewModel: InspectionViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        // 表单列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = state.items,
                key = { index, _ -> index }
            ) { index, itemState ->
                InspectionItemCard(
                    itemState = itemState,
                    index = index,
                    onNormalAbnormalChanged = { isNormal ->
                        viewModel.onNormalAbnormalChanged(index, isNormal)
                    },
                    onNumericValueChanged = { value ->
                        viewModel.onNumericValueChanged(index, value)
                    },
                    onRemarkChanged = { remark ->
                        viewModel.onRemarkChanged(index, remark)
                    }
                )
            }
        }

        // 底部提交按钮
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Button(
                onClick = { viewModel.submitInspection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                enabled = !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("提交中...")
                } else {
                    Text(
                        text = "提交点检",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * 单条点检项卡片
 * 支持 normal_abnormal（双按钮切换）和 numeric（数值输入）两种类型
 */
@Composable
private fun InspectionItemCard(
    itemState: InspectionItemState,
    index: Int,
    onNormalAbnormalChanged: (Boolean) -> Unit,
    onNumericValueChanged: (String) -> Unit,
    onRemarkChanged: (String) -> Unit
) {
    val template = itemState.template
    val isValid = itemState.isValid
    val borderColor = if (!isValid) MaterialTheme.colorScheme.error else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (!isValid) 2.dp else 0.dp,
                color = if (!isValid) MaterialTheme.colorScheme.error else Color.Transparent
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${template.itemName}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (!isValid) {
                    Text(
                        text = "必填",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 根据类型渲染不同的输入控件
            when (template.itemType) {
                "normal_abnormal" -> {
                    NormalAbnormalInput(
                        selectedNormal = itemState.selectedNormal,
                        onSelectionChanged = onNormalAbnormalChanged
                    )
                }
                "numeric" -> {
                    NumericInput(
                        value = itemState.numericValue,
                        onValueChanged = onNumericValueChanged,
                        unit = template.unit,
                        normalMin = template.normalMin,
                        normalMax = template.normalMax
                    )
                }
            }

            // 备注输入
            OutlinedTextField(
                value = itemState.remark,
                onValueChange = onRemarkChanged,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                maxLines = 1
            )
        }
    }
}

/**
 * normal_abnormal 类型输入：两个切换按钮
 */
@Composable
private fun NormalAbnormalInput(
    selectedNormal: Boolean?,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onSelectionChanged(true) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedNormal == true)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "✓ 正常",
                color = if (selectedNormal == true)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }

        Button(
            onClick = { onSelectionChanged(false) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedNormal == false)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "✗ 异常",
                color = if (selectedNormal == false)
                    MaterialTheme.colorScheme.onError
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * numeric 类型输入：数值输入框 + 实时范围检查
 */
@Composable
private fun NumericInput(
    value: String,
    onValueChanged: (String) -> Unit,
    unit: String?,
    normalMin: Double?,
    normalMax: Double?
) {
    // 判断当前值是否在正常范围内
    val numericValue = value.toDoubleOrNull()
    val isInRange = when {
        value.isBlank() -> null                      // 未输入，不显示颜色
        numericValue == null -> false                // 无效数字
        normalMin != null && normalMax != null ->
            numericValue >= normalMin && numericValue <= normalMax
        else -> true                                 // 无范围限制
    }

    val borderColor = when (isInRange) {
        null -> MaterialTheme.colorScheme.outline   // 默认
        true -> Color(0xFF4CAF50)                    // 绿色 - 正常
        false -> Color(0xFFF44336)                   // 红色 - 异常
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChanged,
            label = {
                val rangeText = buildString {
                    if (normalMin != null && normalMax != null) {
                        append("范围: $normalMin ~ $normalMax")
                    }
                    if (unit != null) {
                        if (isNotEmpty()) append(" ")
                        append(unit)
                    }
                }
                Text(rangeText.ifEmpty { "请输入数值" })
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = isInRange == false,
            supportingText = if (isInRange == false) {
                { Text("数值超出正常范围", color = Color(0xFFF44336)) }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor
            )
        )
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 12: Create MainActivity with NavHost

**Files:**
- Create: `app/src/main/java/com/machine_check/inspection/MainActivity.kt`

- [ ] **Step 1: Create MainActivity**

```kotlin
package com.machine_check.inspection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.machine_check.inspection.ui.inspection.InspectionScreen
import com.machine_check.inspection.ui.scan.ScanScreen
import com.machine_check.inspection.ui.theme.MachineCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MachineCheckTheme {
                InspectionNavHost()
            }
        }
    }
}

@Composable
fun InspectionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "scan"
    ) {
        // 扫码页面
        composable("scan") {
            ScanScreen(
                onNavigateToInspection = { deviceModel ->
                    navController.navigate("inspection/$deviceModel")
                }
            )
        }

        // 点检页面
        composable(
            route = "inspection/{deviceModel}",
            arguments = listOf(
                navArgument("deviceModel") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceModel = backStackEntry.arguments?.getString("deviceModel") ?: ""
            // 工号从 ScanScreen 共享 — 通过 shared ViewModel 或参数传递
            // 这里从 ScanScreen 的 ViewModel 通过 remember 获取
            InspectionScreen(
                deviceModel = deviceModel,
                employeeId = "",  // 将在后续任务中通过共享 ViewModel 改进
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 13: Connect employee ID flow between screens

**Files:**
- Modify: `app/src/main/java/com/machine_check/inspection/MainActivity.kt`
- Modify: `app/src/main/java/com/machine_check/inspection/ui/scan/ScanScreen.kt`
- Modify: `app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionScreen.kt`

- [ ] **Step 1: Update MainActivity to pass employeeId via route parameter**

Replace `InspectionNavHost()` in `MainActivity.kt`:

```kotlin
@Composable
fun InspectionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "scan"
    ) {
        composable("scan") {
            ScanScreen(
                onNavigateToInspection = { deviceModel, employeeId ->
                    val encodedModel = java.net.URLEncoder.encode(deviceModel, "UTF-8")
                    val encodedEmployee = java.net.URLEncoder.encode(employeeId, "UTF-8")
                    navController.navigate("inspection/$encodedModel/$encodedEmployee")
                }
            )
        }

        composable(
            route = "inspection/{deviceModel}/{employeeId}",
            arguments = listOf(
                navArgument("deviceModel") { type = NavType.StringType },
                navArgument("employeeId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceModel = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("deviceModel") ?: "", "UTF-8"
            )
            val employeeId = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("employeeId") ?: "", "UTF-8"
            )
            InspectionScreen(
                deviceModel = deviceModel,
                employeeId = employeeId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 2: Update ScanScreen to pass employeeId in callback**

In `ScanScreen.kt`, change the function signature:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateToInspection: (deviceModel: String, employeeId: String) -> Unit,
    viewModel: ScanViewModel = viewModel()
)
```

And update the navigation LaunchedEffect:

```kotlin
    // 导航触发
    LaunchedEffect(uiState.navigateToInspection) {
        uiState.navigateToInspection?.let { deviceModel ->
            onNavigateToInspection(deviceModel, uiState.employeeId)
            viewModel.onNavigationComplete()
        }
    }
```

- [ ] **Step 3: Build to verify compilation**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

### Task 14: Full build and test

**Files:**
- No new files — verification only

- [ ] **Step 1: Clean build APK**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew clean :app:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, APK generated at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: Run unit tests**

```bash
cd D:\Android\AndroidStudioProjects\machine_check && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL (no real tests yet, but compiles)

- [ ] **Step 3: Verify all source files exist**

Run:
```bash
find "D:\Android\AndroidStudioProjects\machine_check\app\src\main\java\com\machine_check\inspection" -name "*.kt" | sort
```

Expected output:
```
app/src/main/java/com/machine_check/inspection/MainActivity.kt
app/src/main/java/com/machine_check/inspection/data/models/Models.kt
app/src/main/java/com/machine_check/inspection/data/network/ApiService.kt
app/src/main/java/com/machine_check/inspection/data/network/RetrofitClient.kt
app/src/main/java/com/machine_check/inspection/data/repository/InspectionRepository.kt
app/src/main/java/com/machine_check/inspection/ui/components/QrCodeScanner.kt
app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionScreen.kt
app/src/main/java/com/machine_check/inspection/ui/inspection/InspectionViewModel.kt
app/src/main/java/com/machine_check/inspection/ui/scan/ScanScreen.kt
app/src/main/java/com/machine_check/inspection/ui/scan/ScanViewModel.kt
app/src/main/java/com/machine_check/inspection/ui/theme/Color.kt
app/src/main/java/com/machine_check/inspection/ui/theme/Theme.kt
app/src/main/java/com/machine_check/inspection/ui/theme/Type.kt
app/src/main/java/com/machine_check/inspection/utils/PreferencesManager.kt
```
