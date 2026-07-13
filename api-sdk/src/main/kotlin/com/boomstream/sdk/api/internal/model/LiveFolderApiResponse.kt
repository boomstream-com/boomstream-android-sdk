package com.boomstream.sdk.api.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope returned by `POST https://boomstream.com/api/live/folder`.
 *
 * Success shape: `{countTotal, Folders[], Medias[]}` — `Status` absent or `"Success"`.
 * Error shape:   `{Status:"Failed", Message:"..."}` — `Medias` absent.
 *
 * Live example (2026-06-20):
 * ```json
 * {
 *   "countTotal": 8,
 *   "Folders": null,
 *   "Medias": [{"Code":"H4pNmmAU","Title":"test3","Poster":null,...}]
 * }
 * ```
 */
@Serializable
internal data class LiveFolderApiResponse(
    @SerialName("Status") val status: String? = null,
    @SerialName("Message") val message: String? = null,
    @SerialName("countTotal") val countTotal: Int = 0,
    @SerialName("Medias") val medias: List<ApiLiveItem> = emptyList(),
)
