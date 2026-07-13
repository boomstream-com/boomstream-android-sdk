package com.boomstream.sdk.offline

/**
 * Typed exceptions thrown by [BoomstreamOfflineManager].
 */
sealed class BoomstreamOfflineException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** The config response did not include a decodable HLS manifest URL for [mediaCode]. */
    class NoHlsUrl(val mediaCode: String) :
        BoomstreamOfflineException("No HLS URL available for media code '$mediaCode'. " +
                "Check that the API key is valid and the media code exists.")

    /**
     * The download service returned a failure state.
     *
     * @param reason Message from the underlying Media3 [androidx.media3.exoplayer.offline.DownloadException].
     */
    class DownloadFailed(val reason: String) :
        BoomstreamOfflineException("Download failed: $reason")

    /** [BoomstreamOfflineManager.downloadVideoOffline] was cancelled before completing. */
    class Cancelled(val mediaCode: String) :
        BoomstreamOfflineException("Download for '$mediaCode' was cancelled.")
}
