package com.example.haiyangapp.api

import com.example.haiyangapp.api.models.DeepSeekRequest
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * DeepSeek API接口
 * 定义API端点和请求方法
 */
interface DeepSeekApiService {

    /**
     * 发送聊天请求到DeepSeek API（流式响应）
     * @param request 包含消息历史和配置的请求体
     * @return 流式响应体
     */
    @Streaming
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Body request: DeepSeekRequest
    ): ResponseBody
}
