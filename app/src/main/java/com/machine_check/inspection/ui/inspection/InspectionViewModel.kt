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
            _uiState.update {
                state.copy(
                    items = validatedItems,
                    errorMessage = "请完善标红的点检项后再提交"
                )
            }
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
                    _uiState.update {
                        state.copy(
                            isSubmitting = false,
                            submitSuccess = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        state.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "提交失败，请重试"
                        )
                    }
                }
            )
        }
    }

    /** 重试加载 */
    fun retry(deviceModel: String, employeeId: String) {
        loadTemplates(deviceModel, employeeId)
    }
}
