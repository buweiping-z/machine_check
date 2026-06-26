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
