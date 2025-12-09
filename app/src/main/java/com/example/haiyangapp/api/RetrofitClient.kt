package com.example.haiyangapp.api

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit客户端配置
 * 单例模式，提供DeepSeek API服务实例
 */
object RetrofitClient {

    private const val BASE_URL = "https://api.deepseek.com/"
    private const val API_KEY = "sk-c960dff7c37846c6b98de07e838ffc4a"

    /**
     * 认证拦截器
     * 自动为每个请求添加Authorization头
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestWithAuth = originalRequest.newBuilder()
            .header("Authorization", "Bearer $API_KEY")
            .header("Content-Type", "application/json")
            .build()
        chain.proceed(requestWithAuth)
    }

    /**
     * 日志拦截器
     * 用于调试，打印请求和响应信息
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /**
     * OkHttp客户端
     * 配置超时时间和拦截器
     */
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Retrofit实例
     */
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        // ResponseBodyConverterFactory必须在GsonConverterFactory之前
        // 确保ResponseBody类型不被Gson解析
        .addConverterFactory(ResponseBodyConverterFactory())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * DeepSeek API服务实例
     */
    val apiService: DeepSeekApiService = retrofit.create(DeepSeekApiService::class.java)
}
