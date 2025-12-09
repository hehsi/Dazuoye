package com.example.haiyangapp.repository

import com.example.haiyangapp.api.ToolSource
import com.example.haiyangapp.api.models.DeepSeekMessage
import com.example.haiyangapp.database.entity.ConversationEntity
import com.example.haiyangapp.database.entity.MessageEntity
import com.example.haiyangapp.knowledge.RetrievalResult
import kotlinx.coroutines.flow.Flow

/**
 * 聊天数据仓库接口
 * 定义与AI对话相关的数据操作
 */
interface ChatRepository {

    /**
     * 发送消息到AI并获取流式回复
     * @param conversationHistory 完整的对话历史（包含上下文）
     * @param onContent 接收流式内容片段的回调函数
     * @return Result封装的完整AI回复消息，失败时返回异常
     */
    suspend fun sendMessageStream(
        conversationHistory: List<DeepSeekMessage>,
        onContent: (String) -> Unit
    ): Result<String>

    /**
     * 发送消息到AI并获取流式回复（带RAG上下文）
     * @param conversationHistory 完整的对话历史（包含上下文）
     * @param ragContext 检索到的相关知识块
     * @param onContent 接收流式内容片段的回调函数
     * @return Result封装的完整AI回复消息，失败时返回异常
     */
    suspend fun sendMessageWithRAG(
        conversationHistory: List<DeepSeekMessage>,
        ragContext: List<RetrievalResult>,
        onContent: (String) -> Unit
    ): Result<String> {
        // 默认实现：不使用RAG，直接调用普通的流式发送
        return sendMessageStream(conversationHistory, onContent)
    }

    // ==================== 对话管理 ====================

    /**
     * 获取所有对话列表
     * @return Flow of conversations ordered by last message time
     */
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /**
     * 创建新对话
     * @param title 对话标题
     * @return 新创建的对话ID
     */
    suspend fun createConversation(title: String = "新对话"): Long

    /**
     * 获取或创建空对话
     * 如果存在没有消息的空对话，则复用它；否则创建新对话
     * 用于避免每次打开APP都创建空对话
     * @return 对话ID
     */
    suspend fun getOrCreateEmptyConversation(): Long

    /**
     * 删除对话
     * @param conversationId 对话ID
     */
    suspend fun deleteConversation(conversationId: Long)

    /**
     * 更新对话标题
     * @param conversationId 对话ID
     * @param title 新标题
     */
    suspend fun updateConversationTitle(conversationId: Long, title: String)

    // ==================== 消息管理 ====================

    /**
     * 获取指定对话的所有消息
     * @param conversationId 对话ID
     * @return Flow of messages ordered by timestamp
     */
    fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>>

    /**
     * 保存消息到数据库
     * @param conversationId 对话ID
     * @param content 消息内容
     * @param isFromUser 是否为用户消息
     * @return 消息ID
     */
    suspend fun saveMessage(conversationId: Long, content: String, isFromUser: Boolean): Long

    /**
     * 更新消息内容
     * @param messageId 消息ID
     * @param content 新内容
     */
    suspend fun updateMessageContent(messageId: Long, content: String)

    /**
     * 更新消息的点赞状态
     * @param messageId 消息ID
     * @param isLiked 是否点赞
     */
    suspend fun updateMessageLikeStatus(messageId: Long, isLiked: Boolean)

    /**
     * 删除消息
     * @param messageId 消息ID
     */
    suspend fun deleteMessage(messageId: Long)

    // ==================== 推荐问题生成 ====================

    /**
     * 获取最近的用户消息
     * @param limit 获取的消息数量
     * @return 最近的用户消息列表
     */
    suspend fun getRecentUserMessages(limit: Int): List<MessageEntity>

    /**
     * 基于历史生成推荐问题
     * @param historyMessages 历史消息内容
     * @return 生成的推荐问题列表
     */
    suspend fun generateSuggestions(historyMessages: List<String>): Result<List<String>>

    // ==================== 消息重新生成 ====================

    /**
     * 版本信息数据类
     */
    data class VersionInfo(
        val currentIndex: Int,    // 当前版本索引（从0开始）
        val totalCount: Int,      // 总版本数
        val versions: List<MessageEntity>  // 所有版本的消息
    )

    /**
     * 获取消息的版本信息
     * @param messageId 消息ID
     * @return 版本信息，如果消息没有版本组则返回null
     */
    suspend fun getVersionInfo(messageId: Long): VersionInfo?

    /**
     * 切换到指定版本
     * @param groupId 重新生成组ID
     * @param targetVersionIndex 目标版本索引
     * @return 切换后的当前消息
     */
    suspend fun switchVersion(groupId: Long, targetVersionIndex: Int): MessageEntity?

    /**
     * 保存重新生成的AI消息（创建新版本）
     * @param conversationId 对话ID
     * @param content 消息内容
     * @param groupId 重新生成组ID（对应用户消息的ID）
     * @return 新消息的ID
     */
    suspend fun saveRegeneratedMessage(
        conversationId: Long,
        content: String,
        groupId: Long
    ): Long

    /**
     * 将原始消息初始化为版本组的第一个版本
     * 用于第一次重新生成时，把原始AI消息加入版本组
     * @param messageId 原始消息ID
     * @param groupId 版本组ID（对应用户消息的ID）
     */
    suspend fun initializeAsFirstVersion(messageId: Long, groupId: Long)

    // ==================== Function Calling ====================

    /**
     * 发送消息到AI并获取流式回复（支持Function Calling）
     * @param conversationHistory 完整的对话历史（包含上下文）
     * @param enableFunctionCalling 是否启用函数调用
     * @param onContent 接收流式内容片段的回调函数
     * @param onToolCall 工具调用状态回调（可选）
     * @param onToolSources 工具来源信息回调（可选，用于显示搜索结果链接）
     * @return Result封装的完整AI回复消息，失败时返回异常
     */
    suspend fun sendMessageWithTools(
        conversationHistory: List<DeepSeekMessage>,
        enableFunctionCalling: Boolean = true,
        onContent: (String) -> Unit,
        onToolCall: ((String) -> Unit)? = null,
        onToolSources: ((List<ToolSource>) -> Unit)? = null
    ): Result<String> {
        // 默认实现：不支持Function Calling，直接调用普通的流式发送
        return sendMessageStream(conversationHistory, onContent)
    }
}
