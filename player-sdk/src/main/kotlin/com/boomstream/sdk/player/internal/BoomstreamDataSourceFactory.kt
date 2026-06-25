package com.boomstream.sdk.player.internal

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.boomstream.sdk.player.BuildConfig
import okhttp3.OkHttpClient

/**
 * [DataSource.Factory] that injects a `User-Agent` header into every HLS segment and
 * manifest request via a dedicated OkHttp client.
 *
 * The base User-Agent is `Boomstream Android SDK v<SDK_VERSION>`.  When [allowClearKeyDRMtoken]
 * is non-null and non-blank, it is appended after a single space:
 * `Boomstream Android SDK v<SDK_VERSION> <allowClearKeyDRMtoken>`.
 * The media server validates this token to enable native Clear Key DRM for mobile playback.
 *
 * **CSO constraint #2:** `HttpLoggingInterceptor` is deliberately absent from this
 * OkHttp client. The User-Agent value carries the optional Clear Key DRM token — logging
 * request headers in any build variant would expose it.
 *
 * ## Interceptor-injection scope
 * [com.boomstream.sdk.api.BoomstreamOptions.additionalInterceptors] is wired into the **api-sdk**
 * OkHttp client only — it covers integrator-side logging/observability of Boomstream **config
 * endpoint** fetches. It does **NOT** flow into this player-sdk segment/manifest client.
 * HLS segment URLs (which carry the UA-injected AES key when present) are by design
 * NOT observable from any integrator-supplied interceptor. This is correct security behavior,
 * not a missing feature.
 *
 * If an integrator needs debug-level HTTP logging of the config endpoint, they may pass an
 * `HttpLoggingInterceptor` via `BoomstreamOptions.additionalInterceptors` and restrict it to
 * debug builds (their responsibility). Such an interceptor will **never** see segment requests.
 */
@OptIn(UnstableApi::class)
internal class BoomstreamDataSourceFactory(
    allowClearKeyDRMtoken: String? = null,
) : DataSource.Factory {

    private val userAgent: String = buildString {
        append("Boomstream Android SDK v")
        append(BuildConfig.SDK_VERSION)
        if (!allowClearKeyDRMtoken.isNullOrBlank()) {
            append(' ')
            append(allowClearKeyDRMtoken)
        }
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            // Inject User-Agent on every request made by ExoPlayer for HLS segment/manifest fetches.
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .build()
            )
        }
        // NO HttpLoggingInterceptor — CSO constraint #2.
        .build()

    private val delegate: OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(okHttpClient)

    override fun createDataSource(): DataSource = delegate.createDataSource()
}
