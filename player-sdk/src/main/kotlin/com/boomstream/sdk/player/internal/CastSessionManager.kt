package com.boomstream.sdk.player.internal

import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the Media3 [CastPlayer] lifecycle and tracks Cast session availability.
 *
 * Created once per player instance when a valid [CastContext] is present (i.e. the host app
 * registered `BoomstreamCastOptionsProvider` in its manifest). [BoomstreamMediaPlayer.attachCast]
 * wires the [onSessionAvailable] / [onSessionUnavailable] callbacks that perform the
 * ExoPlayer ↔ CastPlayer handoff.
 *
 * **Internal only** — [CastPlayer] must not appear on the public API surface (CSO constraint #1).
 *
 * Thread-safety: all methods must be called from the main thread (mirrors the Media3 / Cast SDK
 * threading contract). [isCasting] and [castDeviceName] are safe to collect from any thread.
 */
@OptIn(UnstableApi::class)
internal class CastSessionManager(
    castContext: CastContext,
) : SessionAvailabilityListener {

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName

    /** Called when a Cast session becomes available. Set by [BoomstreamMediaPlayer.attachCast]. */
    var onSessionAvailable: (() -> Unit)? = null

    /** Called when the Cast session ends. Carries the last known CastPlayer position. */
    var onSessionUnavailable: ((positionMs: Long) -> Unit)? = null

    private val gmsSessionManager = castContext.sessionManager

    /** Internal [CastPlayer] — must stay in this package. */
    val castPlayer: CastPlayer = CastPlayer(castContext).also {
        it.setSessionAvailabilityListener(this)
    }

    // ── SessionAvailabilityListener ──────────────────────────────────────────

    override fun onCastSessionAvailable() {
        _isCasting.value = true
        _castDeviceName.value = gmsSessionManager.currentCastSession?.castDevice?.friendlyName
        onSessionAvailable?.invoke()
    }

    override fun onCastSessionUnavailable() {
        val positionMs = castPlayer.currentPosition
        _isCasting.value = false
        _castDeviceName.value = null
        onSessionUnavailable?.invoke(positionMs)
    }

    // ── Playback control ─────────────────────────────────────────────────────

    /**
     * Loads [mediaItem] into [castPlayer] starting at [startPositionMs] and begins playback.
     *
     * The HLS URI in [mediaItem] must have its MIME type set to `application/x-mpegURL` so the
     * default Cast receiver can identify the stream without probing.
     */
    fun loadMedia(mediaItem: MediaItem, startPositionMs: Long) {
        castPlayer.setMediaItem(mediaItem, startPositionMs)
        castPlayer.prepare()
        castPlayer.playWhenReady = true
    }

    fun release() {
        castPlayer.setSessionAvailabilityListener(null)
        castPlayer.release()
    }
}
