package com.boomstream.sdk.player.internal

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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
import com.boomstream.sdk.player.PlaybackProgress
import com.boomstream.sdk.player.PlayerEvent
import com.boomstream.sdk.player.PlayerState
import com.boomstream.sdk.player.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    internal val locale: String? = null,
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

    // ── Events & progress (new public API) ───────────────────────────────────

    private val _events = MutableSharedFlow<PlayerEvent>(replay = 0, extraBufferCapacity = 16)
    internal val events: SharedFlow<PlayerEvent> = _events

    private val _progressFlow = MutableStateFlow(PlaybackProgress(0L, -1L, 0f))
    internal val progressFlow: StateFlow<PlaybackProgress> = _progressFlow

    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    internal val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities

    private val _currentQuality = MutableStateFlow<VideoQuality>(VideoQuality.Auto)
    internal val currentQuality: StateFlow<VideoQuality> = _currentQuality

    // ── Playback speed ────────────────────────────────────────────────────────
    private val _playbackSpeed = MutableStateFlow(1.0f)
    internal val playbackSpeed: StateFlow<Float> = _playbackSpeed

    // ── Audio tracks (HLS multi-audio) ────────────────────────────────────────
    private val _availableAudioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    internal val availableAudioTracks: StateFlow<List<AudioTrackInfo>> = _availableAudioTracks

    private val _currentAudioTrack = MutableStateFlow<AudioTrackInfo?>(null)
    internal val currentAudioTrack: StateFlow<AudioTrackInfo?> = _currentAudioTrack

    // ── Subtitle tracks (HLS WEBVTT text tracks) ──────────────────────────────
    private val _availableSubtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    internal val availableSubtitleTracks: StateFlow<List<SubtitleTrackInfo>> = _availableSubtitleTracks

    private val _currentSubtitleTrack = MutableStateFlow<SubtitleTrackInfo?>(null)
    internal val currentSubtitleTrack: StateFlow<SubtitleTrackInfo?> = _currentSubtitleTrack

    private var lastKnownTracks: Tracks? = null

    private var progressJob: Job? = null
    private var _isFullScreen = false

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
            // Listener 1: existing state-machine + live-recovery logic (unchanged).
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

            // Listener 2: event emitter for the new public PlayerEvent API.
            player.addListener(
                PlayerEventEmitter(
                    events = _events,
                    currentPositionMs = { player.currentPosition },
                    durationMs = { safeDuration(player.duration) },
                    playbackState = { player.playbackState },
                )
            )

            // Listener 3: track-change detector — populates qualities, audio, and subtitle tracks.
            player.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    lastKnownTracks = tracks
                    _availableQualities.value = buildQualityList(tracks)
                    val audioList = buildAudioTrackList(tracks)
                    _availableAudioTracks.value = audioList
                    _currentAudioTrack.value = detectCurrentAudioTrack(tracks, audioList)
                    val subtitleList = buildSubtitleTrackList(tracks)
                    _availableSubtitleTracks.value = subtitleList
                    _currentSubtitleTrack.value = detectCurrentSubtitleTrack(tracks, subtitleList)
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
        // Reset quality, speed, audio, and subtitle track state for the new media item.
        _availableQualities.value = emptyList()
        _currentQuality.value = VideoQuality.Auto
        _availableAudioTracks.value = emptyList()
        _currentAudioTrack.value = null
        _availableSubtitleTracks.value = emptyList()
        _currentSubtitleTrack.value = null
        lastKnownTracks = null
        _playbackSpeed.value = 1.0f
        exoPlayer.setPlaybackSpeed(1.0f)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        startProgressPolling()
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
        progressJob?.cancel()
        progressJob = null
        isPollingMode = false
        currentMediaCode = null
        currentConfigClient = null
        playlistItems = emptyList()
        _availableQualities.value = emptyList()
        _currentQuality.value = VideoQuality.Auto
        _availableAudioTracks.value = emptyList()
        _currentAudioTrack.value = null
        _availableSubtitleTracks.value = emptyList()
        _currentSubtitleTrack.value = null
        lastKnownTracks = null
        _playbackSpeed.value = 1.0f
        exoPlayer.release()
        _state.value = PlayerState.Idle
        _progressFlow.value = PlaybackProgress(0L, -1L, 0f)
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

    // ── Public control methods ────────────────────────────────────────────────

    internal fun getCurrentPosition(): Long = exoPlayer.currentPosition

    internal fun getDuration(): Long = safeDuration(exoPlayer.duration)

    internal fun play() { exoPlayer.play() }

    internal fun pause() { exoPlayer.pause() }

    internal fun seekTo(positionMs: Long) { exoPlayer.seekTo(positionMs) }

    internal fun seekToPercent(percent: Float) {
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET || duration <= 0L) {
            android.util.Log.w("BoomstreamPlayer", "seekToPercent: duration unknown, ignoring seek")
            return
        }
        val positionMs = (percent.coerceIn(0f, 1f) * duration).toLong()
        exoPlayer.seekTo(positionMs)
    }

    internal fun setVolume(percent: Int) {
        exoPlayer.volume = percent.coerceIn(0, 100) / 100f
    }

    internal fun mute() { setVolume(0) }

    internal fun unmute() { setVolume(100) }

    internal fun next() {
        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
    }

    internal fun previous() {
        if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem()
    }

    internal fun setFullScreen(isFullScreen: Boolean) {
        _isFullScreen = isFullScreen
        _events.tryEmit(PlayerEvent.FullScreenChanged(isFullScreen))
    }

    internal fun toggleFullScreen() {
        setFullScreen(!_isFullScreen)
    }

    /**
     * Locks video rendition to [quality]. Mutates [ExoPlayer.trackSelectionParameters] so
     * ExoPlayer adapts to the closest track ≤ the requested height without reload.
     * For [VideoQuality.Auto] clears all video-size / bitrate constraints.
     */
    internal fun selectQuality(quality: VideoQuality) {
        val params = when (quality) {
            is VideoQuality.Auto -> exoPlayer.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .build()
            is VideoQuality.Resolution -> exoPlayer.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, quality.height)
                .also { b -> if (quality.bitrate > 0) b.setMaxVideoBitrate(quality.bitrate.toInt()) }
                .build()
        }
        exoPlayer.trackSelectionParameters = params
        _currentQuality.value = quality
        _events.tryEmit(PlayerEvent.QualityChanged(quality))
    }

    internal fun selectAuto() = selectQuality(VideoQuality.Auto)

    /** Sets ExoPlayer playback speed. [speed] is clamped to [0.25, 2.0]. */
    internal fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 2.0f)
        exoPlayer.setPlaybackSpeed(clamped)
        _playbackSpeed.value = clamped
    }

    /**
     * Forces playback of the given [AudioTrackInfo]. Clears any previous audio override first.
     * No-op if the corresponding track group is no longer present in [lastKnownTracks].
     */
    internal fun selectAudioTrack(info: AudioTrackInfo) {
        val tracks = lastKnownTracks ?: return
        val group = tracks.groups.getOrNull(info.groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(info.trackIndex))
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(override)
            .build()
        _currentAudioTrack.value = info
    }

    /**
     * Forces playback of the given [SubtitleTrackInfo]. Clears any previous text override first.
     * No-op if the corresponding track group is no longer present in [lastKnownTracks].
     */
    internal fun selectSubtitleTrack(info: SubtitleTrackInfo) {
        val tracks = lastKnownTracks ?: return
        val group = tracks.groups.getOrNull(info.groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(info.trackIndex))
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .addOverride(override)
            .build()
        _currentSubtitleTrack.value = info
    }

    /** Clears any active subtitle override, disabling subtitle display. */
    internal fun clearSubtitleTrack() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        _currentSubtitleTrack.value = null
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Converts ExoPlayer's `C.TIME_UNSET` sentinel to `-1L` for the public API contract. */
    private fun safeDuration(rawDuration: Long): Long =
        if (rawDuration == C.TIME_UNSET) -1L else rawDuration

    /**
     * Starts the progress-polling coroutine if not already running.  The loop runs
     * continuously but skips emission when the player is paused — no cancel/recreate
     * overhead on every pause/resume.
     */
    private fun startProgressPolling() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            progressPollLoop(
                intervalMs = 300L,
                isPlaying = { exoPlayer.isPlaying },
                currentPositionMs = { exoPlayer.currentPosition },
                durationMs = { safeDuration(exoPlayer.duration) },
                onProgress = { _progressFlow.value = it },
            )
        }
    }

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

