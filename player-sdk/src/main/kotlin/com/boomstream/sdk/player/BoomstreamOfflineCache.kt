package com.boomstream.sdk.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache

/**
 * Bridge interface that supplies a [SimpleCache] for offline HLS playback.
 *
 * Pass an instance to [BoomstreamPlayer] so the player can serve cached segments from local
 * storage and fall back to streaming when a segment is not yet cached:
 *
 * ```kotlin
 * // With :offline-sdk on the classpath:
 * BoomstreamPlayer(
 *     mediaCode = "Il4lNOfL",
 *     configClient = Boomstream.configClient,
 *     offlineCache = BoomstreamOfflineCache { offlineManager.downloadCache },
 * )
 * ```
 *
 * When [BoomstreamPlayer] receives a non-null [BoomstreamOfflineCache] it wraps the data-source
 * pipeline with [androidx.media3.datasource.cache.CacheDataSource]: segment requests are served
 * from [SimpleCache] when present, and fetched via the streaming HLS URL otherwise.
 * Passing `null` (the default) disables offline support — all content is streamed.
 *
 * The companion [DownloadState][com.boomstream.sdk.offline.DownloadState] of a media code does
 * **not** need to be `Completed` for playback to succeed — partially cached content plays with
 * automatic live-edge fallback for uncached segments.
 */
@OptIn(UnstableApi::class)
fun interface BoomstreamOfflineCache {
    /** Returns the shared [SimpleCache] that stores downloaded HLS segments. */
    fun provideCache(): SimpleCache
}
