package com.example.haiyangapp.api.models

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek API消息格式
 * 对应OpenAI标准的消息结构
 */
data class DeepSeekMessage(
    @SerializedName("role")
    val role: String, // "system", "user", "assistant", "tool"

    @SerializedName("content")
    val content: String?, // 消息内容（tool_calls时可能为null）

    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? = null, // AI返回的工具调用请求

    @SerializedName("tool_call_id")
    val toolCallId: String? = null // 工具调用结果的对应ID（role="tool"时使用）
) {
    // 辅助构造函数
    constructor(role: String, content: String) : this(role, content, null, null)

    companion object {
        /**
         * 创建工具调用结果消息
         */
        fun toolResult(toolCallId: String, content: String): DeepSeekMessage {
            return DeepSeekMessage(
                role = "tool",
                content = content,
                toolCalls = null,
                toolCallId = toolCallId
            )
        }

        /**
         * 创建带工具调用的助手消息
         */
        fun assistantWithToolCalls(toolCalls: List<ToolCall>): DeepSeekMessage {
            return DeepSeekMessage(
                role = "assistant",
                content = null,
                toolCalls = toolCalls,
                toolCallId = null
            )
        }
    }
}