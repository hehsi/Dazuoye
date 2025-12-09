package com.example.haiyangapp.repository

import android.content.Context
import com.example.haiyangapp.api.RetrofitClient
import com.example.haiyangapp.database.AppDatabase
import com.example.haiyangapp.inference.InferenceRepositoryImpl
import com.example.haiyangapp.inference.ModelConfig
import com.example.haiyangapp.inference.ModelLoadingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository工厂类
 * 负责创建和管理ChatRepository实例
 * 支持在本地推理和远程API之间切换
 */
object RepositoryFactory {

    /**
     * 推理模式
     */
    enum class InferenceMode {
        LOCAL,  // 本地推理
        REMOTE  // 远程API
    }

    private var currentMode: InferenceMode = InferenceMode.LOCAL
    private var localChatRepository: ChatRepository? = null
    private var remoteChatRepository: ChatRepository? = null
    private var inferenceRepository: InferenceRepositoryImpl? = null

    /**
     * 获取ChatRepository实例
     * @param context Android上下文
     * @param mode 推理模式（默认使用本地推理）
     * @return ChatRepository实例
     */
    fun getChatRepository(
        context: Context,
        mode: InferenceMode = currentMode
    ): ChatRepository {
        val database = AppDatabase.getDatabase(context)
        val conversationDao = database.conversationDao()
        val messageDao = database.messageDao()

        return when (mode) {
            InferenceMode.LOCAL -> {
                if (localChatRepository == null) {
                    // 创建推理引擎（如果还没有）
                    if (inferenceRepository == null) {
                        inferenceRepository = InferenceRepositoryImpl(
                            context = context.applicationContext,
                            config = ModelConfig()
                        )
                    }

                    localChatRepository = LocalChatRepositoryImpl(
                        inferenceRepository = inferenceRepository!!,
                        conversationDao = conversationDao,
                        messageDao = messageDao
                    )
                }
                localChatRepository!!
            }

            InferenceMode.REMOTE -> {
                if (remoteChatRepository == null) {
                    val apiService = RetrofitClient.apiService
                    remoteChatRepository = ChatRepositoryImpl(
                        apiService = apiService,
                        conversationDao = conversationDao,
                        messageDao = messageDao
                    )
                }
                remoteChatRepository!!
            }
        }
    }

    /**
     * 获取推理Repository（用于初始化等操作）
     */
    fun getInferenceRepository(context: Context): InferenceRepositoryImpl {
        if (inferenceRepository == null) {
            inferenceRepository = InferenceRepositoryImpl(
                context = context.applicationContext,
                config = ModelConfig()
            )
        }
        return inferenceRepository!!
    }

    /**
     * 获取模型加载状态流
     * @return StateFlow<ModelLoadingState>? 如果使用本地模式则返回状态流，否则返回null
     */
    fun getModelLoadingState(): StateFlow<ModelLoadingState>? {
        return inferenceRepository?.loadingState
    }

    /**
     * 启动后台模型加载
     * 用于APP启动时立即开始加载模型，但不阻塞UI
     */
    fun startBackgroundModelLoading(context: Context) {
        val repo = getInferenceRepository(context)
        repo.startBackgroundInitialization()
    }

    /**
     * 设置推理模式
     */
    fun setInferenceMode(mode: InferenceMode) {
        currentMode = mode
    }

    /**
     * 获取当前推理模式
     */
    fun getCurrentMode(): InferenceMode = currentMode

    /**
     * 释放资源
     */
    fun release() {
        inferenceRepository?.release()
        inferenceRepository = null
        localChatRepository = null
        remoteChatRepository = null
    }
}
