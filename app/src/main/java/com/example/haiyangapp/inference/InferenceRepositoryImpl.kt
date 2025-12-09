package com.example.haiyangapp.inference

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地推理数据仓库实现
 * 使用LlamaCppInference进行实际推理
 */
class InferenceRepositoryImpl(
    context: Context,
    private val config: ModelConfig = ModelConfig()
) : InferenceRepository {

    companion object {
        private const val TAG = "InferenceRepository"
    }

    private val llamaCppInference = LlamaCppInference(context, config)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _loadingState = MutableStateFlow<ModelLoadingState>(ModelLoadingState.NotStarted)
    override val loadingState: StateFlow<ModelLoadingState> = _loadingState.asStateFlow()

    override suspend fun initialize(): Result<Unit> {
        Log.d(TAG, "Initializing inference repository...")
        _loadingState.value = ModelLoadingState.Loading
        val result = llamaCppInference.initialize()
        _loadingState.value = if (result.isSuccess) {
            ModelLoadingState.Ready
        } else {
            ModelLoadingState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }
        return result
    }

    override fun startBackgroundInitialization() {
        if (_loadingState.value == ModelLoadingState.Loading ||
            _loadingState.value == ModelLoadingState.Ready) {
            Log.d(TAG, "Model already loading or loaded, skipping...")
            return
        }

        Log.d(TAG, "Starting background model initialization...")
        scope.launch {
            initialize()
        }
    }

    override suspend fun sendMessageStream(
        conversationHistory: List<Pair<String, String>>,
        onToken: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!llamaCppInference.isLoaded()) {
                return@withContext Result.failure(
                    Exception("Inference engine not initialized")
                )
            }

            // 构建提示词（禁用思考模式）
            val prompt = llamaCppInference.buildPrompt(conversationHistory, enableThinking = false)
            Log.d(TAG, "Generated prompt (first 100 chars): ${prompt.take(100)}")

            // 累积完整响应
            val fullResponse = StringBuilder()

            // 收集流式输出
            llamaCppInference.inferStream(prompt)
                .onEach { token ->
                    fullResponse.append(token)
                    onToken(token)
                }
                .catch { e ->
                    Log.e(TAG, "Stream error", e)
                    throw e
                }
                .collect()

            val finalResponse = fullResponse.toString().trim()
            Log.d(TAG, "Inference completed, response length: ${finalResponse.length}")

            Result.success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message (stream)", e)
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(
        conversationHistory: List<Pair<String, String>>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!llamaCppInference.isLoaded()) {
                return@withContext Result.failure(
                    Exception("Inference engine not initialized")
                )
            }

            // 构建提示词（禁用思考模式）
            val prompt = llamaCppInference.buildPrompt(conversationHistory, enableThinking = false)
            Log.d(TAG, "Generated prompt (first 100 chars): ${prompt.take(100)}")

            // 执行推理
            llamaCppInference.infer(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            Result.failure(e)
        }
    }

    override fun isReady(): Boolean {
        return llamaCppInference.isLoaded()
    }

    override fun release() {
        llamaCppInference.release()
    }
}
