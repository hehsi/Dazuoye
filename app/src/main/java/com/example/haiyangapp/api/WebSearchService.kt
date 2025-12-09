package com.example.haiyangapp.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit

/**
 * 网页搜索服务
 * 使用 uapis.cn
 */
class WebSearchService {

    companion object {
        private const val TAG = "WebSearchService"

        // uapis.cn 智能搜索 API
        private const val UAPI_ENDPOINT = "https://uapis.cn/api/v1/search/aggregate"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 执行网页搜索
     * @param query 搜索关键词
     * @param site 限制搜索特定网站（可选）
     * @param fileType 限制文件类型（可选）
     * @param fetchFull 是否获取完整正文（会影响响应时间）
     * @return 搜索结果摘要
     */
    suspend fun search(
        query: String,
        site: String? = null,
        fileType: String? = null,
        fetchFull: Boolean = false
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching for: $query")

                // 构建请求体
                val requestBody = buildJsonObject {
                    put("query", query)
                    site?.let { put("site", it) }
                    fileType?.let { put("filetype", it) }
                    put("fetch_full", fetchFull)
                    put("timeout_ms", 15000)
                }

                Log.d(TAG, "Request body: $requestBody")

                val request = Request.Builder()
                    .url(UAPI_ENDPOINT)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Search failed: ${response.code}")
                    return@withContext Result.failure(Exception("搜索请求失败: ${response.code}"))
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("搜索响应为空"))
                }

                Log.d(TAG, "Search response: ${responseBody.take(1000)}")

                parseSearchResponse(responseBody)

            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 解析搜索响应
     */
    private fun parseSearchResponse(responseBody: String): Result<String> {
        try {
            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)

            // 检查是否有错误
            if (jsonObject.has("error")) {
                val error = jsonObject.get("error")?.asString ?: "未知错误"
                return Result.failure(Exception(error))
            }

            val query = jsonObject.get("query")?.asString ?: ""
            val totalResults = jsonObject.get("total_results")?.asInt ?: 0
            val results = jsonObject.getAsJsonArray("results")

            if (results == null || results.size() == 0) {
                return Result.success("未找到关于\"$query\"的相关搜索结果")
            }

            val searchResults = StringBuilder()
            searchResults.append("搜索结果 (共${totalResults}条):\n\n")

            // 取前5条结果
            results.take(5).forEachIndexed { index, element ->
                val item = element.asJsonObject
                val title = item.get("title")?.asString ?: ""
                val url = item.get("url")?.asString ?: ""
                val snippet = item.get("snippet")?.asString ?: ""
                val domain = item.get("domain")?.asString ?: ""
                val publishTime = item.get("publish_time")?.asString
                val score = item.get("score")?.asFloat

                searchResults.append("${index + 1}. $title\n")
                searchResults.append("   来源: $domain\n")
                if (!publishTime.isNullOrEmpty()) {
                    searchResults.append("   时间: $publishTime\n")
                }
                searchResults.append("   摘要: $snippet\n")
                searchResults.append("   链接: $url\n\n")
            }

            // 添加处理信息
            val processTime = jsonObject.get("process_time_ms")?.asInt
            val cached = jsonObject.get("cached")?.asBoolean ?: false
            if (processTime != null) {
                searchResults.append("(搜索耗时: ${processTime}ms")
                if (cached) {
                    searchResults.append(", 来自缓存")
                }
                searchResults.append(")")
            }

            return Result.success(searchResults.toString())

        } catch (e: Exception) {
            Log.e(TAG, "Parse search response error", e)
            return Result.failure(Exception("解析搜索结果失败: ${e.message}"))
        }
    }

    /**
     * 构建 JSON 对象的辅助函数
     */
    private fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): String {
        val builder = JsonObjectBuilder()
        builder.block()
        return builder.build()
    }

    /**
     * JSON 对象构建器
     */
    private class JsonObjectBuilder {
        private val map = mutableMapOf<String, Any?>()

        fun put(key: String, value: Any?) {
            map[key] = value
        }

        fun build(): String {
            return Gson().toJson(map)
        }
    }
}

/**
 * 搜索结果数据类
 */
data class SearchResultItem(
    @SerializedName("title")
    val title: String,

    @SerializedName("url")
    val url: String,

    @SerializedName("snippet")
    val snippet: String,

    @SerializedName("domain")
    val domain: String,

    @SerializedName("source")
    val source: String?,

    @SerializedName("position")
    val position: Int?,

    @SerializedName("score")
    val score: Float?,

    @SerializedName("publish_time")
    val publishTime: String?,

    @SerializedName("author")
    val author: String?
)

/**
 * 搜索响应数据类
 */
data class SearchResponse(
    @SerializedName("query")
    val query: String,

    @SerializedName("total_results")
    val totalResults: Int,

    @SerializedName("results")
    val results: List<SearchResultItem>,

    @SerializedName("process_time_ms")
    val processTimeMs: Int?,

    @SerializedName("cached")
    val cached: Boolean?
)
