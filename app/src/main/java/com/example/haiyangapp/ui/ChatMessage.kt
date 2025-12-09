package com.example.haiyangapp.ui

/**
 * 引用来源信息
 */
data class SourceReference(
    val documentId: Long,
    val documentTitle: String,
    val sourcePath: String = "",  // 文档原始路径/URI，用于打开文件
    val similarity: Float = 0f
)

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val id: Int,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: String = "",
    // 版本管理信息（仅AI消息有效）
    val regenerateGroupId: Long? = null,  // 重新生成组ID
    val versionIndex: Int = 0,            // 当前版本索引
    val totalVersions: Int = 1,           // 总版本数
    // RAG 引用来源（仅AI消息有效）
    val sources: List<SourceReference> = emptyList()
)
