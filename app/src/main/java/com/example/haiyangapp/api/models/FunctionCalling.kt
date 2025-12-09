package com.example.haiyangapp.api.models

import com.google.gson.annotations.SerializedName

/**
 * 工具定义
 */
data class Tool(
    @SerializedName("type")
    val type: String = "function",

    @SerializedName("function")
    val function: FunctionDefinition
)

/**
 * 函数定义
 */
data class FunctionDefinition(
    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("parameters")
    val parameters: FunctionParameters
)

/**
 * 函数参数定义
 */
data class FunctionParameters(
    @SerializedName("type")
    val type: String = "object",

    @SerializedName("properties")
    val properties: Map<String, ParameterProperty>,

    @SerializedName("required")
    val required: List<String> = emptyList()
)

/**
 * 参数属性
 */
data class ParameterProperty(
    @SerializedName("type")
    val type: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("enum")
    val enum: List<String>? = null
)

/**
 * 工具调用请求（AI返回的）
 */
data class ToolCall(
    @SerializedName("id")
    val id: String,

    @SerializedName("type")
    val type: String = "function",

    @SerializedName("function")
    val function: FunctionCall
)

/**
 * 函数调用详情
 */
data class FunctionCall(
    @SerializedName("name")
    val name: String,

    @SerializedName("arguments")
    val arguments: String  // JSON字符串
)

/**
 * 工具调用结果消息
 */
data class ToolResultMessage(
    @SerializedName("role")
    val role: String = "tool",

    @SerializedName("content")
    val content: String,

    @SerializedName("tool_call_id")
    val toolCallId: String
)

/**
 * 可用工具注册表
 */
object AvailableTools {

    /**
     * 获取天气查询工具定义
     */
    fun getWeatherTool(): Tool {
        return Tool(
            type = "function",
            function = FunctionDefinition(
                name = "get_weather",
                description = "获取指定城市的天气信息。当用户询问某个城市的天气时调用此函数。",
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "city" to ParameterProperty(
                            type = "string",
                            description = "城市名称，例如：北京、上海、广州"
                        )
                    ),
                    required = listOf("city")
                )
            )
        )
    }

    /**
     * 获取网页搜索工具定义
     */
    fun getWebSearchTool(): Tool {
        return Tool(
            type = "function",
            function = FunctionDefinition(
                name = "web_search",
                description = "搜索互联网获取最新信息。当用户询问需要联网查询的问题、最新新闻、实时信息、或者你不确定的知识时，调用此函数进行搜索。",
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "query" to ParameterProperty(
                            type = "string",
                            description = "搜索关键词，应该是简洁明确的搜索词"
                        ),
                        "site" to ParameterProperty(
                            type = "string",
                            description = "可选，限制搜索特定网站，例如：zhihu.com、github.com"
                        )
                    ),
                    required = listOf("query")
                )
            )
        )
    }

    /**
     * 获取所有可用工具
     */
    fun getAllTools(): List<Tool> {
        return listOf(
            getWeatherTool(),
            getWebSearchTool()
        )
    }
}
