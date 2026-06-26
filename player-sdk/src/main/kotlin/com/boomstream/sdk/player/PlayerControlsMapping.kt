package com.boomstream.sdk.player

/**
 * Returns `true` when prev/next navigation buttons should be shown in the player UI.
 *
 * Only `PlayerState.Ready` with `isPlaylist = true` warrants navigation controls.
 * All other states (Idle, Loading, Ended, PosterOnly, Error) and single-video Ready
 * hide the buttons — there is nothing to navigate to or from.
 */
internal fun PlayerState.navButtonsVisible(): Boolean =
    this is PlayerState.Ready && this.isPlaylist
