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

                    val imageAnalysisBuilder = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1920, 1080))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

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

                        val extender = Camera2Interop.Extender<ImageAnalysis>(imageAnalysisBuilder)

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

                    val imageAnalysis = imageAnalysisBuilder.build()
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
