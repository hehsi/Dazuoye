package com.example.haiyangapp.inference

/**
 * llama.cpp JNI 接口
 * 负责调用原生 C++ 代码
 */
object LlamaCppJNI {

    init {
        try {
            // 加载原生库
            System.loadLibrary("llama-android")
        } catch (e: UnsatisfiedLinkError) {
            throw RuntimeException("Failed to load llama-android native library", e)
        }
    }

    /**
     * 初始化模型 (CPU only 模式)
     * @param modelPath 模型文件路径
     * @param contextSize 上下文大小
     * @param threads 线程数
     * @return 模型句柄
     */
    external fun initModel(
        modelPath: String,
        contextSize: Int,
        threads: Int
    ): Long

    /**
     * 初始化模型 (GPU 支持版本，带静默回退)
     *
     * 创新点：
     * - 尝试使用 Vulkan GPU 加速
     * - 如果 GPU 不可用，自动静默回退到 CPU
     * - 用户无感知，始终能正常工作
     *
     * @param modelPath 模型文件路径
     * @param contextSize 上下文大小
     * @param threads CPU 线程数
     * @param useGpu 是否尝试使用 GPU
     * @param gpuLayers GPU 层数 (-1 表示全部层)
     * @return 模型句柄
     */
    external fun initModelWithGpu(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        useGpu: Boolean,
        gpuLayers: Int
    ): Long

    /**
     * 检查当前是否正在使用 GPU 加速
     * @param modelHandle 模型句柄
     * @return true 如果使用 GPU, false 如果使用 CPU
     */
    external fun isUsingGpu(modelHandle: Long): Boolean

    /**
     * 获取当前使用的 GPU 层数
     * @param modelHandle 模型句柄
     * @return GPU 层数，0 表示纯 CPU
     */
    external fun getGpuLayers(modelHandle: Long): Int

    /**
     * 生成文本
     * @param modelHandle 模型句柄
     * @param prompt 提示词
     * @param maxTokens 最大token数
     * @param temperature 温度参数
     * @param topP top-p 采样参数
     * @param topK top-k 采样参数
     * @return 生成的文本
     */
    external fun generate(
        modelHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int
    ): String

    /**
     * 流式生成文本
     * @param modelHandle 模型句柄
     * @param prompt 提示词
     * @param maxTokens 最大token数
     * @param temperature 温度参数
     * @param topP top-p 采样参数
     * @param topK top-k 采样参数
     * @param callback 流式回调接口
     */
    external fun generateStream(
        modelHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        callback: StreamCallback
    )

    /**
     * 释放模型资源
     * @param modelHandle 模型句柄
     */
    external fun freeModel(modelHandle: Long)

    // ============================================
    // 嵌入模型相关方法 (用于知识库 RAG)
    // ============================================

    /**
     * 初始化嵌入模型
     *
     * @param modelPath 嵌入模型文件路径 (如 all-MiniLM-L6-v2.gguf)
     * @param contextSize 上下文大小 (嵌入模型一般用 512)
     * @param threads CPU 线程数
     * @return 嵌入模型句柄，0 表示失败
     */
    external fun initEmbeddingModel(
        modelPath: String,
        contextSize: Int,
        threads: Int
    ): Long

    /**
     * 获取嵌入模型的向量维度
     *
     * @param modelHandle 嵌入模型句柄
     * @return 向量维度 (如 384 for all-MiniLM-L6-v2)
     */
    external fun getEmbeddingDimension(modelHandle: Long): Int

    /**
     * 获取文本的嵌入向量
     *
     * @param modelHandle 嵌入模型句柄
     * @param text 输入文本
     * @return 归一化后的嵌入向量 (L2 normalized)，失败返回 null
     */
    external fun getEmbedding(modelHandle: Long, text: String): FloatArray?

    /**
     * 释放嵌入模型资源
     *
     * @param modelHandle 嵌入模型句柄
     */
    external fun freeEmbeddingModel(modelHandle: Long)
}

/**
 * 流式生成回调接口
 */
interface StreamCallback {
    /**
     * 每生成一个token时调用
     * @param token 生成的token文本
     */
    fun onToken(token: String)

    /**
     * 生成完成时调用
     */
    fun onComplete()

    /**
     * 发生错误时调用
     * @param error 错误信息
     */
    fun onError(error: String)
}
