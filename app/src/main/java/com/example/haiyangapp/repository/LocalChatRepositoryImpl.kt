package com.example.haiyangapp.repository

import android.util.Log
import com.example.haiyangapp.api.models.DeepSeekMessage
import com.example.haiyangapp.database.dao.ConversationDao
import com.example.haiyangapp.database.dao.MessageDao
import com.example.haiyangapp.database.entity.ConversationEntity
import com.example.haiyangapp.database.entity.MessageEntity
import com.example.haiyangapp.inference.InferenceRepository
import com.example.haiyangapp.knowledge.RetrievalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 本地聊天数据仓库实现
 * 使用本地LLM推理代替远程API调用
 */
class LocalChatRepositoryImpl(
    private val inferenceRepository: InferenceRepository,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {

    companion object {
        private const val TAG = "LocalChatRepository"
    }

    /**
     * 发送消息到本地LLM并支持流式回调
     * @param conversationHistory 完整对话历史
     * @param onContent 接收每个流式内容片段的回调函数
     * @return AI的回复内容或错误信息
     */
    override suspend fun sendMessageStream(
        conversationHistory: List<DeepSeekMessage>,
        onContent: (String) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查推理引擎是否就绪
                if (!inferenceRepository.isReady()) {
                    Log.e(TAG, "Inference engine not ready")
                    return@withContext Result.failure(
                        Exception("本地模型未加载,请稍候重试")
                    )
                }

                // 转换DeepSeekMessage格式为推理引擎需要的格式
                // 过滤掉content为null的消息（如tool_calls消息）
                val conversationPairs = conversationHistory
                    .filter { it.content != null }
                    .map { msg ->
                        Pair(msg.role, msg.content!!)
                    }

                Log.d(TAG, "Sending ${conversationPairs.size} messages to local inference")

                // 调用本地推理
                val result = inferenceRepository.sendMessageStream(
                    conversationHistory = conversationPairs,
                    onToken = { token ->
                        // 回调到UI层
                        onContent(token)
                    }
                )

                result
            } catch (e: Exception) {
                Log.e(TAG, "Local inference failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 发送消息到本地LLM并支持流式回调（带RAG上下文）
     * 将检索到的知识块注入到系统消息中
     */
    override suspend fun sendMessageWithRAG(
        conversationHistory: List<DeepSeekMessage>,
        ragContext: List<RetrievalResult>,
        onContent: (String) -> Unit
    ): Result<String> {
        if (ragContext.isEmpty()) {
            // 没有RAG上下文，使用普通发送
            return sendMessageStream(conversationHistory, onContent)
        }

        Log.d(TAG, "Sending message with ${ragContext.size} RAG context chunks")

        // 构建增强的对话历史
        val augmentedHistory = injectRAGContext(conversationHistory, ragContext)
        return sendMessageStream(augmentedHistory, onContent)
    }

    /**
     * 将RAG上下文注入到对话历史中
     */
    private fun injectRAGContext(
        history: List<DeepSeekMessage>,
        context: List<RetrievalResult>
    ): List<DeepSeekMessage> {
        // 构建参考信息文本
        val contextText = context.mapIndexed { index, result ->
            """
【参考${index + 1}】来源: ${result.documentTitle}
${result.content}
            """.trimIndent()
        }.joinToString("\n\n")

        // 查找系统消息
        val systemMessage = history.firstOrNull { it.role == "system" }
        val originalSystemContent = systemMessage?.content ?: "你是一个友好、乐于助人的AI助手。"

        // 构建增强的系统消息
        val enhancedSystemContent = """
$originalSystemContent

你现在可以访问知识库中的相关信息。以下是与用户问题相关的参考资料：

$contextText

使用指南：
1. 优先使用参考资料中的信息来回答问题
2. 如果参考资料不足以回答问题，可以结合你的知识进行补充
3. 回答时请自然流畅，不需要逐条引用
4. 如果参考信息与问题无关，可以忽略它们
        """.trim()

        val enhancedSystem = DeepSeekMessage(
            role = "system",
            content = enhancedSystemContent
        )

        // 重建对话历史：增强的系统消息 + 其他消息
        return listOf(enhancedSystem) + history.filter { it.role != "system" }
    }

    // ==================== 对话管理实现 ====================
    // 这些方法与远程实现相同,复用数据库操作

    override fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    override suspend fun createConversation(title: String): Long {
        val currentTime = System.currentTimeMillis()
        val conversation = ConversationEntity(
            title = title,
            createdAt = currentTime,
            lastMessageAt = currentTime
        )
        return conversationDao.insertConversation(conversation)
    }

    override suspend fun getOrCreateEmptyConversation(): Long {
        // 获取所有对话
        val conversationList = conversationDao.getAllConversations().first()

        // 查找没有消息的空对话
        for (conversation in conversationList) {
            val messageCount = messageDao.getMessageCount(conversation.id)
            if (messageCount == 0) {
                // 找到空对话，复用它
                return conversation.id
            }
        }

        // 没有找到空对话，创建新的
        return createConversation("新对话")
    }

    override suspend fun deleteConversation(conversationId: Long) {
        val conversation = conversationDao.getConversationById(conversationId)
        conversation?.let {
            conversationDao.deleteConversation(it)
        }
    }

    override suspend fun updateConversationTitle(conversationId: Long, title: String) {
        conversationDao.updateTitle(conversationId, title)
    }

    // ==================== 消息管理实现 ====================

    override fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByConversation(conversationId)
    }

    override suspend fun saveMessage(conversationId: Long, content: String, isFromUser: Boolean): Long {
        val currentTime = System.currentTimeMillis()
        val message = MessageEntity(
            conversationId = conversationId,
            content = content,
            isFromUser = isFromUser,
            timestamp = currentTime,
            isLiked = false
        )
        val messageId = messageDao.insertMessage(message)

        // 更新对话的最后消息时间
        conversationDao.updateLastMessageTime(conversationId, currentTime)

        return messageId
    }

    override suspend fun updateMessageContent(messageId: Long, content: String) {
        if (messageId > 0) {
            messageDao.updateContent(messageId, content)
        }
    }

    override suspend fun updateMessageLikeStatus(messageId: Long, isLiked: Boolean) {
        messageDao.updateLikeStatus(messageId, isLiked)
    }

    override suspend fun deleteMessage(messageId: Long) {
        messageDao.deleteMessage(messageId)
    }

    // ==================== 推荐问题生成实现 ====================

    override suspend fun getRecentUserMessages(limit: Int): List<MessageEntity> {
        return messageDao.getRecentUserMessages(limit)
    }

    /**
     * 本地模式不支持生成推荐问题，返回失败让调用方使用远程模型
     */
    override suspend fun generateSuggestions(historyMessages: List<String>): Result<List<String>> {
        return Result.failure(Exception("本地模式不支持生成推荐问题"))
    }

    // ==================== 消息重新生成实现 ====================

    override suspend fun getVersionInfo(messageId: Long): ChatRepository.VersionInfo? {
        val message = messageDao.getMessageById(messageId) ?: return null
        val groupId = message.regenerateGroupId ?: return null

        val versions = messageDao.getVersionsByGroup(groupId)
        if (versions.isEmpty()) return null

        val currentIndex = versions.indexOfFirst { it.isCurrentVersion }
        return ChatRepository.VersionInfo(
            currentIndex = if (currentIndex >= 0) currentIndex else 0,
            totalCount = versions.size,
            versions = versions
        )
    }

    override suspend fun switchVersion(groupId: Long, targetVersionIndex: Int): MessageEntity? {
        val versions = messageDao.getVersionsByGroup(groupId)
        if (versions.isEmpty() || targetVersionIndex < 0 || targetVersionIndex >= versions.size) {
            return null
        }

        val targetMessage = versions[targetVersionIndex]

        // 先清除组内所有当前版本标记
        messageDao.clearCurrentVersionInGroup(groupId)
        // 设置目标版本为当前版本
        messageDao.setAsCurrentVersion(targetMessage.id)

        return targetMessage.copy(isCurrentVersion = true)
    }

    override suspend fun saveRegeneratedMessage(
        conversationId: Long,
        content: String,
        groupId: Long
    ): Long {
        val currentTime = System.currentTimeMillis()

        // 获取当前最大版本号
        val maxVersion = messageDao.getMaxVersionIndex(groupId) ?: -1
        val newVersionIndex = maxVersion + 1

        // 先将组内所有消息设为非当前版本
        messageDao.clearCurrentVersionInGroup(groupId)

        // 创建新版本消息
        val message = MessageEntity(
            conversationId = conversationId,
            content = content,
            isFromUser = false,
            timestamp = currentTime,
            isLiked = false,
            regenerateGroupId = groupId,
            versionIndex = newVersionIndex,
            isCurrentVersion = true  // 新版本设为当前版本
        )

        val messageId = messageDao.insertMessage(message)

        // 更新对话的最后消息时间
        conversationDao.updateLastMessageTime(conversationId, currentTime)

        return messageId
    }

    override suspend fun initializeAsFirstVersion(messageId: Long, groupId: Long) {
        messageDao.initializeAsFirstVersion(messageId, groupId)
    }
}
