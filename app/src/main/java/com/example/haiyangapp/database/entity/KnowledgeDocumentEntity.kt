package com.example.haiyangapp.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 知识库文档实体
 * 存储用户导入的文档元信息
 */
@Entity(tableName = "knowledge_documents")
data class KnowledgeDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 文档标题 */
    val title: String,

    /** 文档类型: pdf / docx / txt */
    val sourceType: String,

    /** 原始文件路径或URI */
    val sourcePath: String,

    /** 文件大小（字节） */
    val fileSize: Long,

    /** 分块数量 */
    val chunkCount: Int = 0,

    /** 是否已完成索引 */
    val isIndexed: Boolean = false,

    /** 索引进度 (0.0 - 1.0) */
    val indexProgress: Float = 0f,

    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 最后更新时间 */
    val updatedAt: Long = System.currentTimeMillis()
)
