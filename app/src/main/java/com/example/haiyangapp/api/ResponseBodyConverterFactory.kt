package com.example.haiyangapp.api

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * 自定义转换器工厂
 * 确保ResponseBody类型直接返回，不经过Gson解析
 */
class ResponseBodyConverterFactory : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        // 如果返回类型是ResponseBody，直接返回不做转换
        if (type == ResponseBody::class.java) {
            return Converter<ResponseBody, ResponseBody> { body -> body }
        }
        return null
    }
}
