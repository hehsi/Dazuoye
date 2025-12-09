package com.example.haiyangapp.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 模型加载状态
 */
sealed class ModelLoadingState {
    object NotStarted : ModelLoadingState()
    object Loading : ModelLoadingState()
    object Ready : ModelLoadingState()
    data class Error(val message: String) : ModelLoadingState()
}

/**
 * 本地推理数据仓库接口
 * 提供统一的推理接口,隔离具体实现
 */
interface InferenceRepository {

    /**
     * 模型加载状态流，用于UI观察
     */
    val loadingState: StateFlow<ModelLoadingState>

    /**
     * 初始化推理引擎
     * 应在应用启动时调用
     */
    suspend fun initialize(): Result<Unit>

    /**
     * 在后台启动初始化（非阻塞）
     * 可通过 loadingState 监听加载进度
     */
    fun startBackgroundInitialization()

    /**
     * 发送消息并获取AI回复（流式）
     * @param conversationHistory 对话历史 (role, content)
     * @param onToken 接收每个token的回调
     * @return Result封装的完整回复
     */
    suspend fun sendMessageStream(
        conversationHistory: List<Pair<String, String>>,
        onToken: (String) -> Unit
    ): Result<String>

    /**
     * 发送消息并获取AI回复（非流式）
     * @param conversationHistory 对话历史 (role, content)
     * @return Result封装的完整回复
     */
    suspend fun sendMessage(
        conversationHistory: List<Pair<String, String>>
    ): Result<String>

    /**
     * 检查推理引擎是否已就绪
     */
    fun isReady(): Boolean

    /**
     * 释放资源
     */
    fun release()
}
