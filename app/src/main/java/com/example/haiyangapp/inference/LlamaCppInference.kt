package com.example.haiyangapp.inference

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * LLaMA.cpp推理引擎封装
 * 使用 JNI 调用原生 llama.cpp 库
 */
class LlamaCppInference(
    private val context: Context,
    private val config: ModelConfig = ModelConfig()
) {
    companion object {
        private const val TAG = "LlamaCppInference"
    }

    private var modelHandle: Long = 0
    private var isModelLoaded = false
    private var isUsingGpu = false
    private var gpuLayers = 0

    /**
     * 初始化模型（自动检测并启用 GPU，不支持时静默回退到 CPU）
     *
     * 创新点：
     * - 自动检测设备 GPU 能力
     * - 尝试使用 Vulkan GPU 加速
     * - 失败时静默回退到 CPU，用户无感知
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing LLaMA model via JNI...")
            Log.d(TAG, "GPU config: useGpu=${config.useGpu}, gpuLayers=${config.gpuLayers}")

            // 获取模型文件
            val modelFile = getModelFile()
            if (!modelFile.exists()) {
                return@withContext Result.failure(
                    Exception("Model file not found: ${modelFile.absolutePath}")
                )
            }

            Log.d(TAG, "Model file path: ${modelFile.absolutePath}, size: ${modelFile.length()} bytes")
            Log.d(TAG, "Context size: ${config.contextLength}, Threads: ${config.threads}")

            // 使用带 GPU 支持的 JNI 加载模型（内置静默回退）
            modelHandle = LlamaCppJNI.initModelWithGpu(
                modelPath = modelFile.absolutePath,
                contextSize = config.contextLength,
                threads = config.threads,
                useGpu = config.useGpu,
                gpuLayers = config.gpuLayers
            )

            if (modelHandle == 0L) {
                return@withContext Result.failure(Exception("Failed to load model via JNI"))
            }

            // 检查实际是否使用了 GPU
            isUsingGpu = LlamaCppJNI.isUsingGpu(modelHandle)
            gpuLayers = LlamaCppJNI.getGpuLayers(modelHandle)

            isModelLoaded = true

            if (isUsingGpu) {
                Log.i(TAG, "Model loaded with GPU acceleration! ($gpuLayers layers on GPU)")
            } else {
                Log.i(TAG, "Model loaded in CPU mode (GPU not available or fallback)")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize model", e)
            isModelLoaded = false
            Result.failure(e)
        }
    }

    /**
     * 检查是否正在使用 GPU 加速
     */
    fun isGpuEnabled(): Boolean = isUsingGpu

    /**
     * 获取当前使用的 GPU 层数
     */
    fun getGpuLayerCount(): Int = gpuLayers

    /**
     * 获取推理模式描述
     */
    fun getInferenceModeDescription(): String {
        return if (isUsingGpu) {
            "GPU 加速模式 ($gpuLayers 层)"
        } else {
            "CPU 模式"
        }
    }

    /**
     * 从assets复制模型到内部存储
     */
    private suspend fun getModelFile(): File = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val modelFile = File(modelDir, config.modelPath)

        // 如果文件已存在且大小正确,直接返回
        if (modelFile.exists()) {
            val assetSize = context.assets.open(config.modelPath).use { it.available().toLong() }
            if (modelFile.length() == assetSize) {
                Log.d(TAG, "Model file already exists: ${modelFile.absolutePath}")
                return@withContext modelFile
            }
        }

        // 复制模型文件
        Log.d(TAG, "Copying model from assets to ${modelFile.absolutePath}")
        context.assets.open(config.modelPath).use { input ->
            FileOutputStream(modelFile).use { output ->
                input.copyTo(output)
            }
        }

        Log.d(TAG, "Model copied successfully, size: ${modelFile.length()} bytes")
        modelFile
    }

    /**
     * 执行推理（非流式）
     * @param prompt 输入提示词
     * @return 推理结果
     */
    suspend fun infer(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isModelLoaded) {
                return@withContext Result.failure(Exception("Model not loaded. Call initialize() first."))
            }

            Log.d(TAG, "Starting inference with prompt: ${prompt.take(50)}...")

            val result = LlamaCppJNI.generate(
                modelHandle = modelHandle,
                prompt = prompt,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK
            )

            // 移除思考标签
            val filteredResult = removeThinkTags(result)

            Log.d(TAG, "Inference completed successfully")
            Result.success(filteredResult)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            Result.failure(e)
        }
    }

    /**
     * 移除文本中的思考标签
     */
    private fun removeThinkTags(text: String): String {
        var result = text
        val thinkPattern = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
        result = thinkPattern.replace(result, "")

        // 如果有未闭合的 <think> 标签，移除从该标签到末尾的所有内容
        val unclosedThinkIndex = result.indexOf("<think>")
        if (unclosedThinkIndex != -1) {
            result = result.substring(0, unclosedThinkIndex)
        }

        return result.trim()
    }

    /**
     * 执行流式推理
     * @param prompt 输入提示词
     * @return Flow发射的文本片段
     */
    fun inferStream(prompt: String): Flow<String> = callbackFlow {
        if (!isModelLoaded) {
            throw Exception("Model not loaded. Call initialize() first.")
        }

        Log.d(TAG, "Starting streaming inference with prompt: ${prompt.take(50)}...")

        // 用于累积完整响应，以便检测和移除思考标签
        val fullResponse = StringBuilder()
        // 标记是否在思考标签内
        var inThinkTag = false
        // 缓存可能不完整的标签
        var tagBuffer = StringBuilder()

        val callback = object : StreamCallback {
            override fun onToken(token: String) {
                // 处理思考标签过滤
                tagBuffer.append(token)
                val bufferedText = tagBuffer.toString()

                // 检查是否包含完整的<think>开始标签
                if (!inThinkTag && bufferedText.contains("<think>")) {
                    val thinkIndex = bufferedText.indexOf("<think>")
                    // 发送<think>之前的内容
                    if (thinkIndex > 0) {
                        val beforeThink = bufferedText.substring(0, thinkIndex)
                        trySend(beforeThink)
                    }
                    inThinkTag = true
                    tagBuffer = StringBuilder(bufferedText.substring(thinkIndex + 7))
                    return
                }

                // 检查是否包含完整的</think>结束标签
                if (inThinkTag && bufferedText.contains("</think>")) {
                    val endIndex = bufferedText.indexOf("</think>")
                    inThinkTag = false
                    tagBuffer = StringBuilder(bufferedText.substring(endIndex + 8))
                    // 继续处理剩余内容
                    if (tagBuffer.isNotEmpty()) {
                        trySend(tagBuffer.toString())
                        tagBuffer = StringBuilder()
                    }
                    return
                }

                // 如果不在思考标签内，检查是否有可能的未完成标签
                if (!inThinkTag) {
                    // 检查是否可能是未完成的<think>标签
                    if (bufferedText.endsWith("<") ||
                        bufferedText.endsWith("<t") ||
                        bufferedText.endsWith("<th") ||
                        bufferedText.endsWith("<thi") ||
                        bufferedText.endsWith("<thin") ||
                        bufferedText.endsWith("<think")) {
                        // 保持在缓冲区，等待更多内容
                        return
                    }
                    // 安全发送内容
                    trySend(bufferedText)
                    tagBuffer = StringBuilder()
                } else {
                    // 在思考标签内，检查是否可能是未完成的</think>标签
                    if (bufferedText.contains("<")) {
                        // 可能是</think>的开始，继续缓存
                        return
                    }
                    // 在思考标签内的内容不发送
                    tagBuffer = StringBuilder()
                }
            }

            override fun onComplete() {
                // 发送缓冲区剩余内容（如果不在思考标签内）
                if (!inThinkTag && tagBuffer.isNotEmpty()) {
                    val remaining = tagBuffer.toString()
                    // 最终过滤可能残留的标签
                    val filtered = removeThinkTags(remaining)
                    if (filtered.isNotEmpty()) {
                        trySend(filtered)
                    }
                }
                Log.d(TAG, "Streaming inference completed")
                close()
            }

            override fun onError(error: String) {
                Log.e(TAG, "Streaming inference error: $error")
                close(Exception(error))
            }
        }

        try {
            LlamaCppJNI.generateStream(
                modelHandle = modelHandle,
                prompt = prompt,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                callback = callback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Streaming inference failed", e)
            close(e)
        }

        awaitClose {
            Log.d(TAG, "Stream channel closed")
        }
    }


    /**
     * 构建完整的提示词（包含对话历史）
     * @param messages 对话历史
     * @param enableThinking 是否启用模型思考（默认禁用）
     * @return 格式化的提示词
     */
    fun buildPrompt(messages: List<Pair<String, String>>, enableThinking: Boolean = false): String {
        // Qwen 模型使用 ChatML 格式
        val sb = StringBuilder()

        var systemContent = ""
        var lastUserMessage: String? = null

        for ((role, content) in messages) {
            when (role) {
                "system" -> {
                    systemContent = content
                }
                "user" -> {
                    lastUserMessage = content
                    sb.append("<|im_start|>user\n$content<|im_end|>\n")
                }
                "assistant" -> sb.append("<|im_start|>assistant\n$content<|im_end|>\n")
            }
        }

        // 添加 system message（如果有的话）
        if (systemContent.isNotEmpty()) {
            sb.insert(0, "<|im_start|>system\n$systemContent<|im_end|>\n")
        }

        // 如果禁用思考，在最后一条用户消息中添加 /no_think 标签
        if (!enableThinking && lastUserMessage != null && !lastUserMessage.contains("/no_think")) {
            // 找到最后一个 user 消息并添加 /no_think
            val lastUserIndex = sb.lastIndexOf("<|im_start|>user\n")
            if (lastUserIndex != -1) {
                val endIndex = sb.indexOf("<|im_end|>", lastUserIndex)
                if (endIndex != -1) {
                    // 在 <|im_end|> 之前添加 /no_think
                    sb.insert(endIndex, " /no_think")
                }
            }
        } else if (enableThinking && lastUserMessage != null && !lastUserMessage.contains("/think")) {
            // 如果启用思考，在最后一条用户消息中添加 /think 标签
            val lastUserIndex = sb.lastIndexOf("<|im_start|>user\n")
            if (lastUserIndex != -1) {
                val endIndex = sb.indexOf("<|im_end|>", lastUserIndex)
                if (endIndex != -1) {
                    sb.insert(endIndex, " /think")
                }
            }
        }

        // 添加助手的开始标记
        sb.append("<|im_start|>assistant\n")

        return sb.toString()
    }

    /**
     * 释放模型资源
     */
    fun release() {
        try {
            if (modelHandle != 0L) {
                LlamaCppJNI.freeModel(modelHandle)
                modelHandle = 0
                isModelLoaded = false
                Log.d(TAG, "Model released successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release model", e)
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isLoaded(): Boolean = isModelLoaded
}
