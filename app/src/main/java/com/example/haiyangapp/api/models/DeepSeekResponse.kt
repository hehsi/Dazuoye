package com.example.haiyangapp.api.models

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek API响应体
 * 完整的API返回数据结构
 */
data class DeepSeekResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val objectType: String,

    @SerializedName("created")
    val created: Long,

    @SerializedName("model")
    val model: String,

    @SerializedName("choices")
    val choices: List<Choice>,

    @SerializedName("usage")
    val usage: Usage? = null
)

/**
 * 响应选项
 */
data class Choice(
    @SerializedName("index")
    val index: Int,

    @SerializedName("message")
    val message: DeepSeekMessage,

    @SerializedName("finish_reason")
    val finishReason: String? = null
)

/**
 * Token使用情况
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,

    @SerializedName("completion_tokens")
    val completionTokens: Int,

    @SerializedName("total_tokens")
    val totalTokens: Int
)
