#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include "llama.h"
#include "ggml.h"

#define TAG "LlamaCpp-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// 存储模型和上下文的结构
struct llama_context_wrapper {
    llama_model* model;
    llama_context* ctx;
    bool using_gpu;      // 是否正在使用 GPU
    int gpu_layers;      // GPU 层数
};

// ============================================
// GPU 能力检测
// ============================================

// 检测 Vulkan GPU 是否可用
static bool detect_vulkan_available() {
#ifdef GGML_USE_VULKAN
    // 尝试检测 Vulkan 后端
    LOGI("Checking Vulkan availability...");

    // ggml_backend_vulkan_device_count 返回可用的 Vulkan 设备数量
    // 如果编译时没有启用 Vulkan，这个函数不存在，会在链接时失败
    // 所以我们用一个简单的检查
    return true;  // 如果编译时启用了 GGML_USE_VULKAN，就返回 true
#else
    LOGI("Vulkan backend not compiled in");
    return false;
#endif
}

// ============================================
// 模型初始化函数（原版 - CPU only）
// ============================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_initModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint contextSize,
    jint threads) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGD("Initializing model from: %s", path);
    LOGD("Context size: %d, Threads: %d", contextSize, threads);

    // 初始化 llama 后端
    llama_backend_init();
    llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);

    // 设置模型参数
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU only 模式

    // 加载模型
    llama_model* model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        LOGE("Failed to load model");
        return 0;
    }

    // 设置上下文参数
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    // 创建上下文
    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Failed to create context");
        llama_free_model(model);
        return 0;
    }

    // 创建包装结构
    llama_context_wrapper* wrapper = new llama_context_wrapper();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->using_gpu = false;
    wrapper->gpu_layers = 0;

    LOGD("Model initialized successfully (CPU only)!");
    return reinterpret_cast<jlong>(wrapper);
}

// ============================================
// 模型初始化函数（GPU 支持版本，带静默回退）
// ============================================
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_initModelWithGpu(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint contextSize,
    jint threads,
    jboolean useGpu,
    jint gpuLayers) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing model from: %s", path);
    LOGI("Context size: %d, Threads: %d, UseGPU: %d, GPU Layers: %d",
         contextSize, threads, useGpu, gpuLayers);

    // 初始化 llama 后端
    llama_backend_init();
    llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);

    // 设置模型参数
    llama_model_params model_params = llama_model_default_params();

    bool gpu_available = false;
    int actual_gpu_layers = 0;

    // 尝试使用 GPU
    if (useGpu) {
        gpu_available = detect_vulkan_available();

        if (gpu_available) {
            // gpuLayers = -1 表示全部层都用 GPU
            actual_gpu_layers = (gpuLayers < 0) ? 99 : gpuLayers;
            model_params.n_gpu_layers = actual_gpu_layers;
            LOGI("GPU acceleration enabled, using %d layers on GPU", actual_gpu_layers);
        } else {
            LOGW("GPU requested but not available, falling back to CPU");
            model_params.n_gpu_layers = 0;
        }
    } else {
        model_params.n_gpu_layers = 0;
        LOGI("GPU disabled by user, using CPU only");
    }

    // 加载模型
    llama_model* model = llama_load_model_from_file(path, model_params);

    // 如果 GPU 加载失败，尝试回退到 CPU
    if (model == nullptr && gpu_available && actual_gpu_layers > 0) {
        LOGW("GPU model loading failed, attempting CPU fallback...");
        model_params.n_gpu_layers = 0;
        model = llama_load_model_from_file(path, model_params);
        gpu_available = false;
        actual_gpu_layers = 0;
    }

    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        LOGE("Failed to load model (both GPU and CPU attempts failed)");
        return 0;
    }

    // 设置上下文参数
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    // 创建上下文
    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Failed to create context");
        llama_free_model(model);
        return 0;
    }

    // 创建包装结构
    llama_context_wrapper* wrapper = new llama_context_wrapper();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->using_gpu = gpu_available && (actual_gpu_layers > 0);
    wrapper->gpu_layers = actual_gpu_layers;

    if (wrapper->using_gpu) {
        LOGI("Model initialized successfully with GPU acceleration (%d layers)!", actual_gpu_layers);
    } else {
        LOGI("Model initialized successfully (CPU only, GPU fallback or disabled)");
    }

    return reinterpret_cast<jlong>(wrapper);
}

