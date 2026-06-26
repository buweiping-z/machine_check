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
    onNavigateToInspection: (deviceModel: String, employeeId: String) -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 导航触发
    LaunchedEffect(uiState.navigateToInspection) {
        uiState.navigateToInspection?.let { deviceModel ->
            onNavigateToInspection(deviceModel, uiState.employeeId)
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
