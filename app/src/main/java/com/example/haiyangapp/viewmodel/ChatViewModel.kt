package com.example.haiyangapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haiyangapp.api.ToolSource
import com.example.haiyangapp.api.models.DeepSeekMessage
import com.example.haiyangapp.database.entity.MessageEntity
import com.example.haiyangapp.repository.ChatRepository
import com.example.haiyangapp.repository.RepositoryFactory
import com.example.haiyangapp.inference.ModelLoadingState
import com.example.haiyangapp.ui.ChatMessage
import com.example.haiyangapp.ui.SourceReference
import com.example.haiyangapp.knowledge.KnowledgeRepository
import com.example.haiyangapp.knowledge.RetrievalResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 聊天界面的ViewModel
 * 管理聊天消息列表、加载状态和错误处理
 * 支持多对话和数据库持久化
 */
class ChatViewModel(
    private var repository: ChatRepository,
    private val context: Context
) : ViewModel() {

    // 当前对话ID
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    // 当前对话标题
    private val _conversationTitle = MutableStateFlow("新对话")
    val conversationTitle: StateFlow<String> = _conversationTitle.asStateFlow()

    // UI状态
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 模型加载状态（用于UI显示）
    private val _modelLoadingState = MutableStateFlow<ModelLoadingState>(ModelLoadingState.NotStarted)
    val modelLoadingState: StateFlow<ModelLoadingState> = _modelLoadingState.asStateFlow()

    // 当前推理模式
    private val _currentInferenceMode = MutableStateFlow(RepositoryFactory.getCurrentMode())
    val currentInferenceMode: StateFlow<RepositoryFactory.InferenceMode> = _currentInferenceMode.asStateFlow()

    // 点赞的消息ID集合
    private val _likedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedMessageIds: StateFlow<Set<Long>> = _likedMessageIds.asStateFlow()

    // 深度思考开关状态（默认关闭）
    private val _isDeepThinkingEnabled = MutableStateFlow(false)
    val isDeepThinkingEnabled: StateFlow<Boolean> = _isDeepThinkingEnabled.asStateFlow()

    // 推荐问题状态
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    // 推荐问题加载状态
    private val _isSuggestionsLoading = MutableStateFlow(false)
    val isSuggestionsLoading: StateFlow<Boolean> = _isSuggestionsLoading.asStateFlow()

    // RAG（知识库）开关状态
    private val _isRAGEnabled = MutableStateFlow(false)
    val isRAGEnabled: StateFlow<Boolean> = _isRAGEnabled.asStateFlow()

    // Function Calling（工具调用）开关状态（默认开启）
    private val _isFunctionCallingEnabled = MutableStateFlow(true)
    val isFunctionCallingEnabled: StateFlow<Boolean> = _isFunctionCallingEnabled.asStateFlow()

    // 工具调用状态提示
    private val _toolCallStatus = MutableStateFlow<String?>(null)
    val toolCallStatus: StateFlow<String?> = _toolCallStatus.asStateFlow()

    // 最近一次检索结果（用于UI显示引用来源）
    private val _lastRetrievalResults = MutableStateFlow<List<RetrievalResult>>(emptyList())
    val lastRetrievalResults: StateFlow<List<RetrievalResult>> = _lastRetrievalResults.asStateFlow()

    // 知识库Repository（懒加载）
    private val knowledgeRepository: KnowledgeRepository by lazy {
        KnowledgeViewModel.getKnowledgeRepository(context)
    }

    // SharedPreferences 用于缓存推荐问题
    private val prefs = context.getSharedPreferences("suggestions_cache", Context.MODE_PRIVATE)

    // 默认推荐问题（作为 fallback）
    private val defaultSuggestions = listOf(
        "今天天气怎么样？",
        "给我讲个笑话",
        "推荐一部电影",
        "如何提高工作效率？",
        "解释一下量子计算"
    )

    // API消息历史（用于维护上下文）
    private val apiMessageHistory = mutableListOf<DeepSeekMessage>()

    // 消息ID映射（UI ID -> 数据库ID）
    private val messageIdMap = mutableMapOf<Int, Long>()
    private var uiMessageIdCounter = 0

    init {
        // 添加系统提示词（可选）
        apiMessageHistory.add(
            DeepSeekMessage(
                role = "system",
                content = "你是一个友好、乐于助人的AI助手。请用简洁清晰的语言回答用户的问题。"
            )
        )

        // 监听模型加载状态
        viewModelScope.launch {
            RepositoryFactory.getModelLoadingState()?.collect { state ->
                _modelLoadingState.value = state
            }
        }

        // 加载缓存的推荐问题
        loadCachedSuggestions()
    }

    /**
     * 加载指定对话
     * @param conversationId 对话ID
     */
    fun loadConversation(conversationId: Long) {
        // 立即设置当前对话ID，避免时序问题
        _currentConversationId.value = conversationId

        viewModelScope.launch {
            // 清空当前状态
            apiMessageHistory.clear()
            apiMessageHistory.add(
                DeepSeekMessage(
                    role = "system",
                    content = "你是一个友好、乐于助人的AI助手。请用简洁清晰的语言回答用户的问题。"
                )
            )
            messageIdMap.clear()
            uiMessageIdCounter = 0
            _likedMessageIds.value = emptySet()

            // 从数据库加载消息（只获取一次，不持续监听）
            val dbMessages = repository.getMessagesByConversation(conversationId).first()

            // 转换为UI消息
            val uiMessages = dbMessages.map { dbMsg ->
                val uiId = uiMessageIdCounter++
                messageIdMap[uiId] = dbMsg.id

                // 重建API历史
                apiMessageHistory.add(
                    DeepSeekMessage(
                        role = if (dbMsg.isFromUser) "user" else "assistant",
                        content = dbMsg.content
                    )
                )

                // 更新点赞状态
                if (dbMsg.isLiked) {
                    _likedMessageIds.value = _likedMessageIds.value + dbMsg.id
                }

                ChatMessage(
                    id = uiId,
                    content = dbMsg.content,
                    isFromUser = dbMsg.isFromUser,
                    timestamp = formatTimestamp(dbMsg.timestamp)
                )
            }
            _messages.value = uiMessages

            // 更新对话标题
            if (uiMessages.isNotEmpty()) {
                val firstUserMessage = uiMessages.find { it.isFromUser }
                if (firstUserMessage != null) {
                    val title = firstUserMessage.content.take(20)
                    _conversationTitle.value = if (firstUserMessage.content.length > 20) "$title..." else title
                }
            }
        }
    }

    /**
     * 发送用户消息
     * @param content 用户输入的消息内容
     */
    fun sendMessage(content: String) {
        if (content.isBlank() || _isLoading.value) return

        // 检查模型加载状态
        when (val state = _modelLoadingState.value) {
            is ModelLoadingState.NotStarted, is ModelLoadingState.Loading -> {
                _errorMessage.value = "模型正在加载中，请稍候..."
                return
            }
            is ModelLoadingState.Error -> {
                _errorMessage.value = "模型加载失败: ${state.message}"
                return
            }
            is ModelLoadingState.Ready -> {
                // 模型已就绪，继续发送消息
            }
        }

        val conversationId = _currentConversationId.value
        android.util.Log.d("ChatViewModel", "sendMessage: conversationId = $conversationId, content = $content")

        if (conversationId == null) {
            _errorMessage.value = "当前没有活动对话"
            android.util.Log.e("ChatViewModel", "sendMessage: conversationId is null!")
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.d("ChatViewModel", "sendMessage: About to save message to database")
                // 保存用户消息到数据库
                val userMessageDbId = repository.saveMessage(conversationId, content, true)
                android.util.Log.d("ChatViewModel", "sendMessage: Message saved with ID $userMessageDbId")

                // 如果这是第一条消息，更新对话标题
                if (_messages.value.isEmpty()) {
                    val title = content.take(20)
                    val finalTitle = if (content.length > 20) "$title..." else title
                    repository.updateConversationTitle(conversationId, finalTitle)
                    _conversationTitle.value = finalTitle
                }

                // 添加用户消息到UI列表
                val userUiId = uiMessageIdCounter++
                messageIdMap[userUiId] = userMessageDbId

                val userMessage = ChatMessage(
                    id = userUiId,
                    content = content,
                    isFromUser = true,
                    timestamp = getCurrentTime()
                )
                _messages.value = _messages.value + userMessage

                // 添加到API消息历史
                apiMessageHistory.add(
                    DeepSeekMessage(
                        role = "user",
                        content = content
                    )
                )

                // 调用API获取AI回复
                fetchAiResponse(conversationId)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "sendMessage: Error - ${e.message}", e)
                _errorMessage.value = "发送失败: ${e.message}"
            }
        }
    }

    /**
     * 从API获取AI回复（流式）
     * 如果RAG开关打开，会先检索相关知识块并注入到上下文中
     */
    private fun fetchAiResponse(conversationId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // 创建一个临时的AI消息用于流式更新
            val aiUiId = uiMessageIdCounter++

            // 用于累积完整内容
            val fullContent = StringBuilder()

            // 标记是否已添加AI消息到列表
            var aiMessageAdded = false
            var aiMessageDbId: Long = -1

            // 首先在数据库中创建一个空的AI消息占位符
            aiMessageDbId = repository.saveMessage(conversationId, "", false)
            messageIdMap[aiUiId] = aiMessageDbId

            // RAG检索：如果开启了RAG，先检索相关知识
            var retrievalResults: List<RetrievalResult> = emptyList()
            var sources: List<SourceReference> = emptyList()

            if (_isRAGEnabled.value) {
                try {
                    // 获取最后一条用户消息作为查询
                    val lastUserMessage = apiMessageHistory.lastOrNull { it.role == "user" }?.content ?: ""
                    if (lastUserMessage.isNotBlank()) {
                        android.util.Log.d("ChatViewModel", "RAG: Searching for query: $lastUserMessage")
                        retrievalResults = knowledgeRepository.searchRelevantChunks(lastUserMessage, topK = 3)
                        _lastRetrievalResults.value = retrievalResults

                        // 转换为SourceReference用于UI显示
                        sources = retrievalResults.map { result ->
                            SourceReference(
                                documentId = result.documentId,
                                documentTitle = result.documentTitle,
                                sourcePath = result.sourcePath,
                                similarity = result.similarity
                            )
                        }

                        android.util.Log.d("ChatViewModel", "RAG: Found ${retrievalResults.size} relevant chunks")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "RAG retrieval failed", e)
                    // 检索失败不影响正常对话，继续使用普通模式
                }
            }

            // 检查是否启用Function Calling（仅远程模式支持）
            val useFunctionCalling = _isFunctionCallingEnabled.value &&
                    _currentInferenceMode.value == RepositoryFactory.InferenceMode.REMOTE

            // 根据配置选择不同的发送方式
            val result = when {
                // 有RAG上下文，使用sendMessageWithRAG
                retrievalResults.isNotEmpty() -> {
                    repository.sendMessageWithRAG(apiMessageHistory, retrievalResults) { contentChunk ->
                        fullContent.append(contentChunk)

                        if (!aiMessageAdded) {
                            val aiMessage = ChatMessage(
                                id = aiUiId,
                                content = fullContent.toString(),
                                isFromUser = false,
                                timestamp = getCurrentTime(),
                                sources = sources
                            )
                            _messages.value = _messages.value + aiMessage
                            aiMessageAdded = true
                        } else {
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == aiUiId) {
                                    msg.copy(content = fullContent.toString())
                                } else {
                                    msg
                                }
                            }
                        }
                    }
                }

                // 启用Function Calling，使用sendMessageWithTools
                useFunctionCalling -> {
                    android.util.Log.d("ChatViewModel", "Using Function Calling mode")
                    repository.sendMessageWithTools(
                        conversationHistory = apiMessageHistory,
                        enableFunctionCalling = true,
                        onContent = { contentChunk ->
                            fullContent.append(contentChunk)

                            if (!aiMessageAdded) {
                                val aiMessage = ChatMessage(
                                    id = aiUiId,
                                    content = fullContent.toString(),
                                    isFromUser = false,
                                    timestamp = getCurrentTime()
                                )
                                _messages.value = _messages.value + aiMessage
                                aiMessageAdded = true
                            } else {
                                _messages.value = _messages.value.map { msg ->
                                    if (msg.id == aiUiId) {
                                        msg.copy(content = fullContent.toString())
                                    } else {
                                        msg
                                    }
                                }
                            }
                        },
                        onToolCall = { status ->
                            _toolCallStatus.value = status
                            android.util.Log.d("ChatViewModel", "Tool call status: $status")
                        },
                        onToolSources = { toolSources ->
                            // 将工具来源转换为SourceReference并添加到sources
                            val webSources = toolSources.map { toolSource ->
                                SourceReference(
                                    documentId = 0L,  // 网页搜索没有documentId
                                    documentTitle = toolSource.title,
                                    sourcePath = toolSource.url,  // 使用URL作为来源路径
                                    similarity = 0f
                                )
                            }
                            sources = sources + webSources
                            android.util.Log.d("ChatViewModel", "Received ${webSources.size} web search sources")
                        }
                    )
                }

                // 普通模式，使用sendMessageStream
                else -> {
                    repository.sendMessageStream(apiMessageHistory) { contentChunk ->
                        fullContent.append(contentChunk)

                        if (!aiMessageAdded) {
                            val aiMessage = ChatMessage(
                                id = aiUiId,
                                content = fullContent.toString(),
                                isFromUser = false,
                                timestamp = getCurrentTime()
                            )
                            _messages.value = _messages.value + aiMessage
                            aiMessageAdded = true
                        } else {
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == aiUiId) {
                                    msg.copy(content = fullContent.toString())
                                } else {
                                    msg
                                }
                            }
                        }
                    }
                }
            }

            // 清除工具调用状态
            _toolCallStatus.value = null

            result.fold(
                onSuccess = { finalContent ->
                    // 成功：添加到API消息历史（维护上下文）
                    apiMessageHistory.add(
                        DeepSeekMessage(
                            role = "assistant",
                            content = finalContent
                        )
                    )

                    // 最终更新数据库中的完整内容
                    viewModelScope.launch {
                        if (aiMessageDbId != -1L) {
                            repository.updateMessageContent(aiMessageDbId, finalContent)
                        }
                    }

                    // 确保最终消息包含sources
                    if (sources.isNotEmpty()) {
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == aiUiId) {
                                msg.copy(content = finalContent, sources = sources)
                            } else {
                                msg
                            }
                        }
                    }
                },
                onFailure = { exception ->
                    // 失败：显示错误信息并清理
                    if (aiMessageAdded) {
                        _messages.value = _messages.value.filter { it.id != aiUiId }
                    }
                    if (aiMessageDbId != -1L) {
                        viewModelScope.launch {
                            repository.deleteMessage(aiMessageDbId)
                        }
                    }
                    _errorMessage.value = "发送失败: ${exception.message}"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 获取当前时间字符串
     */
    private fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date())
    }

    /**
     * 格式化时间戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    /**
     * 获取指定消息的内容（用于编辑）
     */
    fun getMessageContent(messageId: Int): String? {
        return _messages.value.find { it.id == messageId }?.content
    }

    /**
     * 编辑消息并重新发送
     * @param messageId 要编辑的消息ID（UI ID）
     * @param newContent 新的消息内容
     */
    fun editMessage(messageId: Int, newContent: String) {
        if (newContent.isBlank() || _isLoading.value) return

        val conversationId = _currentConversationId.value ?: return

        viewModelScope.launch {
            // 找到要编辑的消息在列表中的索引
            val messageIndex = _messages.value.indexOfFirst { it.id == messageId }
            if (messageIndex == -1) return@launch

            // 删除该消息及其后续所有消息（UI层和数据库）
            val messagesToDelete = _messages.value.drop(messageIndex)
            for (msg in messagesToDelete) {
                val dbId = messageIdMap[msg.id]
                if (dbId != null) {
                    repository.deleteMessage(dbId)
                    messageIdMap.remove(msg.id)
                }
            }

            _messages.value = _messages.value.take(messageIndex)

            // 同步删除 API 消息历史
            val apiHistoryIndex = messageIndex + 1  // +1 是因为第一条是system消息
            if (apiHistoryIndex < apiMessageHistory.size) {
                while (apiMessageHistory.size > apiHistoryIndex) {
                    apiMessageHistory.removeAt(apiHistoryIndex)
                }
            }

            // 发送新的消息内容
            sendMessage(newContent)
        }
    }

    /**
     * 切换消息的点赞状态
     * @param uiMessageId 消息的UI ID
     */
    fun toggleLike(uiMessageId: Int) {
        val dbId = messageIdMap[uiMessageId] ?: return

        viewModelScope.launch {
            val currentLikes = _likedMessageIds.value.toMutableSet()
            val isLiked = if (currentLikes.contains(dbId)) {
                currentLikes.remove(dbId)
                false
            } else {
                currentLikes.add(dbId)
                true
            }
            _likedMessageIds.value = currentLikes

            // 更新数据库
            repository.updateMessageLikeStatus(dbId, isLiked)
        }
    }

    /**
     * 检查消息是否被点赞
     */
    fun isMessageLiked(uiMessageId: Int): Boolean {
        val dbId = messageIdMap[uiMessageId] ?: return false
        return _likedMessageIds.value.contains(dbId)
    }

    /**
     * 设置推理模式（本地或远程）
     * @param mode 推理模式
     */
    fun setInferenceMode(mode: RepositoryFactory.InferenceMode) {
        if (_currentInferenceMode.value == mode) return

        _currentInferenceMode.value = mode
        RepositoryFactory.setInferenceMode(mode)

        // 更新 repository 为当前模式对应的 repository
        repository = RepositoryFactory.getChatRepository(context, mode)

        // 更新模型加载状态显示
        when (mode) {
            RepositoryFactory.InferenceMode.LOCAL -> {
                // 本地模式，监听模型加载状态
                viewModelScope.launch {
                    RepositoryFactory.getModelLoadingState()?.collect { state ->
                        _modelLoadingState.value = state
                    }
                }
            }
            RepositoryFactory.InferenceMode.REMOTE -> {
                // 远程模式，直接设置为已就绪
                _modelLoadingState.value = ModelLoadingState.Ready
            }
        }

        android.util.Log.d("ChatViewModel", "Inference mode changed to: $mode")
    }

    /**
     * 切换深度思考开关
     */
    fun toggleDeepThinking() {
        _isDeepThinkingEnabled.value = !_isDeepThinkingEnabled.value
        android.util.Log.d("ChatViewModel", "Deep thinking enabled: ${_isDeepThinkingEnabled.value}")
    }

    /**
     * 切换RAG（知识库）开关
     */
    fun toggleRAG() {
        _isRAGEnabled.value = !_isRAGEnabled.value
        android.util.Log.d("ChatViewModel", "RAG enabled: ${_isRAGEnabled.value}")
    }

    /**
     * 设置RAG开关状态
     */
    fun setRAGEnabled(enabled: Boolean) {
        _isRAGEnabled.value = enabled
        android.util.Log.d("ChatViewModel", "RAG set to: $enabled")
    }

    /**
     * 切换Function Calling（工具调用）开关
     */
    fun toggleFunctionCalling() {
        _isFunctionCallingEnabled.value = !_isFunctionCallingEnabled.value
        android.util.Log.d("ChatViewModel", "Function Calling enabled: ${_isFunctionCallingEnabled.value}")
    }

    /**
     * 设置Function Calling开关状态
     */
    fun setFunctionCallingEnabled(enabled: Boolean) {
        _isFunctionCallingEnabled.value = enabled
        android.util.Log.d("ChatViewModel", "Function Calling set to: $enabled")
    }

    /**
     * 检查是否有可用的知识库文档
     */
    suspend fun hasKnowledgeDocuments(): Boolean {
        return try {
            knowledgeRepository.hasIndexedDocuments()
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to check knowledge documents", e)
            false
        }
    }

    // ==================== 推荐问题相关 ====================

    /**
     * 加载缓存的推荐问题
     * 如果没有缓存则使用默认问题
     */
    private fun loadCachedSuggestions() {
        val cached = prefs.getStringSet("cached_suggestions", null)
        _suggestions.value = cached?.toList()?.take(5) ?: defaultSuggestions
        android.util.Log.d("ChatViewModel", "Loaded suggestions: ${_suggestions.value}")
    }

    /**
     * 基于用户历史生成推荐问题并缓存（suspend函数，调用方需等待完成）
     * 使用远程模型生成，生成完成后更新UI并缓存供下次使用
     * 在新建会话时调用，阻塞直到生成完成
     */
    suspend fun generateAndCacheSuggestions() {
        _isSuggestionsLoading.value = true

        try {
            // 使用远程 Repository 生成推荐问题
            val remoteRepository = RepositoryFactory.getChatRepository(
                context,
                RepositoryFactory.InferenceMode.REMOTE
            )

            // 获取最近的用户消息
            val recentMessages = remoteRepository.getRecentUserMessages(10)

            if (recentMessages.isEmpty()) {
                android.util.Log.d("ChatViewModel", "No history, using default suggestions")
                _suggestions.value = defaultSuggestions
                return
            }

            val historyContents = recentMessages.map { it.content }
            android.util.Log.d("ChatViewModel", "Generating suggestions from ${historyContents.size} messages")

            // 调用远程模型生成推荐问题
            val result = remoteRepository.generateSuggestions(historyContents)

            result.fold(
                onSuccess = { suggestions ->
                    // 更新UI
                    _suggestions.value = suggestions

                    // 缓存到 SharedPreferences
                    prefs.edit()
                        .putStringSet("cached_suggestions", suggestions.toSet())
                        .apply()

                    android.util.Log.d("ChatViewModel", "Generated and cached suggestions: $suggestions")
                },
                onFailure = { error ->
                    android.util.Log.e("ChatViewModel", "Failed to generate suggestions: ${error.message}")
                    // 失败时使用默认推荐
                    _suggestions.value = defaultSuggestions
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error generating suggestions", e)
            // 异常时使用默认推荐
            _suggestions.value = defaultSuggestions
        } finally {
            _isSuggestionsLoading.value = false
        }
    }

    /**
     * 刷新推荐问题（重新从缓存加载）
     */
    fun refreshSuggestions() {
        loadCachedSuggestions()
    }

    // ==================== 消息重新生成 ====================

    /**
     * 重新生成AI回复
     * @param aiMessageUiId 要重新生成的AI消息的UI ID
     */
    fun regenerateMessage(aiMessageUiId: Int) {
        if (_isLoading.value) return

        val conversationId = _currentConversationId.value ?: return
        val aiMessageDbId = messageIdMap[aiMessageUiId] ?: return

        // 找到该AI消息在列表中的位置
        val aiMessageIndex = _messages.value.indexOfFirst { it.id == aiMessageUiId }
        if (aiMessageIndex == -1 || aiMessageIndex == 0) return

        // AI消息前面的应该是对应的用户消息
        val userMessage = _messages.value.getOrNull(aiMessageIndex - 1)
        if (userMessage == null || !userMessage.isFromUser) return

        val userMessageDbId = messageIdMap[userMessage.id] ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 使用用户消息的数据库ID作为 groupId
                val groupId = userMessageDbId

                // 获取当前AI消息，检查是否已有版本组
                val currentAiMessage = _messages.value[aiMessageIndex]
                val existingGroupId = currentAiMessage.regenerateGroupId

                // 如果是第一次重新生成，需要先将原始消息加入版本组
                if (existingGroupId == null) {
                    // 将原始AI消息初始化为版本组的第一个版本（versionIndex=0）
                    repository.initializeAsFirstVersion(aiMessageDbId, groupId)
                }

                // 构建上下文：使用到该用户消息为止的历史
                val contextHistory = apiMessageHistory.take(
                    apiMessageHistory.indexOfFirst { it.content == userMessage.content && it.role == "user" } + 1
                ).toMutableList()

                // 确保包含系统消息
                if (contextHistory.isEmpty() || contextHistory[0].role != "system") {
                    contextHistory.add(0, DeepSeekMessage(
                        role = "system",
                        content = "你是一个友好、乐于助人的AI助手。请用简洁清晰的语言回答用户的问题。"
                    ))
                }

                // 创建新的AI消息UI占位
                val newAiUiId = uiMessageIdCounter++
                val fullContent = StringBuilder()
                var newAiMessageAdded = false

                // 调用API获取新的回复
                val result = repository.sendMessageStream(contextHistory) { contentChunk ->
                    fullContent.append(contentChunk)

                    if (!newAiMessageAdded) {
                        // 替换原来的AI消息为新版本
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == aiMessageUiId) {
                                msg.copy(
                                    content = fullContent.toString(),
                                    regenerateGroupId = groupId,
                                    versionIndex = (msg.totalVersions),
                                    totalVersions = msg.totalVersions + 1
                                )
                            } else {
                                msg
                            }
                        }
                        newAiMessageAdded = true
                    } else {
                        // 更新内容
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == aiMessageUiId) {
                                msg.copy(content = fullContent.toString())
                            } else {
                                msg
                            }
                        }
                    }
                }

                result.fold(
                    onSuccess = { finalContent ->
                        // 保存新版本到数据库
                        val newMessageDbId = repository.saveRegeneratedMessage(
                            conversationId = conversationId,
                            content = finalContent,
                            groupId = groupId
                        )

                        // 更新映射
                        messageIdMap[aiMessageUiId] = newMessageDbId

                        // 更新UI中的版本信息
                        val versionInfo = repository.getVersionInfo(newMessageDbId)
                        if (versionInfo != null) {
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == aiMessageUiId) {
                                    msg.copy(
                                        regenerateGroupId = groupId,
                                        versionIndex = versionInfo.currentIndex,
                                        totalVersions = versionInfo.totalCount
                                    )
                                } else {
                                    msg
                                }
                            }
                        }

                        // 更新API历史中的最后一条assistant消息
                        val lastAssistantIndex = apiMessageHistory.indexOfLast { it.role == "assistant" }
                        if (lastAssistantIndex >= 0) {
                            apiMessageHistory[lastAssistantIndex] = DeepSeekMessage(
                                role = "assistant",
                                content = finalContent
                            )
                        }

                        android.util.Log.d("ChatViewModel", "Message regenerated successfully")
                    },
                    onFailure = { exception ->
                        _errorMessage.value = "重新生成失败: ${exception.message}"
                        // 恢复原消息内容
                        loadConversation(conversationId)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "重新生成失败: ${e.message}"
                android.util.Log.e("ChatViewModel", "regenerateMessage error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 切换到上一个版本
     * @param aiMessageUiId AI消息的UI ID
     */
    fun switchToPreviousVersion(aiMessageUiId: Int) {
        val currentMessage = _messages.value.find { it.id == aiMessageUiId } ?: return
        if (currentMessage.versionIndex > 0) {
            switchToVersion(aiMessageUiId, currentMessage.versionIndex - 1)
        }
    }

    /**
     * 切换到下一个版本
     * @param aiMessageUiId AI消息的UI ID
     */
    fun switchToNextVersion(aiMessageUiId: Int) {
        val currentMessage = _messages.value.find { it.id == aiMessageUiId } ?: return
        if (currentMessage.versionIndex < currentMessage.totalVersions - 1) {
            switchToVersion(aiMessageUiId, currentMessage.versionIndex + 1)
        }
    }

    /**
     * 切换到指定版本
     */
    private fun switchToVersion(aiMessageUiId: Int, targetVersionIndex: Int) {
        val currentMessage = _messages.value.find { it.id == aiMessageUiId } ?: return
        val groupId = currentMessage.regenerateGroupId ?: return

        viewModelScope.launch {
            val newMessage = repository.switchVersion(groupId, targetVersionIndex)
            if (newMessage != null) {
                // 更新UI
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == aiMessageUiId) {
                        msg.copy(
                            content = newMessage.content,
                            versionIndex = targetVersionIndex
                        )
                    } else {
                        msg
                    }
                }

                // 更新映射
                messageIdMap[aiMessageUiId] = newMessage.id

                // 更新API历史
                val lastAssistantIndex = apiMessageHistory.indexOfLast { it.role == "assistant" }
                if (lastAssistantIndex >= 0) {
                    apiMessageHistory[lastAssistantIndex] = DeepSeekMessage(
                        role = "assistant",
                        content = newMessage.content
                    )
                }
            }
        }
    }

    /**
     * 获取消息的版本信息
     */
    fun getMessageVersionInfo(aiMessageUiId: Int): Triple<Int, Int, Long?>? {
        val message = _messages.value.find { it.id == aiMessageUiId } ?: return null
        return Triple(message.versionIndex, message.totalVersions, message.regenerateGroupId)
    }

}
