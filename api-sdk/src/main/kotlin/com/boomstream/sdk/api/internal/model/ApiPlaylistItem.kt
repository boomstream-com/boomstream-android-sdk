package com.boomstream.sdk.api.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A playlist entry as returned inside `Items[]` by `POST /api/playlist/list`.
 *
 * [durationMs] is the total playlist duration in milliseconds, serialised as a string by the API.
 * Live example (2026-06-20): `"Duration": "244000"` for a 4-file playlist ≈ 244 s total.
 * [poster] follows the same `{Url}` shape used by `api/media/folder` and `api/live/folder`.
 */
@Serializable
internal data class ApiPlaylistItem(
    @SerialName("Code") val code: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Duration") val durationMs: String = "0",
    @SerialName("Poster") val poster: ApiPoster? = null,
)
