package com.boomstream.sdk.player.internal

import android.view.View
import androidx.media3.ui.PlayerView

/**
 * Hides or shows the seek bar and time display in the default Media3 controller.
 *
 * Live streams do not support scrubbing, so the timeline is hidden when [isLive] is `true`.
 * The controller overlay itself (play/pause, fullscreen) remains accessible.
 *
 * Uses `exo_progress` (DefaultTimeBar) and `exo_time` (position/duration text) which are
 * stable IDs in the Media3 default controller layout since ExoPlayer 2.x.
 */
internal fun PlayerView.applyLiveControllerUi(isLive: Boolean) {
    val vis = if (isLive) View.GONE else View.VISIBLE
    @Suppress("DEPRECATION")
    findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.visibility = vis
    @Suppress("DEPRECATION")
    findViewById<View>(androidx.media3.ui.R.id.exo_time)?.visibility = vis
}
