package com.example.haiyangapp.inference

/**
 * 推理结果封装
 */
sealed class InferenceResult {
    /**
     * 推理成功
     * @param text 生成的文本
     */
    data class Success(val text: String) : InferenceResult()

    /**
     * 推理失败
     * @param error 错误信息
     * @param throwable 异常对象（可选）
     */
    data class Error(val error: String, val throwable: Throwable? = null) : InferenceResult()

    /**
     * 推理进行中（流式输出）
     * @param partialText 部分生成的文本
     * @param isDone 是否完成
     */
    data class Streaming(val partialText: String, val isDone: Boolean = false) : InferenceResult()
}
