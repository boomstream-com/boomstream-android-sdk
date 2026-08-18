package com.boomstream.sdk.player.internal

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.boomstream.sdk.player.BoomstreamSurfaceType
import com.boomstream.sdk.player.R

/**
 * Inflates a Media3 [PlayerView] with the requested surface backing.
 *
 * The surface type (`SurfaceView` vs `TextureView`) can only be chosen at inflation time via the
 * `app:surface_type` XML attribute — there is no runtime setter — so each variant lives in its own
 * one-line layout and we inflate the matching one.
 *
 * When [secure] is `true` the video surface is marked secure (see [applyVideoSurfaceSecure]) — used
 * for encrypted content so screen capture of the video frame is blocked. Set it at inflation time
 * so the flag lands before the surface is created.
 *
 * The returned view has no `LayoutParams` (inflated with a `null` root); callers add it to their
 * container with explicit params.
 */
@OptIn(UnstableApi::class)
internal fun inflatePlayerView(
    context: Context,
    surfaceType: BoomstreamSurfaceType,
    secure: Boolean = false,
): PlayerView {
    val layoutRes = when (surfaceType) {
        BoomstreamSurfaceType.TEXTURE_VIEW -> R.layout.boomstream_internal_player_texture
        BoomstreamSurfaceType.SURFACE_VIEW -> R.layout.boomstream_internal_player_surface
    }
    val playerView = LayoutInflater.from(context).inflate(layoutRes, null, false) as PlayerView
    if (secure) applyVideoSurfaceSecure(playerView)
    return playerView
}

/**
 * Marks ONLY the video surface secure (`SurfaceView.setSecure(true)`), the way the Android system
 * video player does: screenshots and screen recording render black over the video frame, while the
 * host app's own UI stays capturable. This is a software block (not the hardware secure pipeline —
 * that needs Widevine L1); it applies regardless of the encryption scheme.
 *
 * Must be called before the [SurfaceView]'s surface is created (inflation time, or while the view
 * is not yet attached / `GONE`) to take effect. A `TextureView` cannot be made secure, so callers
 * that need capture protection must use [BoomstreamSurfaceType.SURFACE_VIEW].
 */
@OptIn(UnstableApi::class)
internal fun applyVideoSurfaceSecure(playerView: PlayerView) {
    when (val surface = playerView.videoSurfaceView) {
        is SurfaceView -> surface.setSecure(true)
        else -> Log.w(
            "BoomstreamPlayer",
            "Encrypted content on a TextureView surface cannot be marked secure; use " +
                "BoomstreamSurfaceType.SURFACE_VIEW to block screen capture of the video.",
        )
    }
}