// ============================================
// 获取当前推理模式信息
// ============================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_isUsingGpu(
    JNIEnv* env,
    jobject /* this */,
    jlong modelHandle) {

    if (modelHandle == 0) {
        return JNI_FALSE;
    }

    llama_context_wrapper* wrapper = reinterpret_cast<llama_context_wrapper*>(modelHandle);
    return wrapper->using_gpu ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_getGpuLayers(
    JNIEnv* env,
    jobject /* this */,
    jlong modelHandle) {

    if (modelHandle == 0) {
        return 0;
    }

    llama_context_wrapper* wrapper = reinterpret_cast<llama_context_wrapper*>(modelHandle);
    return wrapper->gpu_layers;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_generate(
    JNIEnv* env,
    jobject /* this */,
    jlong modelHandle,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jint topK) {

    if (modelHandle == 0) {
        LOGE("Model handle is null");
        return env->NewStringUTF("");
    }

    llama_context_wrapper* wrapper = reinterpret_cast<llama_context_wrapper*>(modelHandle);
    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Generating text for prompt (length: %zu)", strlen(promptStr));

    // Tokenize prompt
    const llama_vocab* vocab = llama_model_get_vocab(wrapper->model);

    // 预分配足够的空间
    std::vector<llama_token> tokens;
    tokens.resize(strlen(promptStr) + 256);

    int n_tokens = llama_tokenize(
        vocab,
        promptStr,
        strlen(promptStr),
        tokens.data(),
        tokens.size(),
        true,   // add_special - 添加BOS token
        true    // parse_special - 解析ChatML特殊标记(<|im_start|>, <|im_end|>等)
    );

    env->ReleaseStringUTFChars(prompt, promptStr);

    if (n_tokens < 0) {
        LOGE("Failed to tokenize prompt");
        return env->NewStringUTF("");
    }

    tokens.resize(n_tokens);

    LOGD("Prompt tokenized: %d tokens", n_tokens);

    // 准备采样参数
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);

    // 添加重复惩罚
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        256,     // penalty_last_n - 增加到256,考虑更长的历史
        1.15f,   // penalty_repeat - 提高重复惩罚系数
        0.1f,    // penalty_freq - 添加频率惩罚
        0.0f     // penalty_present
    ));

    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // 清空内存缓存,避免上次对话影响本次生成
    llama_memory_t mem = llama_get_memory(wrapper->ctx);
    llama_memory_clear(mem, true);

    // 创建批次
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);

    // 评估提示词
    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        llama_sampler_free(smpl);
        return env->NewStringUTF("");
    }

    // 生成tokens
    std::string result;
    int n_generated = 0;

    while (n_generated < maxTokens) {
        // 采样下一个token
        llama_token new_token = llama_sampler_sample(smpl, wrapper->ctx, -1);

        // 检查是否是结束符(EOS/EOG/EOT)
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGD("End of generation token received");
            break;
        }

        // 将token转换为文本
        char piece[256];
        int n_piece = llama_token_to_piece(vocab, new_token, piece, sizeof(piece), 0, false);
        if (n_piece > 0) {
            result.append(piece, n_piece);

            // 额外检查: 如果生成的文本包含<|im_end|>标记,停止生成
            if (result.find("<|im_end|>") != std::string::npos) {
                LOGD("Found <|im_end|> in generated text, stopping");
                // 移除<|im_end|>标记
                size_t pos = result.find("<|im_end|>");
                result = result.substr(0, pos);
                break;
            }
        }

        // 准备下一次解码
        batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(wrapper->ctx, batch) != 0) {
            LOGE("Failed to decode token");
            break;
        }

        n_generated++;
    }

    llama_sampler_free(smpl);

    LOGD("Generation completed: %d tokens generated", n_generated);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_freeModel(
    JNIEnv* env,
    jobject /* this */,
    jlong modelHandle) {

    if (modelHandle == 0) {
        LOGW("Attempting to free null model handle");
        return;
    }

    LOGD("Freeing model");
    llama_context_wrapper* wrapper = reinterpret_cast<llama_context_wrapper*>(modelHandle);

    if (wrapper->ctx) {
        llama_free(wrapper->ctx);
    }
    if (wrapper->model) {
        llama_free_model(wrapper->model);
    }

    delete wrapper;
    llama_backend_free();

    LOGD("Model freed successfully");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_generateStream(
    JNIEnv* env,
    jobject /* this */,
    jlong modelHandle,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jint topK,
    jobject callback) {

    if (modelHandle == 0) {
        LOGE("Model handle is null");
        return;
    }

    llama_context_wrapper* wrapper = reinterpret_cast<llama_context_wrapper*>(modelHandle);
    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Generating text (stream) for prompt (length: %zu)", strlen(promptStr));

    // 获取回调方法
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "()V");
    jmethodID onErrorMethod = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");

    if (onTokenMethod == nullptr || onCompleteMethod == nullptr || onErrorMethod == nullptr) {
        LOGE("Failed to get callback methods");
        env->ReleaseStringUTFChars(prompt, promptStr);
        return;
    }

    // Tokenize prompt
    const llama_vocab* vocab = llama_model_get_vocab(wrapper->model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(promptStr) + 256);

    int n_tokens = llama_tokenize(
        vocab,
        promptStr,
        strlen(promptStr),
        tokens.data(),
        tokens.size(),
        true,
        true
    );

    env->ReleaseStringUTFChars(prompt, promptStr);

    if (n_tokens < 0) {
        LOGE("Failed to tokenize prompt");
        jstring errorMsg = env->NewStringUTF("Failed to tokenize prompt");
        env->CallVoidMethod(callback, onErrorMethod, errorMsg);
        return;
    }

    tokens.resize(n_tokens);
    LOGD("Prompt tokenized: %d tokens", n_tokens);

    // 准备采样参数
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);

    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        256, 1.15f, 0.1f, 0.0f
    ));

    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // 清空内存缓存
    llama_memory_t mem = llama_get_memory(wrapper->ctx);
    llama_memory_clear(mem, true);

    // 创建批次
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);

    // 评估提示词
    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        llama_sampler_free(smpl);
        jstring errorMsg = env->NewStringUTF("Failed to decode prompt");
        env->CallVoidMethod(callback, onErrorMethod, errorMsg);
        return;
    }

    // 生成tokens
    std::string result;
    int n_generated = 0;

    while (n_generated < maxTokens) {
        // 采样下一个token
        llama_token new_token = llama_sampler_sample(smpl, wrapper->ctx, -1);

        // 检查是否是结束符
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGD("End of generation token received");
            break;
        }

        // 将token转换为文本
        char piece[256];
        int n_piece = llama_token_to_piece(vocab, new_token, piece, sizeof(piece), 0, false);
        if (n_piece > 0) {
            std::string tokenStr(piece, n_piece);
            result.append(tokenStr);

            // 检查<|im_end|>标记
            if (result.find("<|im_end|>") != std::string::npos) {
                LOGD("Found <|im_end|> in generated text, stopping");
                break;
            }

            // 流式回调：发送每个token
            jstring tokenJStr = env->NewStringUTF(tokenStr.c_str());
            env->CallVoidMethod(callback, onTokenMethod, tokenJStr);
            env->DeleteLocalRef(tokenJStr);
        }

        // 准备下一次解码
        batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(wrapper->ctx, batch) != 0) {
            LOGE("Failed to decode token");
            break;
        }

        n_generated++;
    }

    llama_sampler_free(smpl);

    LOGD("Stream generation completed: %d tokens generated", n_generated);
    env->CallVoidMethod(callback, onCompleteMethod);
}

