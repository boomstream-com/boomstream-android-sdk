package com.boomstream.sdk.player.internal

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.boomstream.sdk.api.BoomstreamConfigClient
import com.boomstream.sdk.api.model.MediaData
import com.boomstream.sdk.api.model.effectivePosters
import com.boomstream.sdk.api.model.mediaDataPlaylist
import com.boomstream.sdk.api.model.mediaDataSingle
import com.boomstream.sdk.player.AdvancedPlayerOptions
import com.boomstream.sdk.player.BoomstreamOfflineCache
import com.boomstream.sdk.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Internal engine responsible for the ExoPlayer lifecycle, Boomstream config fetch, and HLS source
 * construction. One instance is created per [com.boomstream.sdk.player.BoomstreamPlayerView] or
 * per [com.boomstream.sdk.player.BoomstreamPlayer] Composable session.
 *
 * **CSO constraint #1:** [exoPlayer] is `internal` — it MUST NOT be promoted to the
 * public API surface of [com.boomstream.sdk.player.BoomstreamPlayer] or
 * [com.boomstream.sdk.player.BoomstreamPlayerView]. Exposure would let integrators bypass the SDK's
 * `BoomstreamDataSourceFactory` (losing User-Agent injection) and attach
 * `HttpLoggingInterceptor.Level.BODY` (UA/key leak to logcat).
 *
 * **Offline playback:** when [offlineCache] is non-null, the HLS media source is built with a
 * [CacheDataSource.Factory] wrapping [BoomstreamDataSourceFactory]. Segment reads are served from
 * the local [androidx.media3.datasource.cache.SimpleCache] when present and fetched via the
 * streaming HLS URL otherwise. Write-back during playback is disabled
 * (`setCacheWriteDataSinkFactory(null)`) — the download service owns cache population.
 *
 * **Offline-live recordings:** when a single live item is offline and its config
 * contains `records`, the SDK plays the records as a playlist with a dismissible `playing_record`
 * system message. When there are no records the player polls `getConfig` every 10 seconds (with
 * `forceRefresh=true`) and switches to live playback as soon as the broadcast comes online. The
 * poll is paused on `onStop` / `release` and resumed on `onStart`.
 *
 * **Must be created and accessed on the main thread** (ExoPlayer requirement).
 */
