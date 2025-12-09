package com.example.haiyangapp.knowledge

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Word 文档处理器
 * 使用原生 ZIP + XML 解析提取 Word (.docx) 文本
 *
 * .docx 文件本质上是一个 ZIP 压缩包，包含：
 * - word/document.xml: 主要文档内容
 * - word/styles.xml: 样式定义
 * - 其他支持文件
 *
 * 文本内容存储在 <w:t> 标签中
 */
class WordDocumentProcessor : DocumentProcessor {

    companion object {
        private const val DOCUMENT_XML_PATH = "word/document.xml"
        private const val NAMESPACE_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        private const val TAG_TEXT = "t"           // <w:t> 文本内容
        private const val TAG_PARAGRAPH = "p"      // <w:p> 段落
        private const val TAG_TABLE_CELL = "tc"    // <w:tc> 表格单元格
        private const val TAG_TABLE_ROW = "tr"     // <w:tr> 表格行
    }

    override suspend fun extractText(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(
                        DocumentProcessingException("无法打开 Word 文件")
                    )

                inputStream.use { stream ->
                    val documentXml = extractDocumentXml(stream)

                    if (documentXml == null) {
                        return@withContext Result.failure(
                            DocumentProcessingException("无效的 Word 文件格式，找不到 document.xml")
                        )
                    }

                    val extractedText = parseDocumentXml(documentXml)

                    if (extractedText.isBlank()) {
                        return@withContext Result.failure(
                            DocumentProcessingException("Word 文件无法提取文本")
                        )
                    }

                    // 清理文本
                    val cleanedText = cleanText(extractedText)
                    Result.success(cleanedText)
                }
            } catch (e: Exception) {
                Result.failure(DocumentProcessingException("Word 解析失败: ${e.message}", e))
            }
        }

    /**
     * 从 .docx ZIP 包中提取 word/document.xml 内容
     */
    private fun extractDocumentXml(inputStream: InputStream): String? {
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry

        while (entry != null) {
            if (entry.name == DOCUMENT_XML_PATH) {
                // 读取 document.xml 内容
                val content = zipInputStream.bufferedReader().readText()
                zipInputStream.closeEntry()
                return content
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }

        return null
    }

    /**
     * 解析 document.xml 提取文本内容
     */
    private fun parseDocumentXml(xmlContent: String): String {
        val textBuilder = StringBuilder()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(xmlContent.reader())

            var eventType = parser.eventType
            var inParagraph = false
            var currentParagraphText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        when (tagName) {
                            TAG_PARAGRAPH -> {
                                inParagraph = true
                                currentParagraphText = StringBuilder()
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        // 获取文本内容
                        val text = parser.text
                        if (text != null && text.isNotBlank()) {
                            currentParagraphText.append(text)
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name
                        when (tagName) {
                            TAG_TEXT -> {
                                // <w:t> 标签结束，可能需要添加空格
                            }
                            TAG_PARAGRAPH -> {
                                // 段落结束，添加换行
                                if (currentParagraphText.isNotBlank()) {
                                    textBuilder.append(currentParagraphText.toString().trim())
                                    textBuilder.append("\n")
                                }
                                inParagraph = false
                            }
                            TAG_TABLE_ROW -> {
                                // 表格行结束，添加换行
                                textBuilder.append("\n")
                            }
                            TAG_TABLE_CELL -> {
                                // 单元格结束，添加制表符分隔
                                textBuilder.append("\t")
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e("WordDocProcessor", "XML parsing error: ${e.message}", e)
            // 如果 XML 解析失败，尝试简单的正则提取
            return extractTextWithRegex(xmlContent)
        }

        return textBuilder.toString()
    }

    /**
     * 使用正则表达式提取文本（备用方案）
     */
    private fun extractTextWithRegex(xmlContent: String): String {
        val textBuilder = StringBuilder()

        // 匹配 <w:t>...</w:t> 或 <w:t ...>...</w:t> 中的文本
        val pattern = Regex("<w:t[^>]*>([^<]*)</w:t>")
        val matches = pattern.findAll(xmlContent)

        var lastParagraphEnd = 0
        val paragraphPattern = Regex("</w:p>")

        matches.forEach { match ->
            textBuilder.append(match.groupValues[1])
        }

        // 在段落结束处添加换行
        val paragraphs = xmlContent.split("</w:p>")
        val result = StringBuilder()

        paragraphs.forEach { paragraph ->
            val textPattern = Regex("<w:t[^>]*>([^<]*)</w:t>")
            val texts = textPattern.findAll(paragraph)
            val paragraphText = texts.map { it.groupValues[1] }.joinToString("")
            if (paragraphText.isNotBlank()) {
                result.append(paragraphText.trim())
                result.append("\n")
            }
        }

        return result.toString()
    }

    override fun getSupportedExtensions(): List<String> = listOf("docx")

    override fun getDocumentType(): String = "docx"

    /**
     * 清理提取的文本
     */
    private fun cleanText(text: String): String {
        return text
            // 统一换行符
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // 移除连续多个空行
            .replace(Regex("\n{3,}"), "\n\n")
            // 移除多余的制表符
            .replace(Regex("\t+"), " ")
            // 移除行首尾空白
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            .trimStart('\n')
            .trimEnd('\n')
    }
}