/**
 * Maps all video renditions in [tracks] to [VideoQuality.Resolution] entries, de-duped by height
 * and sorted highest-resolution first. Returns an empty list when no video tracks are available.
 *
 * Only renditions with a known height (> 0) are included. Renditions that share the same pixel
 * height are collapsed to one entry (keeping the first seen, which is typically the lower-bitrate
 * variant for identical-height codec ladders).
 */
/**
 * Audio track descriptor used by [BoomstreamSettingsSheet].
 *
 * Holds the [Tracks] indices so [BoomstreamMediaPlayer.selectAudioTrack] can build a
 * [TrackSelectionOverride] without re-scanning the track list.
 */
internal data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
)

/** Builds a flat list of all audio renditions found in [tracks]. */
private fun buildAudioTrackList(tracks: Tracks): List<AudioTrackInfo> {
    val result = mutableListOf<AudioTrackInfo>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            val label = format.label?.takeIf { it.isNotBlank() }
                ?: format.language?.uppercase()
                ?: "Audio ${result.size + 1}"
            result.add(AudioTrackInfo(groupIndex, trackIndex, label))
        }
    }
    return result
}

/** Returns the [AudioTrackInfo] whose track is currently selected, or the first entry when none match. */
private fun detectCurrentAudioTrack(tracks: Tracks, audioList: List<AudioTrackInfo>): AudioTrackInfo? {
    if (audioList.isEmpty()) return null
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (group.isTrackSelected(trackIndex)) {
                return audioList.find { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
            }
        }
    }
    return audioList.first()
}

