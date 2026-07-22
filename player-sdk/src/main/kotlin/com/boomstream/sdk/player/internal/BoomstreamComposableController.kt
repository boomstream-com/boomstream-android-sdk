package com.boomstream.sdk.player.internal

import com.boomstream.sdk.player.BoomstreamPlayerController
import com.boomstream.sdk.player.PlaybackProgress
import com.boomstream.sdk.player.PlayerEvent
import com.boomstream.sdk.player.PlayerState
import com.boomstream.sdk.player.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Stable [BoomstreamPlayerController] implementation for the Compose path.
 *
 * Returned by `rememberBoomstreamPlayerController()` and attached to the internal
 * [BoomstreamMediaPlayer] by the [com.boomstream.sdk.player.BoomstreamPlayer] composable when
 * a controller reference is passed via the `controller` parameter.
 *
 * The instance is stable across recompositions; it owns its own hot flows and forwards events
 * from the attached player.  When no player is attached, control methods are no-ops and
 * `progressFlow` holds a zeroed [PlaybackProgress].
 */
internal class BoomstreamComposableController : BoomstreamPlayerController {

    private val _events = MutableSharedFlow<PlayerEvent>(replay = 0, extraBufferCapacity = 16)
    private val _progressFlow = MutableStateFlow(PlaybackProgress(0L, -1L, 0f))
    private val _stateFlow = MutableStateFlow<PlayerState>(PlayerState.Idle)
    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    private val _currentQuality = MutableStateFlow<VideoQuality>(VideoQuality.Auto)

    override val events: SharedFlow<PlayerEvent> = _events
    override val progressFlow: StateFlow<PlaybackProgress> = _progressFlow
    override val state: StateFlow<PlayerState> = _stateFlow
    override val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities
    override val currentQuality: StateFlow<VideoQuality> = _currentQuality

    private var delegate: BoomstreamMediaPlayer? = null
    private val forwardJobs = mutableListOf<Job>()

    internal fun attach(player: BoomstreamMediaPlayer, scope: CoroutineScope) {
        detach()
        delegate = player
        forwardJobs += scope.launch { player.events.collect { _events.emit(it) } }
        forwardJobs += scope.launch { player.progressFlow.collect { _progressFlow.value = it } }
        forwardJobs += scope.launch { player.stateFlow.collect { _stateFlow.value = it } }
        forwardJobs += scope.launch { player.availableQualities.collect { _availableQualities.value = it } }
        forwardJobs += scope.launch { player.currentQuality.collect { _currentQuality.value = it } }
    }

    internal fun detach() {
        forwardJobs.forEach { it.cancel() }
        forwardJobs.clear()
        delegate = null
        _progressFlow.value = PlaybackProgress(0L, -1L, 0f)
        _availableQualities.value = emptyList()
        _currentQuality.value = VideoQuality.Auto
    }

    override fun getCurrentPosition(): Long = delegate?.getCurrentPosition() ?: 0L
    override fun getDuration(): Long = delegate?.getDuration() ?: -1L
    override fun play() { delegate?.play() }
    override fun pause() { delegate?.pause() }
    override fun seekTo(positionMs: Long) { delegate?.seekTo(positionMs) }
    override fun seekToPercent(percent: Float) { delegate?.seekToPercent(percent) }
    override fun setVolume(percent: Int) { delegate?.setVolume(percent) }
    override fun mute() { delegate?.mute() }
    override fun unmute() { delegate?.unmute() }
    override fun next() { delegate?.next() }
    override fun previous() { delegate?.previous() }
    override fun setFullScreen(isFullScreen: Boolean) { delegate?.setFullScreen(isFullScreen) }
    override fun toggleFullScreen() { delegate?.toggleFullScreen() }
    override fun selectQuality(quality: VideoQuality) { delegate?.selectQuality(quality) }
    override fun selectAuto() { delegate?.selectAuto() }
}
