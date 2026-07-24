package com.boomstream.sdk.player

/**
 * Whitelisted performance tuning knobs for [BoomstreamPlayer] and [BoomstreamPlayerView].
 *
 * **CSO constraint #1:** The raw `ExoPlayer` instance is intentionally absent from
 * the public API. Returning it would allow integrators to bypass the SDK's `BoomstreamMediaSourceFactory`
 * (losing User-Agent injection and certificate pinning) or attach an `HttpLoggingInterceptor`
 * at `Level.BODY` which would leak `User-Agent` — possibly carrying the media-server-key — to
 * logcat. Use this class for permissible customisation instead.
 *
 * **Interceptor-injection scope:**
 * [com.boomstream.sdk.api.BoomstreamOptions.additionalInterceptors] covers the **api-sdk** config
 * endpoint OkHttp client only — it is **NOT** an integrator hook into HLS segment/manifest fetches.
 * Segment requests are made by player-sdk's `BoomstreamDataSourceFactory`, which intentionally
 * does **not** receive `additionalInterceptors`. Segment URLs (and the UA they carry) are not
 * observable from integrator code by design.
 *
 * @param minBufferMs           Minimum buffered playback duration (ms) before ExoPlayer stops
 *                              buffering. Default 15 000 ms (15 s).
 * @param maxBufferMs           Maximum playback buffer ExoPlayer retains (ms). Default 50 000 ms.
 * @param bufferForPlaybackMs   Buffer required after an under-run before resuming playback (ms).
 *                              Default 2 500 ms.
 * @param bufferForPlaybackAfterRebufferMs Buffer target after a rebuffer event (ms). Default 5 000 ms.
 * @param enableQualitySelector When `true`, a **Quality** row is added to the player settings
 *                              panel (the gear icon, ⚙). The panel already contains Speed and
 *                              Audio options; this flag controls whether Quality appears alongside
 *                              them. Tapping a quality row calls [BoomstreamPlayerController.selectQuality]
 *                              or [BoomstreamPlayerController.selectAuto] under the hood.
 *
 *                              When `false` (default), Quality is absent from the settings panel.
 *                              The programmatic [BoomstreamPlayerController] API remains available
 *                              regardless of this flag.
 */
class AdvancedPlayerOptions @JvmOverloads constructor(
    val minBufferMs: Int = 15_000,
    val maxBufferMs: Int = 50_000,
    val bufferForPlaybackMs: Int = 2_500,
    val bufferForPlaybackAfterRebufferMs: Int = 5_000,
    val enableQualitySelector: Boolean = false,
)
