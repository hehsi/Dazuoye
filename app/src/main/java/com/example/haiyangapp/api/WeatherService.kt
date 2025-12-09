package com.example.haiyangapp.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 高德天气API服务
 * API文档: https://lbs.amap.com/api/webservice/guide/api/weatherinfo
 */
class WeatherService {

    companion object {
        private const val TAG = "WeatherService"

        // 高德天气API地址
        private const val WEATHER_API_URL = "https://restapi.amap.com/v3/weather/weatherInfo"

        // 高德地理编码API（用于城市名转adcode）
        private const val GEO_API_URL = "https://restapi.amap.com/v3/geocode/geo"

        // TODO: 请替换为你的高德API Key
        private const val AMAP_API_KEY = "f2dcddaac2d2c9e25a36d25200ade121"

        // 常用城市adcode映射（减少API调用）
        private val CITY_CODE_MAP = mapOf(
            "北京" to "110000",
            "上海" to "310000",
            "广州" to "440100",
            "深圳" to "440300",
            "杭州" to "330100",
            "南京" to "320100",
            "成都" to "510100",
            "重庆" to "500000",
            "武汉" to "420100",
            "西安" to "610100",
            "天津" to "120000",
            "苏州" to "320500",
            "长沙" to "430100",
            "郑州" to "410100",
            "青岛" to "370200",
            "厦门" to "350200",
            "福州" to "350100",
            "合肥" to "340100",
            "济南" to "370100",
            "沈阳" to "210100",
            "大连" to "210200",
            "哈尔滨" to "230100",
            "长春" to "220100",
            "昆明" to "530100",
            "贵阳" to "520100",
            "南宁" to "450100",
            "海口" to "460100",
            "三亚" to "460200",
            "拉萨" to "540100",
            "乌鲁木齐" to "650100",
            "兰州" to "620100",
            "银川" to "640100",
            "西宁" to "630100",
            "呼和浩特" to "150100",
            "石家庄" to "130100",
            "太原" to "140100",
            "南昌" to "360100",
            "无锡" to "320200",
            "宁波" to "330200",
            "温州" to "330300",
            "佛山" to "440600",
            "东莞" to "441900",
            "珠海" to "440400"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 获取城市天气
     * @param cityName 城市名称
     * @return 天气信息字符串，失败返回错误信息
     */
    suspend fun getWeather(cityName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting weather for: $cityName")

            // 获取城市代码
            val cityCode = getCityCode(cityName)
            if (cityCode == null) {
                return@withContext Result.failure(Exception("无法找到城市: $cityName"))
            }

            Log.d(TAG, "City code for $cityName: $cityCode")

            // 调用天气API
            val url = "$WEATHER_API_URL?key=$AMAP_API_KEY&city=$cityCode&extensions=base&output=JSON"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("天气API请求失败: ${response.code}"))
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("天气API返回空数据"))
            }

            Log.d(TAG, "Weather API response: $responseBody")

            // 解析响应
            val weatherResponse = gson.fromJson(responseBody, WeatherResponse::class.java)

            if (weatherResponse.status != "1") {
                return@withContext Result.failure(Exception("天气查询失败: ${weatherResponse.info}"))
            }

            val lives = weatherResponse.lives
            if (lives.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未获取到天气数据"))
            }

            val weather = lives[0]
            val result = buildWeatherDescription(weather)

            Log.d(TAG, "Weather result: $result")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get weather", e)
            Result.failure(e)
        }
    }

    /**
     * 获取城市代码
     */
    private suspend fun getCityCode(cityName: String): String? {
        // 先从本地映射查找
        val normalizedName = cityName.replace("市", "").replace("省", "").trim()
        CITY_CODE_MAP[normalizedName]?.let { return it }

        // 如果本地没有，调用地理编码API
        return try {
            val url = "$GEO_API_URL?key=$AMAP_API_KEY&address=$cityName&output=JSON"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null

            val geoResponse = gson.fromJson(responseBody, GeoResponse::class.java)
            if (geoResponse.status == "1" && !geoResponse.geocodes.isNullOrEmpty()) {
                geoResponse.geocodes[0].adcode
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get city code", e)
            null
        }
    }

    /**
     * 构建天气描述
     */
    private fun buildWeatherDescription(weather: WeatherLive): String {
        return StringBuilder().apply {
            append("${weather.city}天气信息：\n")
            append("天气：${weather.weather}\n")
            append("温度：${weather.temperature}°C\n")
            append("湿度：${weather.humidity}%\n")
            append("风向：${weather.windDirection}\n")
            append("风力：${weather.windPower}级\n")
            append("更新时间：${weather.reportTime}")
        }.toString()
    }
}

/**
 * 天气API响应
 */
data class WeatherResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("info")
    val info: String,

    @SerializedName("lives")
    val lives: List<WeatherLive>?
)

/**
 * 实况天气数据
 */
data class WeatherLive(
    @SerializedName("province")
    val province: String,

    @SerializedName("city")
    val city: String,

    @SerializedName("adcode")
    val adcode: String,

    @SerializedName("weather")
    val weather: String,

    @SerializedName("temperature")
    val temperature: String,

    @SerializedName("winddirection")
    val windDirection: String,

    @SerializedName("windpower")
    val windPower: String,

    @SerializedName("humidity")
    val humidity: String,

    @SerializedName("reporttime")
    val reportTime: String
)

/**
 * 地理编码API响应
 */
data class GeoResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("geocodes")
    val geocodes: List<GeoCode>?
)

/**
 * 地理编码结果
 */
data class GeoCode(
    @SerializedName("adcode")
    val adcode: String,

    @SerializedName("city")
    val city: String?
)
