package com.machine_check.inspection.data.models

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

