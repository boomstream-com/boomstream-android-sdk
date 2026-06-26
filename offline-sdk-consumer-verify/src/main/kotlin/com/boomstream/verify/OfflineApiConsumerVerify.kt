package com.boomstream.verify

import androidx.media3.datasource.cache.SimpleCache
import com.boomstream.sdk.offline.BoomstreamOfflineManager

/**
 * Compile-only verification that [BoomstreamOfflineManager.downloadCache] is accessible
 * without depending on player-sdk. If media3-datasource is not api()-exposed by offline-sdk,
 * the [SimpleCache] import fails to compile.
 */
@Suppress("UnusedParameter")
fun verifyDownloadCacheOnClasspath(manager: BoomstreamOfflineManager): SimpleCache =
    manager.downloadCache