private fun buildQualityList(tracks: Tracks): List<VideoQuality.Resolution> {
    val seen = mutableSetOf<Int>()
    return tracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
            (0 until group.length).mapNotNull { i ->
                val format = group.getTrackFormat(i)
                val height = format.height
                if (height <= 0 || !seen.add(height)) return@mapNotNull null
                val bitrate = format.bitrate
                val bitrateL = if (bitrate == Format.NO_VALUE) -1L else bitrate.toLong()
                VideoQuality.Resolution(height = height, bitrate = bitrateL)
            }
        }
        .sortedByDescending { it.height }
}

/**
 * Subtitle (text-track) descriptor used by [BoomstreamSettingsSheet].
 *
 * Holds the [Tracks] indices so [BoomstreamMediaPlayer.selectSubtitleTrack] can build a
 * [TrackSelectionOverride] without re-scanning the track list.
 */
internal data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
)

// CEA-608/CEA-708 are closed-caption tracks that ExoPlayer surfaces automatically from
// muxed HLS streams. They carry no displayable subtitles and must not appear in the UI.
private val PHANTOM_CAPTION_MIME_TYPES = setOf(
    MimeTypes.APPLICATION_CEA608,
    MimeTypes.APPLICATION_CEA708,
)

/** Builds a flat list of real subtitle renditions found in [tracks] (TRACK_TYPE_TEXT),
 * excluding CEA-608/CEA-708 closed-caption tracks injected by ExoPlayer from muxed HLS. */
internal fun buildSubtitleTrackList(tracks: Tracks): List<SubtitleTrackInfo> {
    val result = mutableListOf<SubtitleTrackInfo>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            if (format.sampleMimeType in PHANTOM_CAPTION_MIME_TYPES) continue
            if (!group.isTrackSupported(trackIndex)) continue
            val label = format.label?.takeIf { it.isNotBlank() }
                ?: format.language?.uppercase()
                ?: "Subtitle ${result.size + 1}"
            result.add(SubtitleTrackInfo(groupIndex, trackIndex, label))
        }
    }
    return result
}

/**
 * Returns the [SubtitleTrackInfo] whose track is currently selected, or `null` when no text
 * track is actively selected (i.e. subtitles are off).
 */
internal fun detectCurrentSubtitleTrack(tracks: Tracks, subtitleList: List<SubtitleTrackInfo>): SubtitleTrackInfo? {
    if (subtitleList.isEmpty()) return null
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (group.isTrackSelected(trackIndex)) {
                return subtitleList.find { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
            }
        }
    }
    return null
}
