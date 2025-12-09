package com.example.haiyangapp.knowledge

/**
 * 文本分块配置
 */
data class ChunkConfig(
    /** 每块的目标字符数 */
    val chunkSize: Int = 500,

    /** 块之间的重叠字符数 */
    val overlapSize: Int = 50,

    /** 最小块大小（小于此值的块会被合并或丢弃） */
    val minChunkSize: Int = 100,

    /** 是否按句子边界分割 */
    val respectSentenceBoundary: Boolean = true,

    /** 是否按段落边界分割 */
    val respectParagraphBoundary: Boolean = true
)

/**
 * 分块结果
 */
data class TextChunk(
    /** 块内容 */
    val content: String,

    /** 块在原文中的索引 */
    val index: Int,

    /** 块的起始字符位置 */
    val startPosition: Int,

    /** 块的结束字符位置 */
    val endPosition: Int
)

/**
 * 文本分块器
 * 将长文本智能分割成适合嵌入的小块
 */
class TextChunker(
    private val config: ChunkConfig = ChunkConfig()
) {
    companion object {
        // 句子结束标点
        private val SENTENCE_ENDINGS = setOf('。', '！', '？', '.', '!', '?', '；', ';')

        // 段落分隔符
        private val PARAGRAPH_SEPARATOR = "\n\n"
    }

    /**
     * 将文本分割成块
     *
     * @param text 要分割的文本
     * @return 分块列表
     */
    fun chunk(text: String): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        val cleanedText = text.trim()

        // 如果文本足够短，直接返回单块
        if (cleanedText.length <= config.chunkSize) {
            return listOf(
                TextChunk(
                    content = cleanedText,
                    index = 0,
                    startPosition = 0,
                    endPosition = cleanedText.length
                )
            )
        }

        val chunks = mutableListOf<TextChunk>()

        // 首先按段落分割
        val paragraphs = if (config.respectParagraphBoundary) {
            splitByParagraphs(cleanedText)
        } else {
            listOf(cleanedText)
        }

        var globalPosition = 0
        var chunkIndex = 0

        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) {
                globalPosition += paragraph.length + 2  // +2 for "\n\n"
                continue
            }

            // 如果段落本身足够短
            if (paragraph.length <= config.chunkSize) {
                if (paragraph.length >= config.minChunkSize) {
                    chunks.add(
                        TextChunk(
                            content = paragraph,
                            index = chunkIndex++,
                            startPosition = globalPosition,
                            endPosition = globalPosition + paragraph.length
                        )
                    )
                } else if (chunks.isNotEmpty()) {
                    // 尝试合并到上一个块
                    val lastChunk = chunks.removeLast()
                    val merged = lastChunk.content + "\n\n" + paragraph
                    if (merged.length <= config.chunkSize * 1.5) {
                        chunks.add(
                            lastChunk.copy(
                                content = merged,
                                endPosition = globalPosition + paragraph.length
                            )
                        )
                        chunkIndex--
                    } else {
                        chunks.add(lastChunk)
                        chunks.add(
                            TextChunk(
                                content = paragraph,
                                index = chunkIndex++,
                                startPosition = globalPosition,
                                endPosition = globalPosition + paragraph.length
                            )
                        )
                    }
                }
            } else {
                // 段落太长，需要进一步分割
                val paragraphChunks = splitLongParagraph(
                    paragraph,
                    globalPosition,
                    chunkIndex
                )
                chunks.addAll(paragraphChunks)
                chunkIndex += paragraphChunks.size
            }

            globalPosition += paragraph.length + 2
        }

        // 后处理：应用重叠
        return if (config.overlapSize > 0 && chunks.size > 1) {
            applyOverlap(chunks)
        } else {
            chunks
        }
    }

    /**
     * 按段落分割文本
     */
    private fun splitByParagraphs(text: String): List<String> {
        return text.split(PARAGRAPH_SEPARATOR)
    }

    /**
     * 分割长段落
     */
    private fun splitLongParagraph(
        paragraph: String,
        basePosition: Int,
        baseIndex: Int
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()

        if (config.respectSentenceBoundary) {
            // 按句子分割
            val sentences = splitBySentences(paragraph)
            var currentChunk = StringBuilder()
            var currentStart = 0
            var positionInParagraph = 0
            var chunkIndex = baseIndex

            for (sentence in sentences) {
                if (currentChunk.length + sentence.length <= config.chunkSize) {
                    currentChunk.append(sentence)
                } else {
                    // 保存当前块
                    if (currentChunk.isNotEmpty() && currentChunk.length >= config.minChunkSize) {
                        chunks.add(
                            TextChunk(
                                content = currentChunk.toString().trim(),
                                index = chunkIndex++,
                                startPosition = basePosition + currentStart,
                                endPosition = basePosition + positionInParagraph
                            )
                        )
                    }
                    currentStart = positionInParagraph
                    currentChunk = StringBuilder(sentence)
                }
                positionInParagraph += sentence.length
            }

            // 保存最后一块
            if (currentChunk.isNotEmpty() && currentChunk.length >= config.minChunkSize) {
                chunks.add(
                    TextChunk(
                        content = currentChunk.toString().trim(),
                        index = chunkIndex,
                        startPosition = basePosition + currentStart,
                        endPosition = basePosition + paragraph.length
                    )
                )
            }
        } else {
            // 简单按字符数分割
            var start = 0
            var chunkIndex = baseIndex

            while (start < paragraph.length) {
                val end = minOf(start + config.chunkSize, paragraph.length)
                val chunkContent = paragraph.substring(start, end).trim()

                if (chunkContent.length >= config.minChunkSize) {
                    chunks.add(
                        TextChunk(
                            content = chunkContent,
                            index = chunkIndex++,
                            startPosition = basePosition + start,
                            endPosition = basePosition + end
                        )
                    )
                }

                start = end
            }
        }

        return chunks
    }

    /**
     * 按句子分割文本
     */
    private fun splitBySentences(text: String): List<String> {
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
     * 应用块之间的重叠
     */
    private fun applyOverlap(chunks: List<TextChunk>): List<TextChunk> {
        if (chunks.size <= 1 || config.overlapSize <= 0) return chunks

        return chunks.mapIndexed { index, chunk ->
            if (index == 0) {
                // 第一块：添加下一块的开头作为后缀
                val nextChunk = chunks.getOrNull(index + 1)
                if (nextChunk != null) {
                    val overlap = nextChunk.content.take(config.overlapSize)
                    chunk.copy(content = chunk.content + " " + overlap)
                } else {
                    chunk
                }
            } else {
                // 后续块：添加上一块的结尾作为前缀
                val prevChunk = chunks[index - 1]
                val overlap = prevChunk.content.takeLast(config.overlapSize)
                chunk.copy(content = overlap + " " + chunk.content)
            }
        }
    }
}
