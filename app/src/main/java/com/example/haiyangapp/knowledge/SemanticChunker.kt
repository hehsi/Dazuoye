package com.example.haiyangapp.knowledge

import android.util.Log

/**
 * 语义分块配置
 */
data class SemanticChunkConfig(
    /** 每块的目标字符数 */
    val targetChunkSize: Int = 400,

    /** 最大块大小 */
    val maxChunkSize: Int = 600,

    /** 最小块大小 */
    val minChunkSize: Int = 100,

    /** 块之间的重叠句子数 */
    val overlapSentences: Int = 1,

    /** 是否检测标题并用作分块边界 */
    val detectHeadings: Boolean = true,

    /** 是否按主题边界分割 */
    val detectTopicBoundary: Boolean = true,

    /** 主题变化检测的关键词变化阈值 */
    val topicChangeThreshold: Float = 0.7f
)

/**
 * 语义分块结果
 */
data class SemanticChunk(
    /** 块内容 */
    val content: String,

    /** 块索引 */
    val index: Int,

    /** 块类型 */
    val type: ChunkType = ChunkType.CONTENT,

    /** 所属章节标题（如果有） */
    val sectionTitle: String? = null,

    /** 上下文前缀（来自上一块的结尾） */
    val contextPrefix: String = ""
) {
    enum class ChunkType {
        HEADING,    // 标题块
        CONTENT,    // 内容块
        LIST,       // 列表块
        TABLE       // 表格块
    }

    /**
     * 获取带上下文的完整内容
     */
    fun getFullContent(): String {
        return if (contextPrefix.isNotEmpty()) {
            "$contextPrefix\n\n$content"
        } else {
            content
        }
    }
}

/**
 * 语义分块器
 * 基于文档结构和语义边界进行智能分块
 */
