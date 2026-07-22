package com.boomstream.sdk.player

/**
 * Represents a video quality level selectable via [BoomstreamPlayerController.selectQuality].
 *
 * **CSO constraint #1:** this type is media3-free. Mapping to/from ExoPlayer
 * [androidx.media3.common.Format] and [androidx.media3.common.TrackSelectionParameters]
 * happens exclusively inside `player-sdk`'s `internal` package.
 */
sealed class VideoQuality {

    /** Adaptive quality — ExoPlayer picks the best rendition based on available bandwidth. */
    object Auto : VideoQuality()

    /**
     * Locked to a specific video resolution.
     *
     * @param height  Vertical resolution in pixels (e.g. 1080, 720, 480).
     * @param bitrate Peak bitrate of this rendition in bits/s, or -1 if unknown.
     * @param label   Human-readable label. Defaults to "${height}p".
     */
    data class Resolution(
        val height: Int,
        val bitrate: Long = -1L,
        val label: String = "${height}p",
    ) : VideoQuality()
}
