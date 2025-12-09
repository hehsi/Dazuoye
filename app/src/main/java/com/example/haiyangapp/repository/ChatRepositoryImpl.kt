package com.example.haiyangapp.repository

import android.util.Log
import com.example.haiyangapp.api.DeepSeekApiService
import com.example.haiyangapp.api.ToolExecutor
import com.example.haiyangapp.api.ToolSource
import com.example.haiyangapp.api.models.AvailableTools
import com.example.haiyangapp.api.models.DeepSeekMessage
import com.example.haiyangapp.api.models.DeepSeekRequest
import com.example.haiyangapp.api.models.FunctionCall
import com.example.haiyangapp.api.models.ToolCall
import com.example.haiyangapp.database.dao.ConversationDao
import com.example.haiyangapp.database.dao.MessageDao
import com.example.haiyangapp.database.entity.ConversationEntity
import com.example.haiyangapp.database.entity.MessageEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import kotlinx.coroutines.delay

/**
 * 聊天数据仓库实现
 * 负责调用DeepSeek API并处理流式响应，同时管理本地数据库
 */
class ChatRepositoryImpl(
    private val apiService: DeepSeekApiService,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepositoryImpl"
    }

    // 使用lenient模式的Gson，可以容忍不规范的JSON
    private val gson = GsonBuilder()
        .setLenient()
        .create()

    // 工具执行器
    private val toolExecutor = ToolExecutor()

    /**
     * 发送消息到DeepSeek API并支持流式回调
     * @param conversationHistory 完整对话历史，包含system、user和assistant消息
     * @param onContent 接收每个流式内容片段的回调函数
     * @return AI的回复内容或错误信息
     */
    override suspend fun sendMessageStream(
        conversationHistory: List<DeepSeekMessage>,
        onContent: (String) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建API请求 - 使用流式响应
                val request = DeepSeekRequest(
                    model = "deepseek-chat",
                    messages = conversationHistory,
                    stream = true, // 使用流式响应
                    temperature = 0.7
                )

                // 调用API获取流式响应
                val responseBody = apiService.chatCompletions(request)

                // 解析SSE流式响应
                val content = StringBuilder()
                responseBody.byteStream().bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.startsWith("data: ") && line != "data: [DONE]") {
                            val jsonStr = line.removePrefix("data: ")
                            try {
                                val jsonObject = gson.fromJson(jsonStr, JsonObject::class.java)
                                val choices = jsonObject.getAsJsonArray("choices")
                                if (choices != null && choices.size() > 0) {
                                    val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                    val contentPart = delta?.get("content")?.asString
                                    if (contentPart != null) {
                                        content.append(contentPart)
                                        // 切换到主线程回调
                                        withContext(Dispatchers.Main) {
                                            onContent(contentPart)
                                        }
                                        // 在IO线程添加延迟，模拟打字速度
                                        delay(30)
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误，继续处理下一行
                            }
                        }
                        line = reader.readLine()
                    }
                }

                val finalContent = content.toString()
                if (finalContent.isEmpty()) {
                    Result.failure(Exception("API返回空响应"))
                } else {
                    Result.success(finalContent)
                }

            } catch (e: Exception) {
                // 捕获所有异常（网络错误、解析错误等）
                Result.failure(e)
            }
        }
    }

    // ==================== 对话管理实现 ====================

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

    override suspend fun generateSuggestions(historyMessages: List<String>): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val historyText = historyMessages.joinToString("\n") { "- $it" }
                val prompt = """基于用户的历史提问，生成5个用户可能感兴趣的新问题。
要求：
1. 问题要与用户历史兴趣相关
2. 问题要简短（不超过20个字）
3. 每行一个问题，不要编号
4. 直接输出问题，不要其他内容

用户历史提问：
$historyText

生成的推荐问题："""

                val messages = listOf(
                    DeepSeekMessage(role = "user", content = prompt)
                )

                val request = DeepSeekRequest(
                    model = "deepseek-chat",
                    messages = messages,
                    stream = false,
                    temperature = 0.8
                )

                val responseBody = apiService.chatCompletions(request)
                val responseText = responseBody.string()
                val jsonObject = gson.fromJson(responseText, JsonObject::class.java)
                val choices = jsonObject.getAsJsonArray("choices")

                if (choices != null && choices.size() > 0) {
                    val content = choices[0].asJsonObject
                        .getAsJsonObject("message")
                        ?.get("content")?.asString ?: ""

                    val suggestions = content.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !it.startsWith("-") }
                        .take(5)

                    if (suggestions.isNotEmpty()) {
                        Result.success(suggestions)
                    } else {
                        Result.failure(Exception("未能生成推荐问题"))
                    }
                } else {
                    Result.failure(Exception("API返回空响应"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
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

    // ==================== Function Calling 实现 ====================

    override suspend fun sendMessageWithTools(
        conversationHistory: List<DeepSeekMessage>,
        enableFunctionCalling: Boolean,
        onContent: (String) -> Unit,
        onToolCall: ((String) -> Unit)?,
        onToolSources: ((List<ToolSource>) -> Unit)?
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 如果不启用Function Calling，直接使用普通流式发送
                if (!enableFunctionCalling) {
                    return@withContext sendMessageStream(conversationHistory, onContent)
                }

                Log.d(TAG, "Sending message with tools enabled")

                // 构建带工具的API请求（非流式，因为需要处理tool_calls）
                val request = DeepSeekRequest(
                    model = "deepseek-chat",
                    messages = conversationHistory,
                    stream = false, // 使用非流式以便处理tool_calls
                    temperature = 0.7,
                    tools = AvailableTools.getAllTools(),
                    toolChoice = "auto"
                )

                // 调用API
                val responseBody = apiService.chatCompletions(request)
                val responseText = responseBody.string()
                Log.d(TAG, "API Response: $responseText")

                val jsonObject = gson.fromJson(responseText, JsonObject::class.java)
                val choices = jsonObject.getAsJsonArray("choices")

                if (choices == null || choices.size() == 0) {
                    return@withContext Result.failure(Exception("API返回空响应"))
                }

                val choice = choices[0].asJsonObject
                val message = choice.getAsJsonObject("message")
                val finishReason = choice.get("finish_reason")?.asString

                // 检查是否有工具调用
                val toolCallsArray = message?.getAsJsonArray("tool_calls")

                if (finishReason == "tool_calls" || (toolCallsArray != null && toolCallsArray.size() > 0)) {
                    // AI请求调用工具
                    Log.d(TAG, "AI requested tool calls: ${toolCallsArray?.size()} tools")

                    // 解析工具调用
                    val toolCalls = parseToolCalls(toolCallsArray)

                    // 通知UI正在调用工具
                    toolCalls.forEach { toolCall ->
                        withContext(Dispatchers.Main) {
                            onToolCall?.invoke("正在查询: ${toolCall.function.name}...")
                        }
                    }

                    // 执行所有工具调用，并收集来源信息
                    val toolResults = mutableListOf<DeepSeekMessage>()
                    val allSources = mutableListOf<ToolSource>()
                    for (toolCall in toolCalls) {
                        val result = toolExecutor.execute(toolCall)
                        Log.d(TAG, "Tool ${toolCall.function.name} result: ${result.result}")
                        toolResults.add(DeepSeekMessage.toolResult(result.toolCallId, result.result))

                        // 收集来源信息
                        if (result.sources.isNotEmpty()) {
                            allSources.addAll(result.sources)
                            Log.d(TAG, "Collected ${result.sources.size} sources from ${toolCall.function.name}")
                        }
                    }

                    // 通过回调传递来源信息
                    if (allSources.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            onToolSources?.invoke(allSources)
                        }
                    }

                    // 构建新的对话历史，包含工具调用和结果
                    val newHistory = conversationHistory.toMutableList()
                    // 添加AI的工具调用消息
                    newHistory.add(DeepSeekMessage.assistantWithToolCalls(toolCalls))
                    // 添加工具结果
                    newHistory.addAll(toolResults)

                    // 再次调用API获取最终回复（流式）
                    Log.d(TAG, "Calling API again with tool results for final response")
                    return@withContext sendFinalResponseWithToolResults(newHistory, onContent)

                } else {
                    // 没有工具调用，直接返回内容
                    val content = message?.get("content")?.asString ?: ""
                    Log.d(TAG, "No tool calls, direct response: ${content.take(100)}...")

                    // 模拟流式输出
                    for (char in content) {
                        withContext(Dispatchers.Main) {
                            onContent(char.toString())
                        }
                        delay(20)
                    }

                    if (content.isEmpty()) {
                        Result.failure(Exception("API返回空响应"))
                    } else {
                        Result.success(content)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in sendMessageWithTools", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 解析工具调用
     */
    private fun parseToolCalls(toolCallsArray: JsonArray?): List<ToolCall> {
        if (toolCallsArray == null) return emptyList()

        return toolCallsArray.mapNotNull { element ->
            try {
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                val type = obj.get("type")?.asString ?: "function"
                val functionObj = obj.getAsJsonObject("function") ?: return@mapNotNull null
                val name = functionObj.get("name")?.asString ?: return@mapNotNull null
                val arguments = functionObj.get("arguments")?.asString ?: "{}"

                ToolCall(
                    id = id,
                    type = type,
                    function = FunctionCall(name = name, arguments = arguments)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing tool call", e)
                null
            }
        }
    }

    /**
     * 使用工具结果获取最终回复（流式）
     */
    private suspend fun sendFinalResponseWithToolResults(
        conversationHistory: List<DeepSeekMessage>,
        onContent: (String) -> Unit
    ): Result<String> {
        return try {
            // 构建请求（流式，不带工具）
            val request = DeepSeekRequest(
                model = "deepseek-chat",
                messages = conversationHistory,
                stream = true,
                temperature = 0.7,
                tools = null, // 不再需要工具
                toolChoice = null
            )

            val responseBody = apiService.chatCompletions(request)
            val content = StringBuilder()

            responseBody.byteStream().bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (line.startsWith("data: ") && line != "data: [DONE]") {
                        val jsonStr = line.removePrefix("data: ")
                        try {
                            val jsonObject = gson.fromJson(jsonStr, JsonObject::class.java)
                            val choices = jsonObject.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                val contentPart = delta?.get("content")?.asString
                                if (contentPart != null) {
                                    content.append(contentPart)
                                    withContext(Dispatchers.Main) {
                                        onContent(contentPart)
                                    }
                                    delay(30)
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误
                        }
                    }
                    line = reader.readLine()
                }
            }

            val finalContent = content.toString()
            if (finalContent.isEmpty()) {
                Result.failure(Exception("API返回空响应"))
            } else {
                Result.success(finalContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendFinalResponseWithToolResults", e)
            Result.failure(e)
        }
    }
}