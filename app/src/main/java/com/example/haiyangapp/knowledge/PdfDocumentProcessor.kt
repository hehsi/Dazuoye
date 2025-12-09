package com.example.haiyangapp.knowledge

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF 文档处理器
 * 使用 PdfBox-Android 库提取 PDF 文本
 */
class PdfDocumentProcessor : DocumentProcessor {

    companion object {
        private var isInitialized = false

        /**
         * 初始化 PDFBox（需要在使用前调用一次）
         */
        fun initialize(context: Context) {
            if (!isInitialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                isInitialized = true
            }
        }
    }

    override suspend fun extractText(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                // 确保已初始化
                initialize(context)

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(
                        DocumentProcessingException("无法打开 PDF 文件")
                    )

                inputStream.use { stream ->
                    PDDocument.load(stream).use { document ->
                        val stripper = PDFTextStripper().apply {
                            sortByPosition = true
                        }

                        val text = stripper.getText(document)

                        if (text.isBlank()) {
                            return@withContext Result.failure(
                                DocumentProcessingException("PDF 文件无法提取文本（可能是扫描件）")
                            )
                        }

                        // 清理文本：移除多余空白行，规范化换行符
                        val cleanedText = cleanText(text)
                        Result.success(cleanedText)
                    }
                }
            } catch (e: Exception) {
                Result.failure(DocumentProcessingException("PDF 解析失败: ${e.message}", e))
            }
        }

    override fun getSupportedExtensions(): List<String> = listOf("pdf")

    override fun getDocumentType(): String = "pdf"

    /**
     * 清理提取的文本
     */
    private fun cleanText(text: String): String {
        return text
            // 统一换行符
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // 移除连续多个空行，保留最多一个
            .replace(Regex("\n{3,}"), "\n\n")
            // 移除行首尾空白
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            // 移除空行开头
            .trimStart('\n')
            // 移除空行结尾
            .trimEnd('\n')
    }
}
