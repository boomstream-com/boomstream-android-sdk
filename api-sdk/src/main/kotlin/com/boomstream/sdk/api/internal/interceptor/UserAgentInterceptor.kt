package com.boomstream.sdk.api.internal.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that sets the custom User-Agent string used for
 * media-server-key propagation on config requests (`play.boomstream.com`).
 *
 * No Authorization header is added — the config endpoint does not require auth.
 */
internal class UserAgentInterceptor(private val userAgent: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .build()
        )
}
