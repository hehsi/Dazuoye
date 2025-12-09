package com.example.haiyangapp.api.models

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek API请求体
 * 包含模型名称、消息历史和其他配置
 */
data class DeepSeekRequest(
    @SerializedName("model")
    val model: String = "deepseek-chat", 

    @SerializedName("messages")
    val messages: List<DeepSeekMessage>, // 完整的对话历史（包含上下文）

    @SerializedName("stream")
    val stream: Boolean = true, // 使用流式响应（SSE格式）

    @SerializedName("temperature")
    val temperature: Double = 0.7, 

    @SerializedName("max_tokens")
    val maxTokens: Int? = null, 

    @SerializedName("tools")
    val tools: List<Tool>? = null, // 可用工具列表（Function Calling）

    @SerializedName("tool_choice")
    val toolChoice: String? = null 
)