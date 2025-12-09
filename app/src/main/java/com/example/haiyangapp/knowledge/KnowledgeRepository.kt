package com.example.haiyangapp.knowledge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.haiyangapp.database.dao.KnowledgeDao
import com.example.haiyangapp.database.entity.KnowledgeChunkEntity
import com.example.haiyangapp.database.entity.KnowledgeDocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 知识库仓库接口
 */
interface KnowledgeRepository {

    /**
     * 添加文档到知识库
     *
     * @param uri 文档 URI
     * @param title 文档标题（可选，为空时自动从文件名获取）
     * @return 文档 ID，失败返回 Result.failure
     */
    suspend fun addDocument(uri: Uri, title: String? = null): Result<Long>

    /**
     * 删除文档
     */
    suspend fun deleteDocument(documentId: Long)

    /**
     * 获取所有文档
     */
    fun getAllDocuments(): Flow<List<KnowledgeDocumentEntity>>

    /**
     * 检查是否有已索引的文档
     */
    suspend fun hasIndexedDocuments(): Boolean

    /**
     * 检索相关知识块
     *
     * @param query 查询文本
     * @param topK 返回的最大结果数
     * @return 检索结果列表
     */
    suspend fun searchRelevantChunks(query: String, topK: Int = 3): List<RetrievalResult>

    /**
     * 获取索引进度
     */
    fun getIndexingProgress(): StateFlow<Map<Long, Float>>
}

/**
 * 知识库仓库实现
 */
