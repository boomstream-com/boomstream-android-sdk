package com.boomstream.sdk.api.internal.interceptor

import okhttp3.Interceptor
import okhttp3.Response

internal class BearerAuthInterceptor(
    private val apiKey: String,
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", userAgent)
                .build()
        )
}