// ============================================
// 嵌入模型相关函数 (用于知识库 RAG)
// ============================================

// 嵌入模型上下文包装
struct embedding_context_wrapper {
    llama_model* model;
    llama_context* ctx;
    int n_embd;  // 嵌入维度
};

/**
 * 初始化嵌入模型
 * @param modelPath 嵌入模型路径 (如 all-MiniLM-L6-v2.gguf)
 * @param contextSize 上下文大小 (嵌入模型一般用 512)
 * @param threads CPU 线程数
 * @return 模型句柄
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_initEmbeddingModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint contextSize,
    jint threads) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing embedding model from: %s", path);

    // 初始化后端 (如果尚未初始化)
    static bool backend_initialized = false;
    if (!backend_initialized) {
        llama_backend_init();
        llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);
        backend_initialized = true;
    }

    // 模型参数 - 嵌入模型使用 CPU
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU only for embedding

    // 加载模型
    llama_model* model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        LOGE("Failed to load embedding model");
        return 0;
    }

    // 上下文参数 - 启用嵌入模式
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    ctx_params.embeddings = true;  // 启用嵌入模式

    // 创建上下文
    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Failed to create embedding context");
        llama_free_model(model);
        return 0;
    }

    // 获取嵌入维度
    int n_embd = llama_n_embd(model);
    LOGI("Embedding model initialized, dimension: %d", n_embd);

    // 创建包装
    embedding_context_wrapper* wrapper = new embedding_context_wrapper();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->n_embd = n_embd;

    return reinterpret_cast<jlong>(wrapper);
}

/**
 * 获取嵌入维度
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_getEmbeddingDimension(
    JNIEnv* env,
    jobject /* this */,
    jlong modelHandle) {

    if (modelHandle == 0) {
        return 0;
    }

    embedding_context_wrapper* wrapper = reinterpret_cast<embedding_context_wrapper*>(modelHandle);
    return wrapper->n_embd;
}

