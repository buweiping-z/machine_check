package com.machine_check.inspection.data.repository

import com.machine_check.inspection.data.models.FullInspectionRequest
import com.machine_check.inspection.data.models.InspectionTemplate
import com.machine_check.inspection.data.models.SubmitResponse
import com.machine_check.inspection.data.network.ApiService
import com.machine_check.inspection.data.network.RetrofitClient

/**
 * 点检数据仓库
 * 封装网络请求，统一处理成功/失败结果
 *
 * @param apiService 可通过构造函数注入以支持测试
 */
class InspectionRepository(
    private val api: ApiService = RetrofitClient.apiService
) {

    /** 获取指定设备型号的点检模板列表 */
    suspend fun getTemplates(deviceModel: String): Result<List<InspectionTemplate>> {
        return try {
            val response = api.getTemplates(deviceModel)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(
                        Exception("获取模板失败: 服务端返回了空响应体")
                    )
                }
            } else {
                // TODO: 考虑使用自定义异常类型替代 Exception
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
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(
                        Exception("提交失败: 服务端返回了空响应体")
                    )
                }
            } else {
                // TODO: 考虑使用自定义异常类型替代 Exception
                Result.failure(
                    Exception("提交失败: ${response.code()} ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络连接失败，请检查网络设置", e))
        }
    }
}
