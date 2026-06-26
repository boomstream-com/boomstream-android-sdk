package com.boomstream.sdk.offline.internal

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

/**
 * [DataSource.Factory] for offline segment downloads.
 *
 * Injects a custom `User-Agent` header into every HLS segment and manifest request
 * made by the [androidx.media3.exoplayer.offline.DownloadManager].
 *
 * **CSO no-logging constraint:** No HTTP-logging interceptor is registered on this OkHttp
 * client. The `User-Agent` value may carry sensitive media-server-key material; logging request
 * headers in any build variant would expose it to logcat. Consumers who need HTTP diagnostics
 * must add their own interceptor via
 * [com.boomstream.sdk.api.BoomstreamOptions.additionalInterceptors], scoped to debug builds
 * only — never in release or SDK code.
 */
@OptIn(UnstableApi::class)
internal class OfflineDataSourceFactory(userAgent: String) : DataSource.Factory {

    private val delegate: OkHttpDataSource.Factory = OkHttpDataSource.Factory(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            }
            // No HTTP-logging interceptor — per CSO security constraints.
            .build()
    )

    override fun createDataSource(): DataSource = delegate.createDataSource()
}
