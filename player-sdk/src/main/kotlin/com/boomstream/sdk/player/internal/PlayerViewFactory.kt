package com.boomstream.sdk.player.internal

import android.content.Context
import android.view.LayoutInflater
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
 * The returned view has no `LayoutParams` (inflated with a `null` root); callers add it to their
 * container with explicit params.
 */
@OptIn(UnstableApi::class)
internal fun inflatePlayerView(context: Context, surfaceType: BoomstreamSurfaceType): PlayerView {
    val layoutRes = when (surfaceType) {
        BoomstreamSurfaceType.TEXTURE_VIEW -> R.layout.boomstream_internal_player_texture
        BoomstreamSurfaceType.SURFACE_VIEW -> R.layout.boomstream_internal_player_surface
    }
    return LayoutInflater.from(context).inflate(layoutRes, null, false) as PlayerView
}
