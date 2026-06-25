package com.boomstream.sdk.offline

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.boomstream.sdk.api.BoomstreamConfigClient
import com.boomstream.sdk.api.model.mediaDataSingle
import com.boomstream.sdk.offline.internal.DownloadManagerProvider
import com.boomstream.sdk.offline.internal.OfflineLicenseHelper
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main entry point for Boomstream offline video functionality.
 *
 * Manages HLS downloads via Media3 [androidx.media3.exoplayer.offline.DownloadService] and
 * exposes a Kotlin-idiomatic [Flow]-based progress API.
 *
 * ## Setup
 *
 * Create one instance per [configClient] and reuse it throughout the app. Typically created
 * alongside the [com.boomstream.sdk.api.Boomstream] SDK instance:
 *
 * ```kotlin
 * class MyApp : Application() {
 *     val offlineManager by lazy {
 *         BoomstreamOfflineManager(
 *             context = this,
 *             configClient = Boomstream.configClient,
 *             userAgent = "BoomstreamSDK/1.0",
 *         )
 *     }
 * }
 * ```
 *
 * ## Offline playback
 *
 * After a download completes (or even during a partial download), pass [downloadCache] to
 * [com.boomstream.sdk.player.BoomstreamPlayer] via
 * [com.boomstream.sdk.player.BoomstreamOfflineCache]:
 *
 * ```kotlin
 * BoomstreamPlayer(
 *     mediaCode = "Il4lNOfL",
 *     configClient = Boomstream.configClient,
 *     offlineCache = BoomstreamOfflineCache { offlineManager.downloadCache },
 * )
 * ```
 *
 * The player automatically serves downloaded segments from local storage and falls back
 * to the streaming HLS URL for any segments not yet cached.
 *
 * ## CSO security constraints
 *
 * - **Constraint #2:** No HTTP-logging interceptor is registered anywhere in this module.
 *   See [com.boomstream.sdk.offline.internal.OfflineDataSourceFactory].
 * - **Constraint #3:** DRM keyset material (future scope) is stored via
 *   [com.boomstream.sdk.offline.internal.OfflineLicenseHelper] (EncryptedSharedPreferences).
 *
 * @param context Application context.
 * @param configClient [BoomstreamConfigClient] used to resolve HLS manifest URLs.
 * @param userAgent Custom `User-Agent` injected into segment download requests.
 *   The value may carry a media-server-key — **never log or expose it.**
 *   Defaults to `"BoomstreamSDK/1.0"`.
 * @param offlineConfig Storage and caching options. Defaults to [BoomstreamOfflineConfig]
 *   which uses [BoomstreamOfflineConfig.StorageLocation.INTERNAL] (app-scoped, protected
 *   from removable-SD extraction). Pass [BoomstreamOfflineConfig.StorageLocation.EXTERNAL]
 *   on low-end devices where internal partition space is the bottleneck.
 */
