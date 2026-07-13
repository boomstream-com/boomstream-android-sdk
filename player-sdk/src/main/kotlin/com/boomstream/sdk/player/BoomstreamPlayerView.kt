package com.boomstream.sdk.player

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.PlayerView
import coil.load
import com.boomstream.sdk.api.BoomstreamConfigClient
import com.boomstream.sdk.api.internal.InternalBoomstreamApi
import com.boomstream.sdk.api.internal.UserAgentTokenProvider
import com.boomstream.sdk.player.internal.BoomstreamMediaPlayer
import com.boomstream.sdk.player.internal.applyLiveControllerUi
import com.boomstream.sdk.player.internal.inflatePlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Traditional `FrameLayout`-based Boomstream player for codebases not yet on Compose.
 *
 * Internally uses the same [com.boomstream.sdk.player.internal.BoomstreamMediaPlayer] engine as
 * the [BoomstreamPlayer] Composable — identical HLS playback, poster fallback, and playlist support.
 *
 * ## Basic usage (Kotlin)
 * ```kotlin
 * class PlayerActivity : AppCompatActivity(), DefaultLifecycleObserver {
 *     private val playerView: BoomstreamPlayerView by lazy {
 *         binding.boomstreamPlayer
 *     }
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         lifecycle.addObserver(playerView)
 *         playerView.load(
 *             mediaCode = "Il4lNOfL",
 *             configClient = Boomstream.configClient,
 *             allowClearKeyDRMtoken = BuildConfig.BOOMSTREAM_DRM_TOKEN.ifBlank { null },
 *         )
 *     }
 * }
 * ```
 *
 * **Important:** Call [release] in `onDestroy`, or register this view with
 * `lifecycle.addObserver(playerView)` — the [DefaultLifecycleObserver] implementation then
 * releases automatically on `ON_DESTROY`.
 *
 * ## CSO constraint #1
 * The internal `ExoPlayer` instance is NOT accessible from this class. Use [advancedOptions]
 * in [load] for permissible buffer tuning.
 *
 * **Unsupported view-tree introspection.** This class extends
 * [android.widget.FrameLayout] for XML-inflation compatibility, so the inherited
 * [android.view.ViewGroup.getChildAt], [android.view.ViewGroup.getChildCount], and related
 * `ViewGroup` accessors are technically reachable from integrator code. They are **not** part of
 * the supported Boomstream SDK API surface. The internal child layout — including any embedded
 * `androidx.media3.ui.PlayerView` — is an implementation detail and may change at any time
 * without notice across minor releases.
 *
 * Integrator code that traverses `getChildAt(...)`, casts to `androidx.media3.ui.PlayerView`, and
 * pulls `getPlayer()` to obtain a raw `ExoPlayer` reference is **explicitly out-of-contract**.
 * Defense-in-depth: the underlying [com.boomstream.sdk.player.internal.BoomstreamDataSourceFactory]
 * OkHttp client is constructor-immutable, so an escape obtained this way cannot retroactively
 * attach an `HttpLoggingInterceptor` to our segment-fetch path — the CSO constraint #2 leak
 * vector is closed at the OkHttp layer regardless. The CSO constraint #1 contract (no raw
 * `ExoPlayer` on the typed public surface) is enforced at the API-shape level by
 * `ConstraintOneReflectionTest` and `PublicApiReflectionTest`.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BoomstreamPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), DefaultLifecycleObserver {

    private var mediaPlayer: BoomstreamMediaPlayer? = null
    private var observerScope: CoroutineScope? = null

    // ── Surface backing ─────────────────────────────────────────────────────────

    // Initialised before [playerView] below so the first PlayerView is inflated with the right
    // surface_type. Property-initialiser order in Kotlin is top-to-bottom, so declaration order
    // here matters — keep _surfaceType above playerView.
    private var _surfaceType: BoomstreamSurfaceType = readSurfaceTypeAttr(context, attrs)

    /**
     * The view backing video rendering — [BoomstreamSurfaceType.SURFACE_VIEW] (default) or
     * [BoomstreamSurfaceType.TEXTURE_VIEW].
     *
     * Prefer setting this via the `boomstreamSurfaceType` XML attribute, or in code **before** the
     * first [load]. Assigning it later transparently recreates the internal `PlayerView` and
     * re-attaches the current player, but a switch mid-playback is briefly visible.
     *
     * Use [BoomstreamSurfaceType.TEXTURE_VIEW] to work around native codec crashes on device
     * rotation seen on some Android 16 hardware — see [BoomstreamSurfaceType] for the trade-offs.
     */
    var surfaceType: BoomstreamSurfaceType
        get() = _surfaceType
        set(value) {
            if (value == _surfaceType) return
            _surfaceType = value
            val previous = playerView
            val attachedPlayer = previous.player
            previous.player = null
            removeView(previous)
            val fresh = createPlayerView(value)
            playerView = fresh
            fresh.player = attachedPlayer
            // Restore the visibility that matches the current state on the fresh view.
            applyState(_stateFlow.value)
        }

    // ── Child views ────────────────────────────────────────────────────────────

    // `var` because [surfaceType] can recreate it. Always the bottom-most child (index 0) so the
    // poster / loading / message overlays stay on top.
    private var playerView: PlayerView = createPlayerView(_surfaceType)

    private val posterView: ImageView = ImageView(context).also { iv ->
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.visibility = View.GONE
        addView(iv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private val loadingView: ProgressBar = ProgressBar(context).also { pb ->
        pb.visibility = View.GONE
        addView(pb, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.CENTER
        })
    }

    private val messageView: TextView = TextView(context).also { tv ->
        tv.visibility = View.GONE
        tv.setTextColor(Color.WHITE)
        tv.setBackgroundColor(Color.argb(0xCC, 0, 0, 0))
        val pad = (12 * resources.displayMetrics.density).toInt()
        val padH = (16 * resources.displayMetrics.density).toInt()
        tv.setPadding(padH, pad, padH, pad)
        tv.gravity = Gravity.CENTER
        addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.TOP
        })
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private val _stateFlow = MutableStateFlow<PlayerState>(PlayerState.Idle)

    /**
     * Observable playback state. Collect in your lifecycle-aware UI layer (e.g. via
     * `lifecycleScope.launch { playerView.stateFlow.collect { … } }`).
     */
    val stateFlow: StateFlow<PlayerState> = _stateFlow

    // ── Events & progress (stable across load() calls) ─────────────────────────

    private val _events = MutableSharedFlow<PlayerEvent>(replay = 0, extraBufferCapacity = 16)
    private val _progressFlow = MutableStateFlow(PlaybackProgress(0L, -1L, 0f))
    private var _isFullScreen = false

    // Jobs forwarding from current mediaPlayer into the stable flows above.
    private var eventForwardJob: Job? = null
    private var progressForwardJob: Job? = null

    // ── Controller ─────────────────────────────────────────────────────────────

    /**
     * A stable [BoomstreamPlayerController] backed by this view.  Remains the same object across
     * [load] calls — safe to capture before calling [load].
     *
     * Control methods are no-ops when [load] has not been called yet.
     */
    val controller: BoomstreamPlayerController = object : BoomstreamPlayerController {
        override val events: SharedFlow<PlayerEvent> = _events
        override val progressFlow: StateFlow<PlaybackProgress> = _progressFlow
        override val state: StateFlow<PlayerState> = _stateFlow

        override fun getCurrentPosition(): Long = mediaPlayer?.getCurrentPosition() ?: 0L
        override fun getDuration(): Long = mediaPlayer?.getDuration() ?: -1L
        override fun play() { mediaPlayer?.play() }
        override fun pause() { mediaPlayer?.pause() }
        override fun seekTo(positionMs: Long) { mediaPlayer?.seekTo(positionMs) }
        override fun seekToPercent(percent: Float) { mediaPlayer?.seekToPercent(percent) }
        override fun setVolume(percent: Int) { mediaPlayer?.setVolume(percent) }
        override fun mute() { mediaPlayer?.mute() }
        override fun unmute() { mediaPlayer?.unmute() }
        override fun next() { mediaPlayer?.next() }
        override fun previous() { mediaPlayer?.previous() }
        override fun setFullScreen(isFullScreen: Boolean) {
            _isFullScreen = isFullScreen
            mediaPlayer?.setFullScreen(isFullScreen)
        }
        override fun toggleFullScreen() {
            setFullScreen(!_isFullScreen)
        }
    }

    /** Convenience proxy — equivalent to `controller.events`. */
    val events: SharedFlow<PlayerEvent> = _events

    /** Convenience proxy — equivalent to `controller.progressFlow`. */
    val progressFlow: StateFlow<PlaybackProgress> = _progressFlow

    // ── Convenience control methods ────────────────────────────────────────────

    /** @see BoomstreamPlayerController.play */
    fun play() = controller.play()

    /** @see BoomstreamPlayerController.pause */
    fun pause() = controller.pause()

    /** @see BoomstreamPlayerController.seekTo */
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    /** @see BoomstreamPlayerController.seekToPercent */
    fun seekToPercent(percent: Float) = controller.seekToPercent(percent)

    /** @see BoomstreamPlayerController.setVolume */
    fun setVolume(percent: Int) = controller.setVolume(percent)

    /** @see BoomstreamPlayerController.mute */
    fun mute() = controller.mute()

    /** @see BoomstreamPlayerController.unmute */
    fun unmute() = controller.unmute()

    /** @see BoomstreamPlayerController.next */
    fun next() = controller.next()

    /** @see BoomstreamPlayerController.previous */
    fun previous() = controller.previous()

    /** @see BoomstreamPlayerController.getCurrentPosition */
    fun getCurrentPosition(): Long = controller.getCurrentPosition()

    /** @see BoomstreamPlayerController.getDuration */
    fun getDuration(): Long = controller.getDuration()

    /**
     * Records the fullscreen state and emits [PlayerEvent.FullScreenChanged].
     * The SDK does not perform orientation or window-flag changes — the host Activity owns that.
     *
     * @see BoomstreamPlayerController.setFullScreen
     */
    fun setFullScreen(isFullScreen: Boolean) = controller.setFullScreen(isFullScreen)

    /**
     * Toggles the current fullscreen state and emits [PlayerEvent.FullScreenChanged].
     * @see BoomstreamPlayerController.toggleFullScreen
     */
    fun toggleFullScreen() = controller.toggleFullScreen()

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Starts loading the Boomstream media identified by [mediaCode].
     *
     * Any previously loaded media is released before the new load begins.
     *
     * @param mediaCode      Boomstream media code (e.g. `"Il4lNOfL"`).
     * @param configClient   [BoomstreamConfigClient] from [com.boomstream.sdk.api.Boomstream.configClient].
     * @param allowClearKeyDRMtoken Optional token appended to the SDK User-Agent for HLS segment
     *                       requests. When non-null, the header becomes
     *                       `Boomstream Android SDK v<version> <allowClearKeyDRMtoken>`.
     *                       The media server uses this token to enable native Clear Key DRM playback.
     *                       **Never log or expose this value.**
     *
     *                       Pass `null` (default) to fall back to
     *                       [com.boomstream.sdk.api.BoomstreamOptions.userAgentToken] if it was set
     *                       during [com.boomstream.sdk.api.Boomstream.init].  Pass a non-null value
     *                       only when you need a per-call override of that init-time token.
     * @param advancedOptions Buffer tuning (see [AdvancedPlayerOptions]).
     * @param offlineCache   Optional [BoomstreamOfflineCache] for offline playback.  When non-null,
     *                       HLS segments are served from the local cache when present and fetched
     *                       from the network otherwise.  Pass `null` (default) to stream only.
     * @param locale         BCP 47-style locale tag for system message localisation (`"ru"`, `"en"`, …).
     *                       When `null` (default) the server-provided fallback text is used.
     */
    @JvmOverloads
    fun load(
        mediaCode: String,
        configClient: BoomstreamConfigClient,
        allowClearKeyDRMtoken: String? = null,
        advancedOptions: AdvancedPlayerOptions = AdvancedPlayerOptions(),
        offlineCache: BoomstreamOfflineCache? = null,
        locale: String? = null,
    ) {
        releaseInternal()

        // Prefer the explicit per-call token; fall back to the token baked into the configClient
        // at init time (from BoomstreamOptions.userAgentToken) so callers using the new
        // single-call init pattern don't have to repeat the token here.
        @OptIn(InternalBoomstreamApi::class)
        val effectiveToken = allowClearKeyDRMtoken ?: (configClient as UserAgentTokenProvider).userAgentToken
        val player = BoomstreamMediaPlayer(context, effectiveToken, advancedOptions, offlineCache, locale)
        mediaPlayer = player
        playerView.player = player.exoPlayer

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        observerScope = scope

        // Forward state updates to this view's stable flow.
        scope.launch {
            player.stateFlow.collect { state ->
                _stateFlow.value = state
                applyState(state)
            }
        }

        // Forward events and progress from the new player into the stable flows.
        eventForwardJob = scope.launch { player.events.collect { _events.emit(it) } }
        progressForwardJob = scope.launch { player.progressFlow.collect { _progressFlow.value = it } }

        player.load(mediaCode, configClient)
    }

    /**
     * Releases the player and all associated resources.
     *
     * Safe to call multiple times. Called automatically on `ON_DESTROY` when this view is
     * registered as a `DefaultLifecycleObserver`.
     */
    fun release() {
        releaseInternal()
    }

    // ── DefaultLifecycleObserver ───────────────────────────────────────────────

    // Delegate to the media player so ExoPlayer pause/play AND poll lifecycle are both managed.
    override fun onStart(owner: LifecycleOwner) { mediaPlayer?.onStart(owner) }
    override fun onStop(owner: LifecycleOwner) { mediaPlayer?.onStop(owner) }
    override fun onDestroy(owner: LifecycleOwner) { releaseInternal() }

    // ── Internal ───────────────────────────────────────────────────────────────

    /**
     * Inflates a fresh [PlayerView] with the requested surface backing and adds it as the
     * bottom-most child (index 0) so the poster / loading / message overlays render above it.
     */
    private fun createPlayerView(type: BoomstreamSurfaceType): PlayerView =
        inflatePlayerView(context, type).also { pv ->
            pv.useController = true
            pv.visibility = View.GONE
            addView(pv, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

    /** Reads the `boomstreamSurfaceType` XML attribute; defaults to [BoomstreamSurfaceType.SURFACE_VIEW]. */
    private fun readSurfaceTypeAttr(context: Context, attrs: AttributeSet?): BoomstreamSurfaceType {
        attrs ?: return BoomstreamSurfaceType.SURFACE_VIEW
        val ta = context.obtainStyledAttributes(attrs, R.styleable.BoomstreamPlayerView)
        return try {
            when (ta.getInt(R.styleable.BoomstreamPlayerView_boomstreamSurfaceType, 0)) {
                1 -> BoomstreamSurfaceType.TEXTURE_VIEW
                else -> BoomstreamSurfaceType.SURFACE_VIEW
            }
        } finally {
            ta.recycle()
        }
    }

    private fun releaseInternal() {
        eventForwardJob?.cancel(); eventForwardJob = null
        progressForwardJob?.cancel(); progressForwardJob = null
        observerScope?.cancel()
        observerScope = null
        mediaPlayer?.release()
        mediaPlayer = null
        playerView.player = null
        _stateFlow.value = PlayerState.Idle
        _progressFlow.value = PlaybackProgress(0L, -1L, 0f)
    }

    private fun applyState(state: PlayerState) {
        when (state) {
            is PlayerState.Idle -> {
                loadingView.visibility = View.GONE
                playerView.visibility = View.GONE
                posterView.visibility = View.GONE
                messageView.visibility = View.GONE
            }
            is PlayerState.Loading -> {
                loadingView.visibility = View.VISIBLE
                playerView.visibility = View.GONE
                posterView.visibility = View.GONE
                messageView.visibility = View.GONE
            }
            is PlayerState.Ready -> {
                loadingView.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                posterView.visibility = View.GONE
                playerView.applyLiveControllerUi(state.isLive)
                playerView.setShowPreviousButton(state.navButtonsVisible())
                playerView.setShowNextButton(state.navButtonsVisible())
                if (state.systemMessage != null) {
                    messageView.text = state.systemMessage
                    messageView.visibility = View.VISIBLE
                    // Tap anywhere on the banner to dismiss it.
                    messageView.setOnClickListener { messageView.visibility = View.GONE }
                } else {
                    messageView.visibility = View.GONE
                    messageView.setOnClickListener(null)
                }
            }
            is PlayerState.Ended -> {
                loadingView.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                posterView.visibility = View.GONE
                messageView.visibility = View.GONE
                playerView.setShowPreviousButton(state.navButtonsVisible())
                playerView.setShowNextButton(state.navButtonsVisible())
            }
            is PlayerState.PosterOnly -> {
                loadingView.visibility = View.GONE
                playerView.visibility = View.GONE
                posterView.visibility = View.VISIBLE
                posterView.load(state.posterUrl)
                if (state.message != null) {
                    messageView.text = state.message
                    messageView.visibility = View.VISIBLE
                } else {
                    messageView.visibility = View.GONE
                }
            }
            is PlayerState.Error -> {
                loadingView.visibility = View.GONE
                playerView.visibility = View.GONE
                posterView.visibility = View.GONE
                messageView.visibility = View.GONE
            }
        }
    }

    // NOTE: we deliberately do NOT cancel [observerScope] in onDetachedFromWindow(). The scope's
    // lifetime is bound to load()/release()/ON_DESTROY only. A transient detach→reattach — as done
    // by Compose `AndroidView`, `ViewPager2`, or a rotation that rebuilds the view tree — must NOT
    // silently kill event/progress/state forwarding, which is what the old detach-cancel did.
}
