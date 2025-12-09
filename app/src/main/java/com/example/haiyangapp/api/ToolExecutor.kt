package com.example.haiyangapp.api

import android.util.Log
import com.example.haiyangapp.api.models.ToolCall
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 工具执行器
 * 负责执行AI返回的工具调用并返回结果
 */
class ToolExecutor {

    companion object {
        private const val TAG = "ToolExecutor"
    }

    private val weatherService = WeatherService()
    private val webSearchService = WebSearchService()
    private val gson = Gson()

    /**
     * 执行工具调用
     * @param toolCall AI返回的工具调用请求
     * @return 工具执行结果
     */
    suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        Log.d(TAG, "Executing tool: ${toolCall.function.name}")
        Log.d(TAG, "Arguments: ${toolCall.function.arguments}")

        return when (toolCall.function.name) {
            "get_weather" -> executeGetWeather(toolCall)
            "web_search" -> executeWebSearch(toolCall)
            else -> ToolExecutionResult(
                toolCallId = toolCall.id,
                success = false,
                result = "未知的工具: ${toolCall.function.name}"
            )
        }
    }

    /**
     * 执行天气查询
     */
    private suspend fun executeGetWeather(toolCall: ToolCall): ToolExecutionResult {
        return try {
            // 解析参数
            val args = gson.fromJson(toolCall.function.arguments, JsonObject::class.java)
            val city = args.get("city")?.asString

            if (city.isNullOrBlank()) {
                return ToolExecutionResult(
                    toolCallId = toolCall.id,
                    success = false,
                    result = "缺少城市参数"
                )
            }

            Log.d(TAG, "Getting weather for city: $city")

            // 调用天气服务
            val weatherResult = weatherService.getWeather(city)

            weatherResult.fold(
                onSuccess = { weatherInfo ->
                    Log.d(TAG, "Weather result: $weatherInfo")
                    ToolExecutionResult(
                        toolCallId = toolCall.id,
                        success = true,
                        result = weatherInfo
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Weather query failed", error)
                    ToolExecutionResult(
                        toolCallId = toolCall.id,
                        success = false,
                        result = "天气查询失败: ${error.message}"
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing get_weather", e)
            ToolExecutionResult(
                toolCallId = toolCall.id,
                success = false,
                result = "工具执行错误: ${e.message}"
            )
        }
    }

    /**
     * 执行网页搜索
     */
    private suspend fun executeWebSearch(toolCall: ToolCall): ToolExecutionResult {
        return try {
            // 解析参数
            val args = gson.fromJson(toolCall.function.arguments, JsonObject::class.java)
            val query = args.get("query")?.asString
            val site = args.get("site")?.asString  // 可选参数

            if (query.isNullOrBlank()) {
                return ToolExecutionResult(
                    toolCallId = toolCall.id,
                    success = false,
                    result = "缺少搜索关键词参数"
                )
            }

            Log.d(TAG, "Searching for: $query, site: $site")

            // 调用搜索服务
            val searchResult = webSearchService.search(
                query = query,
                site = site
            )

            searchResult.fold(
                onSuccess = { searchInfo ->
                    Log.d(TAG, "Search result: ${searchInfo.take(200)}...")

                    // 后处理：从格式化的搜索结果中提取来源信息
                    val sources = parseSourcesFromResult(searchInfo)
                    Log.d(TAG, "Parsed ${sources.size} sources from search result")

                    ToolExecutionResult(
                        toolCallId = toolCall.id,
                        success = true,
                        result = searchInfo,
                        sources = sources
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Web search failed", error)
                    ToolExecutionResult(
                        toolCallId = toolCall.id,
                        success = false,
                        result = "搜索失败: ${error.message}"
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing web_search", e)
            ToolExecutionResult(
                toolCallId = toolCall.id,
                success = false,
                result = "工具执行错误: ${e.message}"
            )
        }
    }

    /**
     * 从格式化的搜索结果字符串中解析来源信息
     * 格式示例：
     * 1. 标题
     *    来源: domain
     *    时间: time
     *    摘要: snippet
     *    链接: url
     */
    private fun parseSourcesFromResult(searchResult: String): List<ToolSource> {
        val sources = mutableListOf<ToolSource>()

        try {
            // 按条目分割（每条以 "数字." 开头）
            val pattern = Regex("""(\d+)\.\s+(.+?)(?=\n\s*\d+\.|${'$'})""", RegexOption.DOT_MATCHES_ALL)
            val matches = pattern.findAll(searchResult)

            for (match in matches) {
                val entryText = match.groupValues[2]

                // 提取标题（第一行）
                val lines = entryText.trim().split("\n")
                val title = lines.firstOrNull()?.trim() ?: continue

                // 提取链接
                val urlMatch = Regex("""链接:\s*(.+)""").find(entryText)
                val url = urlMatch?.groupValues?.get(1)?.trim() ?: continue

                // 提取来源域名
                val domainMatch = Regex("""来源:\s*(.+)""").find(entryText)
                val domain = domainMatch?.groupValues?.get(1)?.trim() ?: ""

                // 提取摘要
                val snippetMatch = Regex("""摘要:\s*(.+?)(?=\n\s*链接:|${'$'})""", RegexOption.DOT_MATCHES_ALL).find(entryText)
                val snippet = snippetMatch?.groupValues?.get(1)?.trim() ?: ""

                sources.add(ToolSource(
                    title = title,
                    url = url,
                    snippet = snippet,
                    domain = domain
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sources from search result", e)
        }

        return sources
    }
}

/**
 * 工具执行结果
 */
data class ToolExecutionResult(
    val toolCallId: String,
    val success: Boolean,
    val result: String,
    val sources: List<ToolSource> = emptyList()  // 来源信息（用于UI显示）
)

/**
 * 工具来源信息（用于显示搜索结果链接等）
 */
data class ToolSource(
    val title: String,
    val url: String,
    val snippet: String = "",
    val domain: String = ""
)