/**
 * 获取单条文本的嵌入向量
 * @param modelHandle 嵌入模型句柄
 * @param text 输入文本
 * @return 嵌入向量 (FloatArray)
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_getEmbedding(
    JNIEnv* env,
    jobject /* this */,
    jlong modelHandle,
    jstring text) {

    if (modelHandle == 0) {
        LOGE("Embedding model handle is null");
        return nullptr;
    }

    embedding_context_wrapper* wrapper = reinterpret_cast<embedding_context_wrapper*>(modelHandle);
    const char* textStr = env->GetStringUTFChars(text, nullptr);

    // Tokenize
    const llama_vocab* vocab = llama_model_get_vocab(wrapper->model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(textStr) + 256);

    int n_tokens = llama_tokenize(
        vocab,
        textStr,
        strlen(textStr),
        tokens.data(),
        tokens.size(),
        true,   // add_special
        true    // parse_special
    );

    env->ReleaseStringUTFChars(text, textStr);

    if (n_tokens < 0) {
        LOGE("Failed to tokenize text for embedding");
        return nullptr;
    }

    tokens.resize(n_tokens);
    LOGD("Embedding: tokenized %d tokens", n_tokens);

    // 清空 KV 缓存
    llama_memory_t mem = llama_get_memory(wrapper->ctx);
    llama_memory_clear(mem, true);

    // 创建批次并解码
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);

    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("Failed to decode for embedding");
        return nullptr;
    }

    // 获取嵌入向量 (使用序列0的嵌入)
    const float* embd = llama_get_embeddings_seq(wrapper->ctx, 0);
    if (embd == nullptr) {
        // 尝试获取整体嵌入
        embd = llama_get_embeddings(wrapper->ctx);
    }

    if (embd == nullptr) {
        LOGE("Failed to get embeddings");
        return nullptr;
    }

    // 创建 Java float 数组
    jfloatArray result = env->NewFloatArray(wrapper->n_embd);
    if (result == nullptr) {
        LOGE("Failed to create float array");
        return nullptr;
    }

    // 归一化嵌入向量 (L2 normalization)
    std::vector<float> normalized(wrapper->n_embd);
    float norm = 0.0f;
    for (int i = 0; i < wrapper->n_embd; i++) {
        norm += embd[i] * embd[i];
    }
    norm = sqrtf(norm);
    if (norm > 0) {
        for (int i = 0; i < wrapper->n_embd; i++) {
            normalized[i] = embd[i] / norm;
        }
    } else {
        for (int i = 0; i < wrapper->n_embd; i++) {
            normalized[i] = embd[i];
        }
    }

    env->SetFloatArrayRegion(result, 0, wrapper->n_embd, normalized.data());

    LOGD("Embedding computed successfully, dimension: %d", wrapper->n_embd);
    return result;
}

/**
 * 释放嵌入模型
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_haiyangapp_inference_LlamaCppJNI_freeEmbeddingModel(
    JNIEnv* env,
    jobject /* this */,
    jlong modelHandle) {

    if (modelHandle == 0) {
        LOGW("Attempting to free null embedding model handle");
        return;
    }

    LOGD("Freeing embedding model");
    embedding_context_wrapper* wrapper = reinterpret_cast<embedding_context_wrapper*>(modelHandle);

    if (wrapper->ctx) {
        llama_free(wrapper->ctx);
    }
    if (wrapper->model) {
        llama_free_model(wrapper->model);
    }

    delete wrapper;
    LOGD("Embedding model freed successfully");
}
