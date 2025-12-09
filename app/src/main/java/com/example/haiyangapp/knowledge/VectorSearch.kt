package com.example.haiyangapp.knowledge

import android.util.Log
import com.example.haiyangapp.database.dao.ChunkWithDocumentTitle
import com.example.haiyangapp.database.dao.KnowledgeDao
import com.example.haiyangapp.database.entity.KnowledgeChunkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * 检索结果
 */
data class RetrievalResult(
    /** 知识块 ID */
    val chunkId: Long,

    /** 所属文档 ID */
    val documentId: Long,

    /** 文档标题 */
    val documentTitle: String,

    /** 文档原始路径/URI */
    val sourcePath: String,

    /** 知识块内容 */
    val content: String,

    /** 相似度分数 (0-1) */
    val similarity: Float,

    /** 块在文档中的索引 */
    val chunkIndex: Int
)

/**
 * 检索配置
 */
data class SearchConfig(
    /** 返回的最大结果数 */
    val topK: Int = 3,

    /** 最低相似度阈值 */
    val similarityThreshold: Float = 0.15f,

    /** 是否使用关键词重排序 */
    val useKeywordReranking: Boolean = true,

    /** 关键词重排序权重 */
    val keywordWeight: Float = 0.2f
)

/**
 * 向量检索引擎
 * 实现基于余弦相似度的语义检索
 */
class VectorSearch(private val knowledgeDao: KnowledgeDao) {

    companion object {
        private const val TAG = "VectorSearch"
    }

    /**
     * 检索与查询最相关的知识块
     *
     * @param queryEmbedding 查询文本的嵌入向量
     * @param query 原始查询文本（用于关键词重排序）
     * @param config 检索配置
     * @return 检索结果列表（按相似度降序）
     */
    suspend fun search(
        queryEmbedding: FloatArray,
        query: String,
        config: SearchConfig = SearchConfig()
    ): List<RetrievalResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Search started for query: $query")
        Log.d(TAG, "Query embedding dimension: ${queryEmbedding.size}")
        Log.d(TAG, "Query embedding sample (first 5): ${queryEmbedding.take(5).joinToString()}")

        // 获取所有知识块
        val allChunks = knowledgeDao.getAllChunks()
        Log.d(TAG, "Total chunks in database: ${allChunks.size}")

        if (allChunks.isEmpty()) {
            Log.w(TAG, "No chunks found in database!")
            return@withContext emptyList()
        }

        // 调试：打印第一个 chunk 的信息
        val firstChunk = allChunks.first()
        val firstChunkEmbedding = firstChunk.getEmbeddingAsFloatArray()
        Log.d(TAG, "First chunk content preview: ${firstChunk.content.take(50)}...")
        Log.d(TAG, "First chunk embedding dimension: ${firstChunkEmbedding.size}")
        Log.d(TAG, "First chunk embedding sample (first 5): ${firstChunkEmbedding.take(5).joinToString()}")

        // 计算相似度并排序
        var maxSimilarity = 0f
        var minSimilarity = 1f
        val results = allChunks.mapNotNull { chunk ->
            val chunkEmbedding = chunk.getEmbeddingAsFloatArray()
            val similarity = cosineSimilarity(queryEmbedding, chunkEmbedding)

            // 追踪最大和最小相似度
            if (similarity > maxSimilarity) maxSimilarity = similarity
            if (similarity < minSimilarity) minSimilarity = similarity

            if (similarity >= config.similarityThreshold) {
                chunk to similarity
            } else {
                null
            }
        }.sortedByDescending { it.second }
            .take(config.topK * 2)  // 取更多候选项用于重排序

        Log.d(TAG, "Similarity range: min=$minSimilarity, max=$maxSimilarity")
        Log.d(TAG, "Chunks passing threshold (${config.similarityThreshold}): ${results.size}")

        if (results.isEmpty()) {
            return@withContext emptyList()
        }

        // 获取文档标题
        val chunkIds = results.map { it.first.id }
        val chunksWithTitles = knowledgeDao.getChunksWithDocumentTitles(chunkIds)
        val titleMap = chunksWithTitles.associateBy { it.id }

        // 转换为结果对象
        var retrievalResults = results.map { (chunk, similarity) ->
            val docInfo = titleMap[chunk.id]
            RetrievalResult(
                chunkId = chunk.id,
                documentId = chunk.documentId,
                documentTitle = docInfo?.documentTitle ?: "未知文档",
                sourcePath = docInfo?.sourcePath ?: "",
                content = chunk.content,
                similarity = similarity,
                chunkIndex = chunk.chunkIndex
            )
        }

        // 关键词重排序
        if (config.useKeywordReranking && query.isNotBlank()) {
            retrievalResults = rerank(query, retrievalResults, config.keywordWeight)
        }

