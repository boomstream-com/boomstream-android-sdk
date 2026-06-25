package com.boomstream.sdk.offline

/**
 * Quality hint for offline HLS downloads.
 *
 * Currently only [AUTO] is supported. Explicit rendition selection (via `HlsDownloadHelper`
 * track keys) is planned for a future release.
 */
enum class DownloadQuality {
    /**
     * Let Media3 select the default renditions from the HLS manifest.
     *
     * For most HLS streams this downloads the first (typically highest-quality) video
     * rendition plus all audio and subtitle tracks included by ExoPlayer's default track
     * selector. The exact set depends on the manifest's `#EXT-X-STREAM-INF` entries.
     */
    AUTO,
}
