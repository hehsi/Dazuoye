package com.example.haiyangapp.knowledge

import android.content.Context
import android.net.Uri

/**
 * 文档处理器接口
 * 定义从不同格式文档中提取文本的标准接口
 */
interface DocumentProcessor {

    /**
     * 从文档中提取纯文本内容
     *
     * @param context Android 上下文
     * @param uri 文档的 URI
     * @return 提取的文本内容，失败返回 Result.failure
     */
    suspend fun extractText(context: Context, uri: Uri): Result<String>

    /**
     * 获取此处理器支持的文件扩展名列表
     */
    fun getSupportedExtensions(): List<String>

    /**
     * 检查是否支持指定的文件类型
     */
    fun isSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return getSupportedExtensions().contains(extension)
    }

    /**
     * 获取文档类型标识
     */
    fun getDocumentType(): String
}

/**
 * 文档处理结果
 */
data class DocumentContent(
    val text: String,
    val pageCount: Int = 1,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 文档处理异常
 */
class DocumentProcessingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
