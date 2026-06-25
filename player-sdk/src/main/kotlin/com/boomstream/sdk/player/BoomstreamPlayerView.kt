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
import com.boomstream.sdk.player.internal.BoomstreamMediaPlayer
import com.boomstream.sdk.player.internal.applyLiveControllerUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
 *             allowClearKeyDRMtoken = BuildConfig.ALLOWED_UA_KEY_TO_PLAY_AES.ifBlank { null },
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

    // ── Child views ────────────────────────────────────────────────────────────

    private val playerView: PlayerView = PlayerView(context).also { pv ->
        pv.useController = true
        pv.visibility = View.GONE
        addView(pv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

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

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Starts loading the Boomstream media identified by [mediaCode].
     *
     * Any previously loaded media is released before the new load begins.
     *
     * @param mediaCode      Boomstream media code (e.g. `"Il4lNOfL"`).
     * @param configClient   [BoomstreamConfigClient] from [com.boomstream.sdk.api.Boomstream.configClient].
     * @param allowClearKeyDRMtoken Optional token appended to the SDK User-Agent for HLS segment requests.
     *                       When non-null, the header becomes
     *                       `Boomstream Android SDK v<version> <allowClearKeyDRMtoken>`.
     *                       The media server uses this token to enable native Clear Key DRM playback.
     *                       **Never log or expose this value.**  Pass `null` (default) for plain playback.
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

        val player = BoomstreamMediaPlayer(context, allowClearKeyDRMtoken, advancedOptions, offlineCache, locale)
        mediaPlayer = player
        playerView.player = player.exoPlayer

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        observerScope = scope
        scope.launch {
            player.stateFlow.collect { state ->
                _stateFlow.value = state
                applyState(state)
            }
        }

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

    private fun releaseInternal() {
        observerScope?.cancel()
        observerScope = null
        mediaPlayer?.release()
        mediaPlayer = null
        playerView.player = null
        _stateFlow.value = PlayerState.Idle
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        observerScope?.cancel()
    }
}