class SemanticChunker(
    private val config: SemanticChunkConfig = SemanticChunkConfig()
) {
    companion object {
        private const val TAG = "SemanticChunker"

        // 中文标题模式
        private val CHINESE_HEADING_PATTERNS = listOf(
            Regex("^[第][一二三四五六七八九十百千万\\d]+[章节部分篇].*"),  // 第一章、第1节
            Regex("^[一二三四五六七八九十]+[、.．].*"),                    // 一、xxx
            Regex("^[（(][一二三四五六七八九十\\d]+[）)].*"),              // (一)、(1)
            Regex("^\\d+[、.．]\\d*[、.．]?.*"),                          // 1、 1.1
            Regex("^[【\\[].*[】\\]]$"),                                  // 【标题】
            Regex("^#+\\s+.*")                                           // Markdown 标题
        )

        // 英文标题模式
        private val ENGLISH_HEADING_PATTERNS = listOf(
            Regex("^Chapter\\s+\\d+.*", RegexOption.IGNORE_CASE),
            Regex("^Section\\s+\\d+.*", RegexOption.IGNORE_CASE),
            Regex("^\\d+\\.\\d*\\.?\\d*\\s+[A-Z].*"),                    // 1.1 Title
            Regex("^[A-Z][A-Z\\s]+$"),                                   // ALL CAPS TITLE
            Regex("^[IVXLCDM]+\\.\\s+.*")                                // Roman numerals
        )

        // 句子结束符
        private val SENTENCE_ENDINGS = charArrayOf('。', '！', '？', '.', '!', '?')

        // 列表项模式
        private val LIST_ITEM_PATTERN = Regex("^[•●○◆◇▪▫\\-\\*]\\s+.*|^\\d+[.、)）]\\s+.*")

        // 主题变化指示词
        private val TOPIC_CHANGE_INDICATORS = setOf(
            "另外", "此外", "其次", "然而", "但是", "不过", "相反",
            "总之", "综上", "因此", "所以", "由此可见",
            "首先", "其次", "最后", "第一", "第二",
            "however", "moreover", "furthermore", "in contrast",
            "therefore", "thus", "in conclusion", "firstly", "secondly"
        )
    }

    /**
     * 对文本进行语义分块
     */
    fun chunk(text: String): List<SemanticChunk> {
        if (text.isBlank()) return emptyList()

        val cleanedText = text.trim()

        // Step 1: 按段落分割
        val paragraphs = splitIntoParagraphs(cleanedText)
        Log.d(TAG, "Split into ${paragraphs.size} paragraphs")

        // Step 2: 识别段落类型（标题、内容、列表等）
        val annotatedParagraphs = paragraphs.map { para ->
            AnnotatedParagraph(
                content = para,
                isHeading = isHeading(para),
                isList = isListItem(para),
                keywords = extractKeywords(para)
            )
        }

        // Step 3: 按语义边界分组
        val semanticGroups = groupBySemanticBoundary(annotatedParagraphs)
        Log.d(TAG, "Grouped into ${semanticGroups.size} semantic groups")

        // Step 4: 将分组转换为块
        val chunks = mutableListOf<SemanticChunk>()
        var currentSectionTitle: String? = null
        var chunkIndex = 0
        var pendingContent = StringBuilder()  // 累积小段落

        for (group in semanticGroups) {
            // 更新当前章节标题
            val headingPara = group.find { it.isHeading }
            if (headingPara != null) {
                currentSectionTitle = headingPara.content.trim()
            }

            // 合并组内段落
            val groupContent = group.filter { !it.isHeading || group.size == 1 }
                .joinToString("\n\n") { it.content }

            if (groupContent.isBlank()) continue

            // 将内容添加到 pending
            if (pendingContent.isNotEmpty()) {
                pendingContent.append("\n\n")
            }
            pendingContent.append(groupContent)

            // 检查是否应该创建块
            if (pendingContent.length >= config.targetChunkSize) {
                // 内容足够大，创建块
                if (pendingContent.length > config.maxChunkSize) {
                    // 太长，需要分割
                    val subChunks = splitLongContent(pendingContent.toString(), currentSectionTitle, chunkIndex)
                    chunks.addAll(subChunks)
                    chunkIndex += subChunks.size
                } else {
                    chunks.add(
                        SemanticChunk(
                            content = pendingContent.toString(),
                            index = chunkIndex++,
                            type = determineChunkType(group),
                            sectionTitle = currentSectionTitle
                        )
                    )
                }
                pendingContent = StringBuilder()
            }
        }

        // 处理剩余的 pending 内容
        if (pendingContent.isNotEmpty()) {
            val remaining = pendingContent.toString()
            if (chunks.isNotEmpty() && remaining.length < config.minChunkSize) {
                // 太短，尝试合并到上一块
                val lastChunk = chunks.removeLast()
                val merged = lastChunk.content + "\n\n" + remaining
                if (merged.length <= config.maxChunkSize) {
                    chunks.add(lastChunk.copy(content = merged))
                } else {
                    // 合并后太长，分别保存
                    chunks.add(lastChunk)
                    chunks.add(
                        SemanticChunk(
                            content = remaining,
                            index = chunkIndex++,
                            sectionTitle = currentSectionTitle
                        )
                    )
                }
            } else {
                // 直接保存（即使很短，也比丢弃好）
                chunks.add(
                    SemanticChunk(
                        content = remaining,
                        index = chunkIndex++,
                        sectionTitle = currentSectionTitle
                    )
                )
            }
        }

        // Step 5: 添加上下文重叠
        val chunksWithContext = addContextOverlap(chunks)

        Log.d(TAG, "Final chunk count: ${chunksWithContext.size}")
        return chunksWithContext
    }

    /**
     * 将文本分割为段落
     */
    private fun splitIntoParagraphs(text: String): List<String> {
        return text.split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * 判断是否为标题
     */
    private fun isHeading(text: String): Boolean {
        if (!config.detectHeadings) return false

        val trimmed = text.trim()

        // 长度检查：标题通常较短
        if (trimmed.length > 100) return false

        // 不以标点结尾（除了特定符号）
        if (trimmed.last() in SENTENCE_ENDINGS) return false

        // 检查中文标题模式
        for (pattern in CHINESE_HEADING_PATTERNS) {
            if (pattern.matches(trimmed)) return true
        }

        // 检查英文标题模式
        for (pattern in ENGLISH_HEADING_PATTERNS) {
            if (pattern.matches(trimmed)) return true
        }

        return false
    }

    /**
     * 判断是否为列表项
     */
    private fun isListItem(text: String): Boolean {
        return LIST_ITEM_PATTERN.matches(text.trim())
    }

    /**
     * 提取关键词（用于主题检测）
     */
    private fun extractKeywords(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[\\s,.!?;:，。！？；：、\\n]+"))
            .filter { it.length >= 2 }
            .toSet()
    }

    /**
     * 计算两个段落的关键词相似度
     */
    private fun calculateKeywordSimilarity(keywords1: Set<String>, keywords2: Set<String>): Float {
        if (keywords1.isEmpty() || keywords2.isEmpty()) return 0f
        val intersection = keywords1.intersect(keywords2)
        val union = keywords1.union(keywords2)
        return intersection.size.toFloat() / union.size
    }

    /**
     * 按语义边界分组
     */
    private fun groupBySemanticBoundary(paragraphs: List<AnnotatedParagraph>): List<List<AnnotatedParagraph>> {
        if (paragraphs.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<AnnotatedParagraph>>()
        var currentGroup = mutableListOf<AnnotatedParagraph>()
        var currentGroupSize = 0

        for (i in paragraphs.indices) {
            val para = paragraphs[i]
            val paraLength = para.content.length

            // 检查是否应该开始新组
            val shouldStartNewGroup = when {
                // 标题总是开始新组
                para.isHeading -> true

                // 当前组为空
                currentGroup.isEmpty() -> false

                // 当前组已经足够大
                currentGroupSize + paraLength > config.targetChunkSize -> true

                // 检测到主题变化（但只在当前组足够大时才考虑）
                config.detectTopicBoundary && i > 0 && currentGroupSize >= config.minChunkSize -> {
                    val prevPara = paragraphs[i - 1]
                    val similarity = calculateKeywordSimilarity(prevPara.keywords, para.keywords)
                    val hasTopicIndicator = TOPIC_CHANGE_INDICATORS.any {
                        para.content.lowercase().startsWith(it)
                    }
                    similarity < config.topicChangeThreshold || hasTopicIndicator
                }

                else -> false
            }

            if (shouldStartNewGroup && currentGroup.isNotEmpty()) {
                groups.add(currentGroup)
                currentGroup = mutableListOf()
                currentGroupSize = 0
            }

            currentGroup.add(para)
            currentGroupSize += paraLength
        }

        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }

        return groups
    }

    /**
     * 分割过长的内容
     */
    private fun splitLongContent(
        content: String,
        sectionTitle: String?,
        startIndex: Int
    ): List<SemanticChunk> {
        val chunks = mutableListOf<SemanticChunk>()
        val sentences = splitIntoSentences(content)

        var currentChunk = StringBuilder()
        var chunkIndex = startIndex

        for (sentence in sentences) {
            if (currentChunk.length + sentence.length > config.targetChunkSize &&
                currentChunk.isNotEmpty()
            ) {
                // 保存当前块
                if (currentChunk.length >= config.minChunkSize) {
                    chunks.add(
                        SemanticChunk(
                            content = currentChunk.toString().trim(),
                            index = chunkIndex++,
                            sectionTitle = sectionTitle
                        )
                    )
                }
                currentChunk = StringBuilder()
            }
            currentChunk.append(sentence)
        }

        // 保存最后一块
        if (currentChunk.isNotEmpty() && currentChunk.length >= config.minChunkSize) {
            chunks.add(
                SemanticChunk(
                    content = currentChunk.toString().trim(),
                    index = chunkIndex,
                    sectionTitle = sectionTitle
                )
            )
        }

        return chunks
    }

    /**
     * 分割为句子
     */
    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var current = StringBuilder()

        for (char in text) {
            current.append(char)
            if (char in SENTENCE_ENDINGS) {
                sentences.add(current.toString())
                current = StringBuilder()
            }
        }

        if (current.isNotEmpty()) {
            sentences.add(current.toString())
        }

        return sentences
    }

    /**
     * 确定块类型
     */
    private fun determineChunkType(paragraphs: List<AnnotatedParagraph>): SemanticChunk.ChunkType {
        return when {
            paragraphs.all { it.isHeading } -> SemanticChunk.ChunkType.HEADING
            paragraphs.all { it.isList } -> SemanticChunk.ChunkType.LIST
            else -> SemanticChunk.ChunkType.CONTENT
        }
    }

    /**
     * 添加上下文重叠
     */
    private fun addContextOverlap(chunks: List<SemanticChunk>): List<SemanticChunk> {
        if (chunks.size <= 1 || config.overlapSentences <= 0) return chunks

        return chunks.mapIndexed { index, chunk ->
            if (index == 0) {
                chunk
            } else {
                // 从上一块获取最后几个句子作为上下文
                val prevChunk = chunks[index - 1]
                val prevSentences = splitIntoSentences(prevChunk.content)
                val contextSentences = prevSentences.takeLast(config.overlapSentences)
                val context = contextSentences.joinToString("").trim()

                chunk.copy(contextPrefix = context)
            }
        }
    }

    /**
     * 带注释的段落
     */
    private data class AnnotatedParagraph(
        val content: String,
        val isHeading: Boolean,
        val isList: Boolean,
        val keywords: Set<String>
    )
}

/**
 * 将 SemanticChunk 转换为 TextChunk（兼容现有接口）
 */
fun SemanticChunk.toTextChunk(): TextChunk {
    return TextChunk(
        content = this.getFullContent(),
        index = this.index,
        startPosition = 0,  // 语义分块不跟踪位置
        endPosition = this.content.length
    )
}

/**
 * 批量转换
 */
fun List<SemanticChunk>.toTextChunks(): List<TextChunk> {
    return this.map { it.toTextChunk() }
}