@OptIn(UnstableApi::class)
class BoomstreamOfflineManager(
    private val context: Context,
    private val configClient: BoomstreamConfigClient,
    private val userAgent: String = "BoomstreamSDK/1.0",
    private val offlineConfig: BoomstreamOfflineConfig = BoomstreamOfflineConfig(),
) {

    init {
        // Configure the DownloadManager singleton before any download is triggered.
        // Must happen before BoomstreamVideoDownloadService.getDownloadManager() is called.
        DownloadManagerProvider.configure(userAgent, offlineConfig.storageLocation)
    }

    private val licenseHelper: OfflineLicenseHelper by lazy {
        OfflineLicenseHelper(context.applicationContext)
    }

    private val downloadManager: DownloadManager
        get() = DownloadManagerProvider.getInstance(context.applicationContext)

    // Scope that hosts all shared upstream flows. Lives as long as this manager instance.
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One SharedFlow per mediaCode. computeIfAbsent is atomic, so parallel first-calls for
    // the same mediaCode produce exactly one upstream callbackFlow.
    private val downloadStateFlows = ConcurrentHashMap<String, SharedFlow<DownloadState>>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * The shared [androidx.media3.datasource.cache.SimpleCache] that stores downloaded HLS
     * segments.
     *
     * Pass this to [com.boomstream.sdk.player.BoomstreamPlayer] via
     * [com.boomstream.sdk.player.BoomstreamOfflineCache] to enable offline playback:
     *
     * ```kotlin
     * BoomstreamPlayer(
     *     mediaCode = "Il4lNOfL",
     *     configClient = Boomstream.configClient,
     *     offlineCache = BoomstreamOfflineCache { offlineManager.downloadCache },
     * )
     * ```
     *
     * The player wraps the data-source pipeline so cached segments are served locally and
     * uncached segments fall back to the streaming HLS URL automatically.
     */
    val downloadCache: androidx.media3.datasource.cache.SimpleCache
        get() = DownloadManagerProvider.getCache(context.applicationContext)

    /**
     * Downloads the video identified by [mediaCode] for offline playback.
     *
     * This is a **suspending** function that returns when the download either completes or
     * fails. Progress is reported via the [onProgress] callback **and** the
     * [getDownloadState] Flow (both may be observed simultaneously).
     *
     * The function resolves the HLS manifest URL from the Boomstream config API, then
     * delegates to [BoomstreamVideoDownloadService] for the actual segment download.
     *
     * **Cancellation:** if the calling coroutine is cancelled, the download continues in the
     * background service (it is not removed). Call [cancelDownload] or [deleteDownload] to
     * explicitly stop or remove it.
     *
     * @param mediaCode Boomstream media code (e.g. `"Il4lNOfL"`).
     * @param quality   Rendition quality hint. Currently only [DownloadQuality.AUTO] is supported.
     * @param onProgress Callback invoked with completion fraction `0.0..1.0` on each progress update.
     *                   Called on the calling coroutine's dispatcher.
     * @throws [BoomstreamOfflineException.NoHlsUrl] if the config response has no HLS URL.
     * @throws [BoomstreamOfflineException.DownloadFailed] if Media3 reports a download failure.
     * @throws [com.boomstream.sdk.api.error.BoomstreamApiError] if the config API call fails.
     */
    suspend fun downloadVideoOffline(
        mediaCode: String,
        quality: DownloadQuality = DownloadQuality.AUTO,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        // Step 1: Resolve the HLS manifest URL from the Boomstream config API.
        val config = configClient.getConfig(mediaCode).getOrThrow()
        val hlsUrl = config.mediaDataSingle?.links?.hlsUrl
            ?: throw BoomstreamOfflineException.NoHlsUrl(mediaCode)

        // Step 2: Queue the download via the DownloadService (thread-safe static call).
        val request = DownloadRequest.Builder(mediaCode, Uri.parse(hlsUrl)).build()
        DownloadService.sendAddDownload(
            context.applicationContext,
            BoomstreamVideoDownloadService::class.java,
            request,
            /* foreground = */ false,
        )

        // Step 3: Observe the download state until a terminal state is reached.
        // `transformWhile` emits while returning true and completes on false.
        var terminalException: BoomstreamOfflineException? = null
        getDownloadState(mediaCode)
            .transformWhile { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        withContext(Dispatchers.Main) { onProgress(state.progress) }
                        emit(state.progress)
                        true
                    }
                    is DownloadState.Completed -> {
                        withContext(Dispatchers.Main) { onProgress(1f) }
                        false
                    }
                    is DownloadState.Failed -> {
                        terminalException = BoomstreamOfflineException.DownloadFailed(state.message)
                        false
                    }
                    // Queued, Removing, NotDownloaded — keep waiting.
                    else -> true
                }
            }
            .collect { /* progress already reported via onProgress above */ }

        terminalException?.let { throw it }
    }

    /**
     * Stops the in-progress download for [mediaCode] without deleting already-downloaded data.
     *
     * The download can be resumed by calling [downloadVideoOffline] again with the same
     * [mediaCode]. The partial download is preserved in local storage.
     *
     * No-op if no download exists for [mediaCode].
     */
    fun cancelDownload(mediaCode: String) {
        // Stop reason 1 = user-requested stop (non-zero = stopped, 0 = not stopped).
        DownloadService.sendSetStopReason(
            context.applicationContext,
            BoomstreamVideoDownloadService::class.java,
            mediaCode,
            /* stopReason = */ 1,
            /* foreground = */ false,
        )
    }

    /**
     * Removes the downloaded content for [mediaCode] and deletes all local segment files.
     *
     * This also clears any DRM keyset stored for this media code (prepared for future DRM support).
     * No-op if no download exists for [mediaCode].
     */
    fun deleteDownload(mediaCode: String) {
        DownloadService.sendRemoveDownload(
            context.applicationContext,
            BoomstreamVideoDownloadService::class.java,
            mediaCode,
            /* foreground = */ false,
        )
        licenseHelper.removeKeyset(mediaCode)
    }

    /**
     * Removes **all** downloaded content and clears all DRM keysets.
     *
     * This is equivalent to calling [deleteDownload] for every downloaded media code.
     */
    fun deleteAllDownloads() {
        DownloadService.sendRemoveAllDownloads(
            context.applicationContext,
            BoomstreamVideoDownloadService::class.java,
            /* foreground = */ false,
        )
        licenseHelper.removeAllKeysets()
    }

    /**
     * Returns a [SharedFlow] that emits the current [DownloadState] for [mediaCode] and
     * continues to emit whenever the state changes.
     *
     * Multiple collectors on the same [mediaCode] share **one** upstream poll loop: the
     * 500 ms DB poll runs once regardless of how many UI widgets
     * subscribe simultaneously. The upstream starts on the first subscriber and stops
     * 5 seconds after the last subscriber disappears ([SharingStarted.WhileSubscribed]).
     * [replay] = 1 guarantees that a new collector immediately receives the latest state.
     *
     * The Flow emits [DownloadState.NotDownloaded] immediately if no download exists for
     * [mediaCode]. It does **not** complete automatically — collect it with
     * `launchIn(viewModelScope)` or a `lifecycleScope` to respect lifecycle boundaries.
     *
     * @param mediaCode Boomstream media code.
     */
    fun getDownloadState(mediaCode: String): Flow<DownloadState> =
        downloadStateFlows.computeIfAbsent(mediaCode) {
            pollDownloadState(mediaCode)
                .shareIn(managerScope, SharingStarted.WhileSubscribed(5_000L), replay = 1)
        }

    private fun pollDownloadState(mediaCode: String): Flow<DownloadState> = callbackFlow {
        val dm = DownloadManagerProvider.getInstance(context.applicationContext)

        // Emit current state immediately so collectors don't have to wait for the first event.
        val current = runCatching { dm.downloadIndex.getDownload(mediaCode) }.getOrNull()
        trySend(current?.toDownloadState() ?: DownloadState.NotDownloaded)

        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                if (download.request.id == mediaCode) {
                    trySend(download.toDownloadState(finalException))
                }
            }

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download,
            ) {
                if (download.request.id == mediaCode) {
                    trySend(DownloadState.NotDownloaded)
                }
            }
        }

        dm.addListener(listener)

        // DownloadManager.Listener fires on state transitions but not on every progress tick.
        // Short clips (<5 s) can go Queued → Completed before the first listener callback fires,
        // so the UI never sees Downloading states. Poll every 500 ms while actively downloading
        // to emit incremental Downloading(percent) values between listener events.
        val pollJob = launch(Dispatchers.IO) {
            while (true) {
                delay(500L)
                val dl = runCatching { dm.downloadIndex.getDownload(mediaCode) }.getOrNull()
                if (dl != null && dl.state == Download.STATE_DOWNLOADING) {
                    trySend(dl.toDownloadState())
                }
            }
        }

        awaitClose {
            pollJob.cancel()
            dm.removeListener(listener)
        }
    }
}

// ── Extension: Download → DownloadState ──────────────────────────────────────

/**
 * Maps a Media3 [Download] (and optional [finalException]) to the SDK's [DownloadState].
 *
 * [Download.percentDownloaded] is `0f` when the download has just been queued and `-1f`
 * for livestreams; we clamp both to `0f` in [DownloadState.Downloading].
 */
@OptIn(UnstableApi::class)
private fun Download.toDownloadState(finalException: Exception? = null): DownloadState =
    when {
        finalException != null || state == Download.STATE_FAILED ->
            DownloadState.Failed(finalException?.message ?: "Download failed")
        state == Download.STATE_DOWNLOADING ->
            DownloadState.Downloading((percentDownloaded / 100f).coerceIn(0f, 1f))
        state == Download.STATE_COMPLETED ->
            DownloadState.Completed
        state == Download.STATE_QUEUED || state == Download.STATE_RESTARTING ->
            DownloadState.Queued
        state == Download.STATE_REMOVING ->
            DownloadState.Removing
        state == Download.STATE_STOPPED ->
            // Stopped by user (cancelDownload) — treat as Queued so the UI can show a resume button.
            DownloadState.Queued
        else -> DownloadState.NotDownloaded
    }
