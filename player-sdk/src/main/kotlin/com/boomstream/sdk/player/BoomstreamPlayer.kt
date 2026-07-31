package com.boomstream.sdk.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.boomstream.sdk.api.BoomstreamConfigClient
import com.boomstream.sdk.api.internal.InternalBoomstreamApi
import com.boomstream.sdk.api.internal.UserAgentTokenProvider
import com.boomstream.sdk.player.internal.BoomstreamComposableController
import com.boomstream.sdk.player.internal.BoomstreamMediaPlayer
import com.boomstream.sdk.player.internal.CastSessionManager
import com.boomstream.sdk.player.internal.applyLiveControllerUi
import com.boomstream.sdk.player.internal.applyStyleToPlayerView
import com.boomstream.sdk.player.internal.inflatePlayerView
import com.boomstream.sdk.player.internal.interceptSettingsButton
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Creates and remembers a [BoomstreamPlayerController] for use with [BoomstreamPlayer].
 *
 * The returned controller is stable across recompositions.  Pass it to the `controller`
 * parameter of [BoomstreamPlayer] to wire it to the internal player engine:
 *
 * ```kotlin
 * val controller = rememberBoomstreamPlayerController()
 * BoomstreamPlayer(
 *     mediaCode = "Il4lNOfL",
 *     configClient = Boomstream.configClient,
 *     controller = controller,
 * )
 * val position by controller.progressFlow.collectAsState()
 * ```
 *
 * Control methods (play, pause, seekTo, …) are no-ops until the [BoomstreamPlayer] composable
 * is in the composition and has attached the controller.
 */
@Composable
fun rememberBoomstreamPlayerController(): BoomstreamPlayerController =
    remember { BoomstreamComposableController() }

/**
 * Composable Boomstream player that streams HLS content identified by [mediaCode],
 * with optional offline playback from a local Media3 download cache.
 *
 * ## Streaming (default)
 * ```kotlin
 * BoomstreamPlayer(
 *     mediaCode = "Il4lNOfL",
 *     configClient = Boomstream.configClient,
 *     modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
 *     allowClearKeyDRMtoken = BuildConfig.BOOMSTREAM_DRM_TOKEN.ifBlank { null },
 * )
 * ```
 *
 * ## Offline / fallback
 * Pass a [BoomstreamOfflineCache] (backed by `:offline-sdk`'s `BoomstreamOfflineManager`) to
 * play from locally cached segments when available, with automatic fallback to streaming:
 *
 * ```kotlin
 * BoomstreamPlayer(
 *     mediaCode = "Il4lNOfL",
 *     configClient = Boomstream.configClient,
 *     offlineCache = BoomstreamOfflineCache { offlineManager.downloadCache },
 * )
 * ```
 *
 * ## Controller
 * Obtain a [BoomstreamPlayerController] with [rememberBoomstreamPlayerController] and pass it
 * via `controller` to observe events and drive playback programmatically:
 *
 * ```kotlin
 * val ctrl = rememberBoomstreamPlayerController()
 * BoomstreamPlayer(mediaCode = "…", configClient = …, controller = ctrl)
 * LaunchedEffect(ctrl) {
 *     ctrl.events.collect { event ->
 *         if (event is PlayerEvent.Progress && event.percent >= 0.7f) triggerCampaign()
 *     }
 * }
 * ```
 *
 * ## Authentication
 * - **Authenticated:** renders a full `PlayerView` with HLS playback and native controls.
 * - **Unauthenticated:** renders the best-available poster image (Coil).
 *
 * ## Lifecycle
 * The player automatically pauses on `ON_STOP` and resumes on `ON_START` via the nearest
 * [LocalLifecycleOwner]. It is released when the Composable leaves the composition.
 *
 * ## CSO constraint #1
 * The internal `ExoPlayer` instance is **not** exposed from this Composable or any other public
 * class. Use [advancedOptions] for permissible buffer tuning.
 *
 * @param mediaCode      Boomstream media code (e.g. `"Il4lNOfL"`).
 * @param configClient   [BoomstreamConfigClient] from [com.boomstream.sdk.api.Boomstream.configClient].
 * @param modifier       Composable layout modifier.
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
 * @param offlineCache   Optional [BoomstreamOfflineCache] for offline playback.  When non-null,
 *                       HLS segments are served from the local cache when present and fetched
 *                       from the network otherwise.  Pass `null` (default) to stream only.
 * @param locale         BCP 47-style locale tag for system message localisation (`"ru"`, `"en"`, …).
 *                       When `null` (default) the server-provided `translate` fallback is used.
 *                       Changing the locale recreates the internal player and triggers a fresh config
 *                       fetch — suitable for in-demo locale switching.
 * @param advancedOptions Buffer tuning knobs (see [AdvancedPlayerOptions]).
 * @param surfaceType    Which view backs video rendering — [BoomstreamSurfaceType.SURFACE_VIEW]
 *                       (default) or [BoomstreamSurfaceType.TEXTURE_VIEW]. Pass
 *                       [BoomstreamSurfaceType.TEXTURE_VIEW] to work around native codec crashes on
 *                       device rotation seen on some Android 16 hardware. Read once when the player
 *                       surface is first created; not designed to change dynamically.
 * @param controller     Optional [BoomstreamPlayerController] obtained from
 *                       [rememberBoomstreamPlayerController]. When provided, the composable wires
 *                       the internal player to this controller, enabling programmatic control and
 *                       event observation.  Pass `null` (default) when no programmatic access is
 *                       needed.
 * @param onState        Called whenever [PlayerState] changes. Runs on the main thread.
 * @param style              Optional [BoomstreamPlayerStyle] for visual customisation. Affects the
 *                           loading spinner colour, seek bar colours, message overlay colours, and
 *                           (best-effort) accent tint on Media3 control buttons.
 *                           Pass `null` (default) to keep the SDK defaults.
 *                           Compose callers should convert [androidx.compose.ui.graphics.Color]
 *                           values via [androidx.compose.ui.graphics.Color.toArgb].
 * @param onFullscreenToggle Optional callback fired when the user taps the fullscreen button.
 *                           Implement to handle orientation / window changes in the host Activity.
 *                           Pass `null` (default) to hide the fullscreen button.
 */
