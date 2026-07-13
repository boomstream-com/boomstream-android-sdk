package com.boomstream.sdk.player

/**
 * Selects the Android view that the SDK renders video into.
 *
 * The underlying `androidx.media3.ui.PlayerView` can be backed either by a `SurfaceView`
 * (the default) or a `TextureView`. The choice affects how playback survives configuration
 * changes such as device rotation.
 *
 * ## Which one to use
 *
 * - [SURFACE_VIEW] — the default. A dedicated hardware compositing layer: lowest power draw,
 *   best for HDR and DRM-secured surfaces. On rotation (or any relayout) the underlying
 *   `Surface` is **destroyed and recreated**, and Media3 re-attaches the running `MediaCodec`
 *   to the new surface. On a small number of vendor codec HALs (observed on some Android 16
 *   devices) that surface hand-off can crash the *native* codec process — a hard `PROCESS ENDED`
 *   with no Java stack trace.
 *
 * - [TEXTURE_VIEW] — renders into a regular `View` backed by a `SurfaceTexture`, which survives
 *   relayout **without** destroying/recreating the surface. This sidesteps the codec hand-off
 *   entirely and is the recommended workaround if you see native crashes on rotation. Trade-offs:
 *   slightly higher power/memory (an off-screen composite), and it cannot render to a *secure*
 *   surface — irrelevant for Boomstream's Clear Key playback, which is non-secure.
 *
 * ## How to set it
 *
 * - [com.boomstream.sdk.player.BoomstreamPlayerView] (View): the `boomstreamSurfaceType` XML
 *   attribute, or the `surfaceType` property (set before `load`).
 * - [com.boomstream.sdk.player.BoomstreamPlayer] (Compose): the `surfaceType` parameter.
 *
 * See `docs/PLAYER-API.md` for a fuller discussion.
 */
enum class BoomstreamSurfaceType {
    /** Default. `SurfaceView`-backed rendering. Surface is destroyed/recreated on relayout. */
    SURFACE_VIEW,

    /** `TextureView`-backed rendering. Survives rotation without recreating the surface. */
    TEXTURE_VIEW,
}
