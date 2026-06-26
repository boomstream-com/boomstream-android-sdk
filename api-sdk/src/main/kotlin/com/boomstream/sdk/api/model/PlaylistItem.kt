package com.boomstream.sdk.api.model

/**
 * A playlist entry returned by the `api/playlist/list` endpoint.
 *
 * [durationSeconds] is the total playlist duration in seconds, converted from the
 * API's millisecond string field (`"Duration": "244000"` → 244 s).
 * [poster] is the absolute URL of the playlist thumbnail, or null when absent.
 */
data class PlaylistItem(
    val code: String,
    val name: String = "",
    val durationSeconds: Int = 0,
    val poster: String? = null,
)
