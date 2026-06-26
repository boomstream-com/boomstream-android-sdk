package com.boomstream.sdk.api.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope returned by `POST https://boomstream.com/api/playlist/list`.
 *
 * Success shape: `{Items[], Status:"Success"}`.
 * Error shape:   `{Status:"Failed", Message:"..."}` — `Items` absent.
 *
 * Live example (2026-06-20, Poster added):
 * ```json
 * {
 *   "Items": [{"Code":"uLmWi9IB","Name":"Commercials","Duration":"244000","Poster":{"Url":"https://..."},"AmountFiles":4,...}],
 *   "Status": "Success"
 * }
 * ```
 */
@Serializable
internal data class PlaylistListApiResponse(
    @SerialName("Status") val status: String? = null,
    @SerialName("Message") val message: String? = null,
    @SerialName("Items") val items: List<ApiPlaylistItem> = emptyList(),
)