        // 返回 Top-K
        retrievalResults.take(config.topK)
    }

    /**
     * 基于指定文档集合的检索
     */
    suspend fun searchInDocuments(
        queryEmbedding: FloatArray,
        query: String,
        documentIds: List<Long>,
        config: SearchConfig = SearchConfig()
    ): List<RetrievalResult> = withContext(Dispatchers.IO) {
        if (documentIds.isEmpty()) {
            return@withContext emptyList()
        }

        val chunks = knowledgeDao.getChunksByDocumentIds(documentIds)

        if (chunks.isEmpty()) {
            return@withContext emptyList()
        }

        val results = chunks.mapNotNull { chunk ->
            val chunkEmbedding = chunk.getEmbeddingAsFloatArray()
            val similarity = cosineSimilarity(queryEmbedding, chunkEmbedding)

            if (similarity >= config.similarityThreshold) {
                chunk to similarity
            } else {
                null
            }
        }.sortedByDescending { it.second }
            .take(config.topK * 2)

        if (results.isEmpty()) {
            return@withContext emptyList()
        }

        val chunkIds = results.map { it.first.id }
        val chunksWithTitles = knowledgeDao.getChunksWithDocumentTitles(chunkIds)
        val titleMap = chunksWithTitles.associateBy { it.id }

        var retrievalResults = results.map { (chunk, similarity) ->
            val docInfo = titleMap[chunk.id]
            RetrievalResult(
                chunkId = chunk.id,
                documentId = chunk.documentId,
                documentTitle = docInfo?.documentTitle ?: "未知文档",
                sourcePath = docInfo?.sourcePath ?: "",
                content = chunk.content,
                similarity = similarity,
                chunkIndex = chunk.chunkIndex
            )
        }

        if (config.useKeywordReranking && query.isNotBlank()) {
            retrievalResults = rerank(query, retrievalResults, config.keywordWeight)
        }

        retrievalResults.take(config.topK)
    }

    /**
     * 计算余弦相似度
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 相似度 (0-1)
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) {
            Log.e(TAG, "Dimension mismatch! a.size=${a.size}, b.size=${b.size}")
            return 0f
        }

        // 检查是否为空向量
        if (a.isEmpty() || b.isEmpty()) {
            Log.e(TAG, "Empty embedding detected!")
            return 0f
        }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) {
            dotProduct / denominator
        } else {
            Log.w(TAG, "Zero norm detected! normA=$normA, normB=$normB")
            0f
        }
    }

    /**
     * 关键词重排序
     * 结合向量相似度和关键词匹配度
     */
    private fun rerank(
        query: String,
        results: List<RetrievalResult>,
        keywordWeight: Float
    ): List<RetrievalResult> {
        // 提取查询关键词（简单分词）
        val queryKeywords = extractKeywords(query)

        if (queryKeywords.isEmpty()) {
            return results
        }

        return results.map { result ->
            val contentKeywords = extractKeywords(result.content)
            val keywordScore = calculateKeywordOverlap(queryKeywords, contentKeywords)

            // 综合分数 = 向量相似度 * (1 - keywordWeight) + 关键词分数 * keywordWeight
            val combinedScore = result.similarity * (1 - keywordWeight) + keywordScore * keywordWeight

            result.copy(similarity = combinedScore)
        }.sortedByDescending { it.similarity }
    }

    /**
     * 提取关键词（简单实现）
     */
    private fun extractKeywords(text: String): Set<String> {
        // 简单的中英文分词
        return text.lowercase()
            // 按标点和空白分割
            .split(Regex("[\\s,.!?;:，。！？；：、\\n\\r]+"))
            // 过滤掉太短的词
            .filter { it.length >= 2 }
            .toSet()
    }

    /**
     * 计算关键词重叠度
     */
    private fun calculateKeywordOverlap(
        queryKeywords: Set<String>,
        contentKeywords: Set<String>
    ): Float {
        if (queryKeywords.isEmpty()) return 0f

        val overlap = queryKeywords.intersect(contentKeywords)
        return overlap.size.toFloat() / queryKeywords.size
    }
}

/**
 * 检索结果缓存
 */
class RetrievalCache(
    private val maxSize: Int = 50
) {
    private val cache = LinkedHashMap<String, List<RetrievalResult>>(
        maxSize,
        0.75f,
        true  // accessOrder = true for LRU
    )

    /**
     * 获取缓存的结果
     */
    @Synchronized
    fun get(query: String): List<RetrievalResult>? {
        return cache[query]
    }

    /**
     * 缓存结果
     */
    @Synchronized
    fun put(query: String, results: List<RetrievalResult>) {
        // 移除最旧的条目
        if (cache.size >= maxSize) {
            cache.remove(cache.keys.first())
        }
        cache[query] = results
    }

    /**
     * 清空缓存
     */
    @Synchronized
    fun clear() {
        cache.clear()
    }
}
