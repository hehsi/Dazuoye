package com.example.haiyangapp.database.dao

import androidx.room.*
import com.example.haiyangapp.database.entity.KnowledgeChunkEntity
import com.example.haiyangapp.database.entity.KnowledgeDocumentEntity
import kotlinx.coroutines.flow.Flow

/**
 * 知识库数据访问对象
 */
@Dao
interface KnowledgeDao {

    // ============================================
    // 文档操作
    // ============================================

    /**
     * 插入新文档
     */
    @Insert
    suspend fun insertDocument(document: KnowledgeDocumentEntity): Long

    /**
     * 更新文档
     */
    @Update
    suspend fun updateDocument(document: KnowledgeDocumentEntity)

    /**
     * 删除文档（会级联删除所有分块）
     */
    @Delete
    suspend fun deleteDocument(document: KnowledgeDocumentEntity)

    /**
     * 根据ID删除文档
     */
    @Query("DELETE FROM knowledge_documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: Long)

    /**
     * 获取所有文档（实时更新）
     */
    @Query("SELECT * FROM knowledge_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<KnowledgeDocumentEntity>>

    /**
     * 获取所有已索引的文档
     */
    @Query("SELECT * FROM knowledge_documents WHERE isIndexed = 1 ORDER BY createdAt DESC")
    fun getIndexedDocuments(): Flow<List<KnowledgeDocumentEntity>>

    /**
     * 根据ID获取文档
     */
    @Query("SELECT * FROM knowledge_documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: Long): KnowledgeDocumentEntity?

    /**
     * 更新文档的索引状态
     */
    @Query("UPDATE knowledge_documents SET isIndexed = :isIndexed, chunkCount = :chunkCount, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updateDocumentIndexStatus(
        documentId: Long,
        isIndexed: Boolean,
        chunkCount: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * 更新文档的索引进度
     */
    @Query("UPDATE knowledge_documents SET indexProgress = :progress, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updateDocumentIndexProgress(
        documentId: Long,
        progress: Float,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * 获取文档数量
     */
    @Query("SELECT COUNT(*) FROM knowledge_documents")
    suspend fun getDocumentCount(): Int

    /**
     * 检查是否有文档
     */
    @Query("SELECT EXISTS(SELECT 1 FROM knowledge_documents WHERE isIndexed = 1 LIMIT 1)")
    suspend fun hasIndexedDocuments(): Boolean

    // ============================================
    // 知识块操作
    // ============================================

    /**
     * 批量插入知识块
     */
    @Insert
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    /**
     * 插入单个知识块
     */
    @Insert
    suspend fun insertChunk(chunk: KnowledgeChunkEntity): Long

    /**
     * 删除文档的所有知识块
     */
    @Query("DELETE FROM knowledge_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: Long)

    /**
     * 获取文档的所有知识块
     */
    @Query("SELECT * FROM knowledge_chunks WHERE documentId = :documentId ORDER BY chunkIndex ASC")
    suspend fun getChunksByDocumentId(documentId: Long): List<KnowledgeChunkEntity>

    /**
     * 获取所有知识块（用于向量检索）
     */
    @Query("SELECT * FROM knowledge_chunks")
    suspend fun getAllChunks(): List<KnowledgeChunkEntity>

    /**
     * 获取指定文档集合的所有知识块
     */
    @Query("SELECT * FROM knowledge_chunks WHERE documentId IN (:documentIds)")
    suspend fun getChunksByDocumentIds(documentIds: List<Long>): List<KnowledgeChunkEntity>

    /**
     * 根据ID获取知识块
     */
    @Query("SELECT * FROM knowledge_chunks WHERE id = :chunkId")
    suspend fun getChunkById(chunkId: Long): KnowledgeChunkEntity?

    /**
     * 获取知识块总数
     */
    @Query("SELECT COUNT(*) FROM knowledge_chunks")
    suspend fun getChunkCount(): Int

    /**
     * 获取文档的知识块数量
     */
    @Query("SELECT COUNT(*) FROM knowledge_chunks WHERE documentId = :documentId")
    suspend fun getChunkCountByDocumentId(documentId: Long): Int

    // ============================================
    // 联合查询
    // ============================================

    /**
     * 获取知识块及其所属文档信息
     */
    @Query("""
        SELECT c.*, d.title as documentTitle, d.sourcePath as sourcePath
        FROM knowledge_chunks c
        INNER JOIN knowledge_documents d ON c.documentId = d.id
        WHERE c.id = :chunkId
    """)
    suspend fun getChunkWithDocumentTitle(chunkId: Long): ChunkWithDocumentTitle?

    /**
     * 批量获取知识块及其所属文档信息
     */
    @Query("""
        SELECT c.*, d.title as documentTitle, d.sourcePath as sourcePath
        FROM knowledge_chunks c
        INNER JOIN knowledge_documents d ON c.documentId = d.id
        WHERE c.id IN (:chunkIds)
    """)
    suspend fun getChunksWithDocumentTitles(chunkIds: List<Long>): List<ChunkWithDocumentTitle>
}

/**
 * 知识块与文档标题的联合结果
 */
data class ChunkWithDocumentTitle(
    val id: Long,
    val documentId: Long,
    val content: String,
    val chunkIndex: Int,
    val embedding: ByteArray,
    val tokenCount: Int,
    val metadata: String?,
    val documentTitle: String,
    val sourcePath: String  // 文档原始路径/URI
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChunkWithDocumentTitle
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
