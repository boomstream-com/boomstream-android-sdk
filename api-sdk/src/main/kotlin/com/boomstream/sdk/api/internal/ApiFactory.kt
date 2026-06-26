package com.boomstream.sdk.api.internal

import com.boomstream.sdk.api.internal.interceptor.BearerAuthInterceptor
import com.boomstream.sdk.api.internal.interceptor.UserAgentInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

internal object ApiFactory {

    private const val CONFIG_BASE_URL = "https://play.boomstream.com/"
    private const val API_BASE_URL = "https://boomstream.com/"

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun createConfigService(
        userAgent: String,
        connectTimeoutSeconds: Long,
        readTimeoutSeconds: Long,
        additionalInterceptors: List<Interceptor>,
        baseUrl: String = CONFIG_BASE_URL,
    ): BoomstreamConfigService = createRetrofit(
        baseUrl = baseUrl,
        interceptors = listOf(UserAgentInterceptor(userAgent = userAgent)) +
            additionalInterceptors,
        connectTimeoutSeconds = connectTimeoutSeconds,
        readTimeoutSeconds = readTimeoutSeconds,
    ).create(BoomstreamConfigService::class.java)

    fun createApiService(
        apiKey: String,
        userAgent: String,
        connectTimeoutSeconds: Long,
        readTimeoutSeconds: Long,
        additionalInterceptors: List<Interceptor>,
        baseUrl: String = API_BASE_URL,
    ): BoomstreamApiService = createRetrofit(
        baseUrl = baseUrl,
        interceptors = listOf(BearerAuthInterceptor(apiKey = apiKey, userAgent = userAgent)) + additionalInterceptors,
        connectTimeoutSeconds = connectTimeoutSeconds,
        readTimeoutSeconds = readTimeoutSeconds,
    ).create(BoomstreamApiService::class.java)

    private fun createRetrofit(
        baseUrl: String,
        interceptors: List<Interceptor>,
        connectTimeoutSeconds: Long,
        readTimeoutSeconds: Long,
    ): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .apply { interceptors.forEach { addInterceptor(it) } }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
