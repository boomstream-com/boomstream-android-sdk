package com.boomstream.sdk.offline

/**
 * Observable state of an offline download for a single media item.
 *
 * Emitted by [BoomstreamOfflineManager.getDownloadState] as a [kotlinx.coroutines.flow.Flow].
 */
sealed class DownloadState {

    /** Download has been queued but not yet started (or has been paused/stopped). */
    object Queued : DownloadState()

    /**
     * Download is actively in progress.
     *
     * @param progress Completion fraction in `0.0..1.0`.
     *                 May briefly report `0.0` at the start before the first progress update.
     */
    data class Downloading(val progress: Float) : DownloadState()

    /** All segments are downloaded and available for offline playback. */
    object Completed : DownloadState()

    /**
     * Download failed.
     *
     * @param message Human-readable reason. Do not show to end users without localisation.
     */
    data class Failed(val message: String) : DownloadState()

    /** Download is being removed from local storage. */
    object Removing : DownloadState()

    /** No download exists for this media code (never started, or already deleted). */
    object NotDownloaded : DownloadState()
}
