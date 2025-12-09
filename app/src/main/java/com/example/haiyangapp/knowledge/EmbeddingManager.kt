package com.example.haiyangapp.knowledge

import android.content.Context
import android.util.Log
import com.example.haiyangapp.inference.LlamaCppJNI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * 嵌入模型管理器
 * 负责加载、管理和释放嵌入模型
 *
 * 特性：
 * - 懒加载：仅在需要时加载模型
 * - 自动释放：空闲5分钟后自动释放模型
 * - 线程安全：使用协程和同步机制
 */
class EmbeddingManager(private val context: Context) {

    companion object {
        private const val TAG = "EmbeddingManager"

        /** 嵌入模型文件名 */
        private const val EMBEDDING_MODEL_NAME = "all-minilm-l6-v2-q8_0.gguf"

        /** 嵌入模型上下文大小 */
        private const val CONTEXT_SIZE = 512

        /** CPU 线程数 */
        private const val THREAD_COUNT = 4

        /** 空闲超时时间（毫秒） */
        private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L  // 5 分钟

        /** 嵌入向量维度 (all-MiniLM-L6-v2) */
        const val EMBEDDING_DIMENSION = 384
    }

    /** 模型句柄 */
    private var modelHandle: Long = 0

    /** 最后使用时间 */
    private var lastUsedTime: Long = 0

    /** 协程作用域 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 空闲检查任务 */
    private var idleCheckJob: Job? = null

    /** 加载状态 */
    sealed class LoadingState {
        object NotLoaded : LoadingState()
        object Loading : LoadingState()
        object Ready : LoadingState()
        data class Error(val message: String) : LoadingState()
    }

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.NotLoaded)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    /** 同步锁 */
    private val lock = Any()

    /**
     * 检查模型是否已加载
     */
    fun isLoaded(): Boolean = synchronized(lock) {
        modelHandle != 0L
    }

    /**
     * 初始化嵌入模型（懒加载）
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (modelHandle != 0L) {
                lastUsedTime = System.currentTimeMillis()
                return@withContext Result.success(Unit)
            }
        }

        _loadingState.value = LoadingState.Loading
        Log.i(TAG, "Starting embedding model initialization...")

        try {
            // 准备模型文件路径
            val modelPath = prepareModelFile()
                ?: return@withContext Result.failure(
                    Exception("嵌入模型文件不存在，请下载 $EMBEDDING_MODEL_NAME")
                )

            Log.i(TAG, "Loading embedding model from: $modelPath")

            // 加载模型
            val handle = LlamaCppJNI.initEmbeddingModel(
                modelPath,
                CONTEXT_SIZE,
                THREAD_COUNT
            )

            if (handle == 0L) {
                _loadingState.value = LoadingState.Error("模型加载失败")
                return@withContext Result.failure(Exception("嵌入模型加载失败"))
            }

            synchronized(lock) {
                modelHandle = handle
                lastUsedTime = System.currentTimeMillis()
            }

            // 获取并验证维度
            val dimension = LlamaCppJNI.getEmbeddingDimension(handle)
            Log.i(TAG, "Embedding model loaded, dimension: $dimension")

            _loadingState.value = LoadingState.Ready

            // 启动空闲检查
            startIdleCheck()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize embedding model", e)
            _loadingState.value = LoadingState.Error(e.message ?: "未知错误")
            Result.failure(e)
        }
    }

    /**
     * 准备模型文件（从 assets 复制到内部存储）
     */
    private fun prepareModelFile(): String? {
        val internalDir = context.filesDir
        val modelFile = File(internalDir, EMBEDDING_MODEL_NAME)

        // 检查是否已存在
        if (modelFile.exists() && modelFile.length() > 0) {
            Log.d(TAG, "Embedding model already exists at: ${modelFile.absolutePath}")
            return modelFile.absolutePath
        }

        // 从 assets 复制
        return try {
            Log.i(TAG, "Copying embedding model from assets...")
            context.assets.open(EMBEDDING_MODEL_NAME).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Embedding model copied to: ${modelFile.absolutePath}")
            modelFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy embedding model from assets", e)
            // 模型文件可能不在 assets 中，检查其他位置
            checkAlternativeLocations()
        }
    }

    /**
     * 检查其他可能的模型位置
     */
    private fun checkAlternativeLocations(): String? {
        // 检查外部存储
        val externalFile = File(context.getExternalFilesDir(null), EMBEDDING_MODEL_NAME)
        if (externalFile.exists() && externalFile.length() > 0) {
            return externalFile.absolutePath
        }

        // 检查 Downloads 目录
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val downloadFile = File(downloadsDir, EMBEDDING_MODEL_NAME)
        if (downloadFile.exists() && downloadFile.length() > 0) {
            return downloadFile.absolutePath
        }

        return null
    }

    /**
     * 获取文本的嵌入向量
     *
     * @param text 输入文本
     * @return 嵌入向量，失败返回 null
     */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        // 确保模型已加载
        if (!isLoaded()) {
            val result = initialize()
            if (result.isFailure) {
                Log.e(TAG, "Failed to initialize embedding model for embed()")
                return@withContext null
            }
        }

        val handle = synchronized(lock) {
            lastUsedTime = System.currentTimeMillis()
            modelHandle
        }

        if (handle == 0L) {
            Log.e(TAG, "Embedding model handle is null")
            return@withContext null
        }

        try {
            val embedding = LlamaCppJNI.getEmbedding(handle, text)
            if (embedding == null) {
                Log.e(TAG, "JNI getEmbedding returned null for text: ${text.take(50)}...")
                return@withContext null
            }

            // 检查嵌入是否为全零（可能表示生成失败）
            val isAllZero = embedding.all { it == 0f }
            if (isAllZero) {
                Log.w(TAG, "Embedding is all zeros for text: ${text.take(50)}...")
            }

            Log.d(TAG, "Embedding generated: dimension=${embedding.size}, first5=${embedding.take(5).joinToString()}")
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get embedding for text: ${text.take(50)}...", e)
            null
        }
    }

    /**
     * 批量获取嵌入向量
     *
     * @param texts 文本列表
     * @return 嵌入向量列表
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.IO) {
        // 确保模型已加载
        if (!isLoaded()) {
            val result = initialize()
            if (result.isFailure) {
                return@withContext texts.map { null }
            }
        }

        texts.map { text ->
            embed(text)
        }
    }

    /**
     * 启动空闲检查任务
     */
    private fun startIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = scope.launch {
            while (isActive) {
                delay(60_000)  // 每分钟检查一次

                val shouldRelease = synchronized(lock) {
                    if (modelHandle != 0L) {
                        val idleTime = System.currentTimeMillis() - lastUsedTime
                        idleTime >= IDLE_TIMEOUT_MS
                    } else {
                        false
                    }
                }

                if (shouldRelease) {
                    Log.i(TAG, "Embedding model idle timeout, releasing...")
                    release()
                    break
                }
            }
        }
    }

    /**
     * 释放模型资源
     */
    fun release() {
        synchronized(lock) {
            if (modelHandle != 0L) {
                Log.i(TAG, "Releasing embedding model...")
                LlamaCppJNI.freeEmbeddingModel(modelHandle)
                modelHandle = 0
                _loadingState.value = LoadingState.NotLoaded
                Log.i(TAG, "Embedding model released")
            }
        }
        idleCheckJob?.cancel()
    }

    /**
     * 销毁管理器
     */
    fun destroy() {
        release()
        scope.cancel()
    }
}