@OptIn(UnstableApi::class)
internal class BoomstreamMediaPlayer(
    context: Context,
    allowClearKeyDRMtoken: String?,
    advancedOptions: AdvancedPlayerOptions,
    private val offlineCache: BoomstreamOfflineCache? = null,
    /** BCP 47-style locale tag used to select system message translations ("ru", "en", …).
     *  `null` falls back to the server-supplied `translate` field. */
    private val locale: String? = null,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fetchJob: Job? = null

    // Config context retained so the polling loop and the source-lost recovery path can
    // call getConfig without the caller needing to pass them again.
    private var currentMediaCode: String? = null
    private var currentConfigClient: BoomstreamConfigClient? = null

    // Poll state — true while we are actively polling for the live broadcast to come online.
    // Survives onStop → onStart so the poll is resumed when the screen returns to foreground.
    private var isPollingMode = false
    private var pollJob: Job? = null

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val stateFlow: StateFlow<PlayerState> = _state

    // Playlist items stored so onMediaItemTransition can look up isLive per item.
    private var playlistItems: List<MediaData> = emptyList()

    private val networkDataSourceFactory = BoomstreamDataSourceFactory(allowClearKeyDRMtoken)

    /**
     * HLS media source factory. When [offlineCache] is provided, wraps the network factory with
     * [CacheDataSource.Factory] so ExoPlayer reads segments from the local cache first.
     * Write-back is disabled to avoid double-storing content already managed by the download
     * service.
     */
    private val hlsSourceFactory: HlsMediaSource.Factory = if (offlineCache != null) {
        val cacheFactory = CacheDataSource.Factory()
            .setCache(offlineCache.provideCache())
            .setUpstreamDataSourceFactory(networkDataSourceFactory)
            .setCacheWriteDataSinkFactory(null)
        HlsMediaSource.Factory(cacheFactory)
    } else {
        HlsMediaSource.Factory(networkDataSourceFactory)
    }

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            advancedOptions.minBufferMs,
            advancedOptions.maxBufferMs,
            advancedOptions.bufferForPlaybackMs,
            advancedOptions.bufferForPlaybackAfterRebufferMs,
        )
        .build()

    /**
     * The underlying ExoPlayer instance.
     *
     * **Internal use only** — passed to [androidx.media3.ui.PlayerView] inside the SDK's own UI
     * layer. Must NOT appear as a return type on any public class (CSO constraint #1).
     */
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(hlsSourceFactory)
        .build()
        .also { player ->
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) _state.value = PlayerState.Ended
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val current = _state.value
                    if (current is PlayerState.Ready && current.isPlaylist) {
                        val idx = player.currentMediaItemIndex
                        _state.value = current.copy(
                            title = mediaItem?.mediaMetadata?.title?.toString() ?: current.title,
                            playlistIndex = idx,
                            isLive = playlistItems.getOrNull(idx)?.isLive ?: false,
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause

                    // Case 3: live source became unavailable during
                    // playback. Refetch the config to decide whether to play records or poll.
                    val currentState = _state.value
                    if (currentState is PlayerState.Ready &&
                        currentState.isLive &&
                        !currentState.isPlaylist
                    ) {
                        val code = currentMediaCode
                        val client = currentConfigClient
                        if (code != null && client != null) {
                            fetchJob?.cancel()
                            fetchJob = scope.launch {
                                val result = client.getConfig(code, forceRefresh = true)
                                withContext(Dispatchers.Main) {
                                    result.fold(
                                        onSuccess = { config ->
                                            val media = config.mediaDataSingle ?: run {
                                                _state.value = PlayerState.Error(
                                                    error.message ?: "Playback error"
                                                )
                                                return@withContext
                                            }
                                            if (media.isLiveOffline) {
                                                val posterUrl = bestPosterUrl(
                                                    media.posters + config.effectivePosters
                                                )
                                                if (media.records.isNotEmpty()) {
                                                    playRecords(media.records, posterUrl)
                                                } else {
                                                    _state.value = PlayerState.PosterOnly(
                                                        posterUrl = posterUrl,
                                                        message = BoomstreamMessages.resolve(
                                                            "stream_offline", locale
                                                        ),
                                                    )
                                                    startConfigPolling()
                                                }
                                            } else {
                                                // Source came back during the refetch — restart live.
                                                val hlsUrl = media.links?.hlsUrl
                                                if (hlsUrl != null) {
                                                    exoPlayer.setMediaItem(
                                                        MediaItem.Builder()
                                                            .setUri(hlsUrl)
                                                            .setMediaMetadata(
                                                                MediaMetadata.Builder()
                                                                    .setTitle(media.title)
                                                                    .build()
                                                            )
                                                            .build()
                                                    )
                                                    exoPlayer.prepare()
                                                    exoPlayer.playWhenReady = true
                                                    _state.value = PlayerState.Ready(
                                                        title = media.title,
                                                        isPlaylist = false,
                                                        isLive = media.isLive,
                                                    )
                                                } else {
                                                    _state.value = PlayerState.Error(
                                                        error.message ?: "Playback error"
                                                    )
                                                }
                                            }
                                        },
                                        onFailure = {
                                            if (offlineCache != null && cause != null &&
                                                isNetworkError(cause)
                                            ) {
                                                _state.value = PlayerState.PosterOnly(
                                                    posterUrl = null,
                                                    message = BoomstreamMessages.resolve(
                                                        "no_network_offline", locale
                                                    ),
                                                )
                                            } else {
                                                _state.value = PlayerState.Error(
                                                    error.message ?: "Playback error"
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            return
                        }
                    }

                    // Default error handling for non-live or missing refetch context.
                    if (offlineCache != null && cause != null && isNetworkError(cause)) {
                        _state.value = PlayerState.PosterOnly(
                            posterUrl = null,
                            message = BoomstreamMessages.resolve("no_network_offline", locale),
                        )
                    } else {
                        _state.value = PlayerState.Error(error.message ?: "Playback error")
                    }
                }
            })
        }

    /**
     * Fetches the Boomstream config for [mediaCode] and starts playback (or shows a poster for
     * unauthenticated access). Cancels any in-flight fetch from a previous [load] call.
     */
    fun load(mediaCode: String, configClient: BoomstreamConfigClient) {
        fetchJob?.cancel()
        cancelPoll()
        isPollingMode = false
        currentMediaCode = mediaCode
        currentConfigClient = configClient
        _state.value = PlayerState.Loading
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        fetchJob = scope.launch {
            val result = configClient.getConfig(mediaCode)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { config ->
                        val accessRestricted = config.accessRestricted
                        if (accessRestricted != null) {
                            val msg = BoomstreamMessages.resolve(
                                key = accessRestricted.message,
                                locale = locale,
                                fallback = accessRestricted.translate.ifBlank { null },
                            )
                            _state.value = PlayerState.PosterOnly(
                                posterUrl = bestPosterUrl(config.effectivePosters),
                                message = msg,
                            )
                            return@withContext
                        }
                        when {
                            config.mediaType == "playlist" -> {
                                val items = config.mediaDataPlaylist ?: emptyList()
                                playlistItems = items
                                val playableItems = items.mapNotNull { media ->
                                    // Skip offline live items — not published or no active source.
                                    if (media.isLiveOffline) return@mapNotNull null
                                    val url = media.links?.hlsUrl ?: return@mapNotNull null
                                    MediaItem.Builder()
                                        .setUri(url)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder().setTitle(media.title).build()
                                        )
                                        .build()
                                }
                                if (playableItems.isNotEmpty()) {
                                    exoPlayer.setMediaItems(playableItems)
                                    exoPlayer.prepare()
                                    exoPlayer.playWhenReady = true
                                    _state.value = PlayerState.Ready(
                                        title = items.firstOrNull()?.title.orEmpty(),
                                        isPlaylist = true,
                                        playlistIndex = 0,
                                        playlistSize = playableItems.size,
                                        isLive = items.firstOrNull()?.isLive ?: false,
                                    )
                                } else {
                                    _state.value = PlayerState.PosterOnly(
                                        bestPosterUrl(config.effectivePosters)
                                    )
                                }
                            }
                            config.mediaDataSingle != null -> {
                                val media = requireNotNull(config.mediaDataSingle)
                                if (media.isLiveOffline) {
                                    val posterUrl = bestPosterUrl(
                                        media.posters + config.effectivePosters
                                    )
                                    if (media.records.isNotEmpty()) {
                                        // Play recordings with a dismissible "playing_record" notice.
                                        playRecords(media.records, posterUrl)
                                    } else {
                                        // No recordings: show offline poster and poll every 10 s.
                                        _state.value = PlayerState.PosterOnly(
                                            posterUrl = posterUrl,
                                            message = BoomstreamMessages.resolve(
                                                "stream_offline", locale
                                            ),
                                        )
                                        startConfigPolling()
                                    }
                                    return@withContext
                                }
                                val hlsUrl = media.links?.hlsUrl
                                if (hlsUrl != null) {
                                    exoPlayer.setMediaItem(
                                        MediaItem.Builder()
                                            .setUri(hlsUrl)
                                            .setMediaMetadata(
                                                MediaMetadata.Builder().setTitle(media.title).build()
                                            )
                                            .build()
                                    )
                                    exoPlayer.prepare()
                                    exoPlayer.playWhenReady = true
                                    _state.value = PlayerState.Ready(
                                        title = media.title,
                                        isPlaylist = false,
                                        isLive = media.isLive,
                                    )
                                } else {
                                    val posterUrl = bestPosterUrl(
                                        media.posters + config.effectivePosters
                                    )
                                    _state.value = PlayerState.PosterOnly(posterUrl)
                                }
                            }
                            else -> {
                                // Unauthenticated or unknown type — show best available poster.
                                _state.value = PlayerState.PosterOnly(
                                    bestPosterUrl(config.effectivePosters)
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        if (offlineCache != null && isNetworkError(error)) {
                            _state.value = PlayerState.PosterOnly(
                                posterUrl = null,
                                message = BoomstreamMessages.resolve("no_network_offline", locale),
                            )
                        } else {
                            _state.value = PlayerState.Error(
                                error.message ?: "Failed to load config for $mediaCode"
                            )
                        }
                    },
                )
            }
        }
    }

    /** Releases ExoPlayer and cancels pending coroutines. Safe to call multiple times. */
    fun release() {
        fetchJob?.cancel()
        cancelPoll()
        isPollingMode = false
        currentMediaCode = null
        currentConfigClient = null
        playlistItems = emptyList()
        exoPlayer.release()
        _state.value = PlayerState.Idle
    }

    // ── DefaultLifecycleObserver ──────────────────────────────────────────────

    override fun onStart(owner: LifecycleOwner) {
        exoPlayer.play()
        if (isPollingMode) startConfigPolling()
    }

    override fun onStop(owner: LifecycleOwner) {
        exoPlayer.pause()
        cancelPoll()  // pause poll; isPollingMode stays true so onStart restarts it
    }

    override fun onDestroy(owner: LifecycleOwner) { release() }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a playlist from [records] and starts playback. Emits [PlayerState.Ready] with a
     * dismissible `playing_record` system message overlay.
     *
     * If none of the records carry a usable HLS URL, falls back to the offline poster + poll.
     */
    private fun playRecords(records: List<MediaData>, fallbackPosterUrl: String?) {
        cancelPoll()
        isPollingMode = false
        playlistItems = records
        val mediaItems = records.mapNotNull { rec ->
            val url = rec.links?.hlsUrl ?: return@mapNotNull null
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(rec.title).build())
                .build()
        }
        if (mediaItems.isNotEmpty()) {
            exoPlayer.setMediaItems(mediaItems)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            _state.value = PlayerState.Ready(
                title = records.firstOrNull()?.title.orEmpty(),
                isPlaylist = true,
                playlistIndex = 0,
                playlistSize = mediaItems.size,
                isLive = false,
                systemMessage = BoomstreamMessages.resolve("playing_record", locale),
            )
        } else {
            // Records present but none have HLS URLs yet — treat as offline + poll.
            _state.value = PlayerState.PosterOnly(
                posterUrl = fallbackPosterUrl,
                message = BoomstreamMessages.resolve("stream_offline", locale),
            )
            startConfigPolling()
        }
    }

    /**
     * Starts a coroutine that polls `getConfig` every 10 seconds (with `forceRefresh=true`).
     * When the broadcast comes online, switches ExoPlayer to the live HLS URL and cancels the loop.
     *
     * The poll is stopped by [cancelPoll] (called from [onStop] and [release]) and restarted by
     * [onStart] when [isPollingMode] is still true (screen came back to foreground).
     */
    private fun startConfigPolling() {
        isPollingMode = true
        cancelPoll()
        val code = currentMediaCode ?: return
        val client = currentConfigClient ?: return
        pollJob = scope.launch {
            while (true) {
                delay(10_000L)
                val result = client.getConfig(code, forceRefresh = true)
                val config = result.getOrNull() ?: continue
                val media = config.mediaDataSingle ?: continue
                if (!media.isLiveOffline) {
                    val hlsUrl = media.links?.hlsUrl ?: continue
                    isPollingMode = false
                    exoPlayer.setMediaItem(
                        MediaItem.Builder()
                            .setUri(hlsUrl)
                            .setMediaMetadata(
                                MediaMetadata.Builder().setTitle(media.title).build()
                            )
                            .build()
                    )
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    _state.value = PlayerState.Ready(
                        title = media.title,
                        isPlaylist = false,
                        isLive = media.isLive,
                    )
                    break
                }
                // Still offline — loop continues with next delay.
            }
        }
    }

    /** Cancels any running poll coroutine. [isPollingMode] is left unchanged. */
    private fun cancelPoll() {
        pollJob?.cancel()
        pollJob = null
    }
}

/**
 * Returns the URL of the highest-resolution poster from [posters], or `null` if the list is empty.
 * Boomstream config typically includes several poster sizes; we pick the largest by pixel area.
 */
private fun bestPosterUrl(posters: List<com.boomstream.sdk.api.model.Poster>): String? =
    posters.maxByOrNull { it.width.toLong() * it.height.toLong() }?.link
