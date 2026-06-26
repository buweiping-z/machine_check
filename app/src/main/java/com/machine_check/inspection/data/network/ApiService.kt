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
