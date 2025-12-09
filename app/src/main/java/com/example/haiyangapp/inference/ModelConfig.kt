package com.example.haiyangapp.inference

/**
 * LLM模型配置
 * 定义推理参数和模型路径
 */
data class ModelConfig(
    /**
     * 模型文件路径（assets中的相对路径）
     */
    val modelPath: String = "qwen3-lora-merged-q4_k_m.gguf",

    /**
     * 上下文长度（token数量）
     */
    val contextLength: Int = 2048,

    /**
     * 每次生成的最大token数
     */
    val maxTokens: Int = 512,

    /**
     * 温度参数（0.0-2.0），控制随机性
     * 较低的值使输出更确定，较高的值使输出更随机
     */
    val temperature: Float = 0.7f,

    /**
     * Top-P采样参数（0.0-1.0）
     * 控制采样的多样性
     */
    val topP: Float = 0.9f,

    /**
     * Top-K采样参数
     * 限制候选token的数量
     */
    val topK: Int = 40,

    /**
     * 重复惩罚系数（1.0为无惩罚）
     */
    val repeatPenalty: Float = 1.1f,

    /**
     * 使用的线程数
     */
    val threads: Int = 4,

    /**
     * 是否使用GPU加速
     */
    val useGpu: Boolean = true,

    /**
     * GPU层数（-1为全部使用GPU）
     */
    val gpuLayers: Int = -1
)