@Composable
fun BoomstreamPlayer(
    mediaCode: String,
    configClient: BoomstreamConfigClient,
    modifier: Modifier = Modifier,
    allowClearKeyDRMtoken: String? = null,
    offlineCache: BoomstreamOfflineCache? = null,
    locale: String? = null,
    advancedOptions: AdvancedPlayerOptions = AdvancedPlayerOptions(),
    surfaceType: BoomstreamSurfaceType = BoomstreamSurfaceType.SURFACE_VIEW,
    style: BoomstreamPlayerStyle? = null,
    controller: BoomstreamPlayerController? = null,
    onState: (PlayerState) -> Unit = {},
    onFullscreenToggle: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Prefer the explicit per-call token; fall back to the token baked into the configClient
    // at init time (from BoomstreamOptions.userAgentToken) so callers using the new
    // single-call init pattern don't have to repeat the token here.
    @OptIn(InternalBoomstreamApi::class)
    val effectiveToken = allowClearKeyDRMtoken ?: (configClient as UserAgentTokenProvider).userAgentToken

    val player = remember(context, offlineCache, locale) {
        BoomstreamMediaPlayer(context, effectiveToken, advancedOptions, offlineCache, locale)
    }

    // Initialise CastContext once (idempotent singleton) so Cast discovery/sessions work. The
    // integrator still MUST wire their own MediaRouteButton with
    // CastButtonFactory.setUpMediaRouteButton(context, button) — initialising CastContext does
    // NOT configure the button (an unwired button shows "no devices"). If the host app has not
    // registered BoomstreamCastOptionsProvider in its manifest, getSharedInstance throws and we
    // degrade silently — Cast is simply unavailable for this session.
    val castContext = remember(context) {
        runCatching { CastContext.getSharedInstance(context) }.getOrNull()
    }

    // Attach a CastSessionManager whenever player or castContext change.
    // The player takes ownership of the manager's lifecycle (releases in player.release()).
    DisposableEffect(player, castContext) {
        if (castContext != null) {
            player.attachCast(CastSessionManager(castContext))
        }
        onDispose { }
    }

    // Track fullscreen state for toggling and event emission.
    var isFullScreen by remember { mutableStateOf(false) }

    // Wire external controller (if provided and is the SDK's own implementation).
    DisposableEffect(player, controller) {
        val composableController = controller as? BoomstreamComposableController
        if (composableController != null) {
            val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            composableController.attach(player, controllerScope)
            onDispose {
                composableController.detach()
                controllerScope.cancel()
            }
        } else {
            onDispose { }
        }
    }

    // Lifecycle: play on ON_START, pause on ON_STOP, release on destroy.
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(player)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(player)
        }
    }

    // Release the player when this Composable leaves the composition entirely.
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Reload whenever mediaCode changes.
    LaunchedEffect(mediaCode) {
        player.load(mediaCode, configClient)
    }

    val state by player.stateFlow.collectAsState()

    // Propagate state changes to the caller.
    LaunchedEffect(state) { onState(state) }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is PlayerState.Idle, is PlayerState.Loading -> {
                val spinnerColor = style?.loaderColor?.let { Color(it) } ?: Color.White
                CircularProgressIndicator(color = spinnerColor)
            }
            is PlayerState.Ready -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            inflatePlayerView(ctx, surfaceType).apply {
                                this.player = player.exoPlayer
                                useController = true
                                applyLiveControllerUi(s.isLive)
                                setShowPreviousButton(s.navButtonsVisible())
                                setShowNextButton(s.navButtonsVisible())
                                style?.let { applyStyleToPlayerView(this, it) }
                                setFullscreenButtonClickListener(
                                    if (onFullscreenToggle != null) {
                                        PlayerView.FullscreenButtonClickListener {
                                            isFullScreen = !isFullScreen
                                            player.setFullScreen(isFullScreen)
                                            onFullscreenToggle()
                                        }
                                    } else {
                                        null
                                    }
                                )
                                // Intercept the settings button to show our unified sheet.
                                interceptSettingsButton(
                                    playerProvider = { player },
                                    styleProvider = { style },
                                    enableQualitySelectorProvider = { advancedOptions.enableQualitySelector },
                                )
                            }
                        },
                        update = { playerView ->
                            // Re-attach after recomposition (e.g. theme change); player ref is stable.
                            if (playerView.player != player.exoPlayer) {
                                playerView.player = player.exoPlayer
                            }
                            // Update timebar and nav-button visibility on every state update (covers playlist item transitions).
                            playerView.applyLiveControllerUi(s.isLive)
                            playerView.setShowPreviousButton(s.navButtonsVisible())
                            playerView.setShowNextButton(s.navButtonsVisible())
                            // Re-apply style on each update (handles live style changes).
                            style?.let { applyStyleToPlayerView(playerView, it) }
                            playerView.setFullscreenButtonClickListener(
                                if (onFullscreenToggle != null) {
                                    PlayerView.FullscreenButtonClickListener {
                                        isFullScreen = !isFullScreen
                                        player.setFullScreen(isFullScreen)
                                        onFullscreenToggle()
                                    }
                                } else {
                                    null
                                }
                            )
                            // Re-wire settings interceptor on each update to pick up latest style.
                            playerView.interceptSettingsButton(
                                playerProvider = { player },
                                styleProvider = { style },
                                enableQualitySelectorProvider = { advancedOptions.enableQualitySelector },
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Dismissible system message overlay (e.g. "playing_record" banner).
                    // remember(key) resets visibility whenever the message text changes.
                    s.systemMessage?.let { msg ->
                        var visible by remember(msg) { mutableStateOf(true) }
                        if (visible) {
                            val msgTextColor = style?.messageTextColor?.let { Color(it) } ?: Color.White
                            val msgBgColor = style?.messageBackgroundColor?.let { Color(it) }
                                ?: Color(0xCC000000.toInt())
                            Text(
                                text = msg,
                                color = msgTextColor,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .background(msgBgColor)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .clickable { visible = false },
                            )
                        }
                    }

                }
            }
            is PlayerState.PosterOnly -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = s.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    s.message?.let { msg ->
                        val msgTextColor = style?.messageTextColor?.let { Color(it) } ?: Color.White
                        val msgBgColor = style?.messageBackgroundColor?.let { Color(it) }
                            ?: Color(0xCC000000.toInt())
                        Text(
                            text = msg,
                            color = msgTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .background(msgBgColor)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            is PlayerState.Error -> {
                Text(
                    text = s.message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is PlayerState.Ended -> {
                // Keep the last frame visible; host can react via onState to show replay UI.
                AndroidView(
                    factory = { ctx ->
                        inflatePlayerView(ctx, surfaceType).apply {
                            this.player = player.exoPlayer
                            useController = true
                            setShowPreviousButton(s.navButtonsVisible())
                            setShowNextButton(s.navButtonsVisible())
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ── Compose preview (does not load real content) ──────────────────────────────

@Preview(showBackground = true)
@Composable
private fun BoomstreamPlayerLoadingPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}
