package com.boomstream.sdk.player.internal

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.boomstream.sdk.player.BoomstreamPlayerStyle

/**
 * Applies [style] colours to a Media3 [PlayerView] in-place.
 *
 * Seek bar colours are forwarded to [DefaultTimeBar] via its public setters.
 * Accent colour is applied best-effort to the standard Media3 control button image views.
 *
 * Only non-null fields in [style] are applied — null fields leave the existing colour unchanged.
 *
 * @UnstableApi: [DefaultTimeBar] and its runtime setters are marked unstable by Media3.
 * Usage is isolated to this internal helper so the opt-in is not spread across the public surface.
 */
@OptIn(UnstableApi::class)
internal fun applyStyleToPlayerView(pv: PlayerView, style: BoomstreamPlayerStyle) {
    // ── Seek bar (DefaultTimeBar) ─────────────────────────────────────────────
    val timeBar = pv.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
    if (timeBar != null) {
        style.seekBarPlayedColor?.let { timeBar.setPlayedColor(it) }
        style.seekBarScrubberColor?.let { timeBar.setScrubberColor(it) }
        style.seekBarBufferedColor?.let { timeBar.setBufferedColor(it) }
    }

    // ── Accent: control button icon tints (best-effort) ───────────────────────
    // ImageView.imageTintList is API 21+, safe for our minSdk 24.
    style.accentColor?.let { color ->
        val tint = ColorStateList.valueOf(color)
        listOf(
            androidx.media3.ui.R.id.exo_play,
            androidx.media3.ui.R.id.exo_pause,
            androidx.media3.ui.R.id.exo_play_pause,
            androidx.media3.ui.R.id.exo_prev,
            androidx.media3.ui.R.id.exo_next,
            androidx.media3.ui.R.id.exo_ffwd,
            androidx.media3.ui.R.id.exo_rew,
            androidx.media3.ui.R.id.exo_settings,
            androidx.media3.ui.R.id.exo_fullscreen,
        ).forEach { id ->
            pv.findViewById<ImageView?>(id)?.imageTintList = tint
        }
    }
}

/**
 * Intercepts taps on the native Media3 `exo_settings` button and shows [BoomstreamSettingsSheet]
 * instead of the native settings dialog.
 *
 * Call this in the [PlayerView] factory **and** in every `update` block so the interceptor
 * always captures the latest [style] and [enableQualitySelector] values.
 *
 * The interceptor is a no-op when [player] is null (player not yet loaded).
 */
@OptIn(UnstableApi::class)
internal fun PlayerView.interceptSettingsButton(
    playerProvider: () -> BoomstreamMediaPlayer?,
    styleProvider: () -> BoomstreamPlayerStyle?,
    enableQualitySelectorProvider: () -> Boolean,
) {
    @Suppress("DEPRECATION")
    val btn = findViewById<View?>(androidx.media3.ui.R.id.exo_settings) ?: return
    btn.setOnClickListener {
        val mp = playerProvider() ?: return@setOnClickListener
        BoomstreamSettingsSheet(
            context = context,
            player = mp,
            style = styleProvider(),
            enableQualitySelector = enableQualitySelectorProvider(),
        ).show()
    }
}