class KnowledgeRepositoryImpl(
    private val context: Context,
    private val knowledgeDao: KnowledgeDao,
    private val embeddingManager: EmbeddingManager
) : KnowledgeRepository {

    companion object {
        private const val TAG = "KnowledgeRepository"
    }

    // 文档处理器
    private val pdfProcessor = PdfDocumentProcessor()
    private val wordProcessor = WordDocumentProcessor()

    // 语义分块器（替代简单的文本分块器）
    private val semanticChunker = SemanticChunker(
        SemanticChunkConfig(
            targetChunkSize = 400,      // 目标块大小
            maxChunkSize = 600,         // 最大块大小
            minChunkSize = 100,         // 最小块大小
            overlapSentences = 1,       // 重叠句子数
            detectHeadings = true,      // 检测标题
            detectTopicBoundary = true  // 检测主题边界
        )
    )

    // 向量检索
    private val vectorSearch = VectorSearch(knowledgeDao)

    // 检索缓存
    private val retrievalCache = RetrievalCache()

    // 索引进度
    private val _indexingProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())

    override suspend fun addDocument(uri: Uri, title: String?): Result<Long> =
        withContext(Dispatchers.IO) {
            try {
                // 获取文件信息
                val fileInfo = getFileInfo(uri)
                    ?: return@withContext Result.failure(Exception("无法读取文件信息"))

                val documentTitle = title ?: fileInfo.name
                val extension = fileInfo.name.substringAfterLast('.', "").lowercase()

                // 选择处理器
                val processor = when {
                    pdfProcessor.isSupported(fileInfo.name) -> pdfProcessor
                    wordProcessor.isSupported(fileInfo.name) -> wordProcessor
                    else -> return@withContext Result.failure(
                        Exception("不支持的文件格式: $extension")
                    )
                }

                Log.i(TAG, "Adding document: $documentTitle, type: $extension")

                // 创建文档记录
                val document = KnowledgeDocumentEntity(
                    title = documentTitle,
                    sourceType = processor.getDocumentType(),
                    sourcePath = uri.toString(),
                    fileSize = fileInfo.size,
                    isIndexed = false
                )

                val documentId = knowledgeDao.insertDocument(document)
                Log.i(TAG, "Document created with ID: $documentId")

                // 更新进度
                updateProgress(documentId, 0.1f)

                // 提取文本
                val textResult = processor.extractText(context, uri)
                if (textResult.isFailure) {
                    knowledgeDao.deleteDocumentById(documentId)
                    return@withContext Result.failure(textResult.exceptionOrNull()!!)
                }

                val text = textResult.getOrThrow()
                Log.i(TAG, "Extracted text length: ${text.length}")
                updateProgress(documentId, 0.2f)

                // 语义分块
                val semanticChunks = semanticChunker.chunk(text)
                Log.i(TAG, "Created ${semanticChunks.size} semantic chunks")

                // 打印分块详情（调试用）
                semanticChunks.take(3).forEachIndexed { idx, chunk ->
                    Log.d(TAG, "Chunk $idx: type=${chunk.type}, section=${chunk.sectionTitle}, len=${chunk.content.length}")
                    Log.d(TAG, "Chunk $idx preview: ${chunk.content.take(100)}...")
                }

                updateProgress(documentId, 0.3f)

                // 转换为 TextChunk 兼容格式
                val chunks = semanticChunks.toTextChunks()

                if (chunks.isEmpty()) {
                    knowledgeDao.deleteDocumentById(documentId)
                    return@withContext Result.failure(Exception("文档内容为空"))
                }

                // 生成嵌入向量并存储
                val chunkEntities = mutableListOf<KnowledgeChunkEntity>()
                val batchSize = 10
                var nullEmbeddingCount = 0

                chunks.forEachIndexed { index, chunk ->
                    val embedding = embeddingManager.embed(chunk.content)
                    if (embedding != null) {
                        // 调试：检查嵌入向量
                        if (index == 0) {
                            Log.d(TAG, "First chunk embedding dimension: ${embedding.size}")
                            Log.d(TAG, "First chunk embedding sample: ${embedding.take(5).joinToString()}")
                        }
                        chunkEntities.add(
                            KnowledgeChunkEntity(
                                documentId = documentId,
                                content = chunk.content,
                                chunkIndex = index,
                                embedding = KnowledgeChunkEntity.floatArrayToByteArray(embedding),
                                tokenCount = chunk.content.length / 4  // 粗略估计
                            )
                        )
                    } else {
                        nullEmbeddingCount++
                        Log.w(TAG, "Null embedding for chunk $index: ${chunk.content.take(50)}...")
                    }

                    // 更新进度 (30% - 90%)
                    val progress = 0.3f + (index.toFloat() / chunks.size) * 0.6f
                    updateProgress(documentId, progress)

                    // 批量插入
                    if (chunkEntities.size >= batchSize) {
                        knowledgeDao.insertChunks(chunkEntities.toList())
                        chunkEntities.clear()
                    }
                }

                Log.i(TAG, "Embedding generation complete. Null embeddings: $nullEmbeddingCount/${chunks.size}")

                // 插入剩余的块
                if (chunkEntities.isNotEmpty()) {
                    knowledgeDao.insertChunks(chunkEntities)
                }

                // 更新文档状态
                knowledgeDao.updateDocumentIndexStatus(
                    documentId = documentId,
                    isIndexed = true,
                    chunkCount = chunks.size
                )

                updateProgress(documentId, 1.0f)
                Log.i(TAG, "Document indexing completed: $documentId")

                // 清除进度（延迟清除，让 UI 有时间显示完成状态）
                kotlinx.coroutines.delay(1000)
                removeProgress(documentId)

                // 清除检索缓存
                retrievalCache.clear()

                Result.success(documentId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add document", e)
                Result.failure(e)
            }
        }

    override suspend fun deleteDocument(documentId: Long) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Deleting document: $documentId")
        knowledgeDao.deleteDocumentById(documentId)
        retrievalCache.clear()
    }

    override fun getAllDocuments(): Flow<List<KnowledgeDocumentEntity>> {
        return knowledgeDao.getAllDocuments()
    }

    override suspend fun hasIndexedDocuments(): Boolean {
        return knowledgeDao.hasIndexedDocuments()
    }

    override suspend fun searchRelevantChunks(query: String, topK: Int): List<RetrievalResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                return@withContext emptyList()
            }

            // 检查缓存
            val cached = retrievalCache.get(query)
            if (cached != null) {
                Log.d(TAG, "Cache hit for query: $query")
                return@withContext cached
            }

            Log.d(TAG, "Generating query embedding for: $query")

            // 生成查询嵌入
            val queryEmbedding = embeddingManager.embed(query)
            if (queryEmbedding == null) {
                Log.e(TAG, "Failed to generate query embedding")
                return@withContext emptyList()
            }

            Log.d(TAG, "Query embedding generated, dimension: ${queryEmbedding.size}")
            Log.d(TAG, "Query embedding sample: ${queryEmbedding.take(5).joinToString()}")

            // 执行检索
            val results = vectorSearch.search(
                queryEmbedding = queryEmbedding,
                query = query,
                config = SearchConfig(topK = topK)
            )

            // 缓存结果
            retrievalCache.put(query, results)

            Log.i(TAG, "Retrieved ${results.size} chunks for query: $query")
            results
        }

    override fun getIndexingProgress(): StateFlow<Map<Long, Float>> {
        return _indexingProgress.asStateFlow()
    }

    /**
     * 获取文件信息
     */
    private fun getFileInfo(uri: Uri): FileInfo? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "unknown"
                    val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L

                    FileInfo(name, size)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file info", e)
            null
        }
    }

    private fun updateProgress(documentId: Long, progress: Float) {
        val current = _indexingProgress.value.toMutableMap()
        current[documentId] = progress
        _indexingProgress.value = current
    }

    private fun removeProgress(documentId: Long) {
        val current = _indexingProgress.value.toMutableMap()
        current.remove(documentId)
        _indexingProgress.value = current
    }

    private data class FileInfo(val name: String, val size: Long)
}
