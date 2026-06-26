package com.boomstream.sdk.api.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope returned by `POST https://boomstream.com/api/media/folder`.
 *
 * Success shape: `{countTotal, Folders[], Medias[]}` — `Status` absent or `"Success"`.
 * Error shape:   `{Status:"Failed", Message:"..."}` — `Medias` absent.
 *
 * Live example (2026-06-19):
 * ```json
 * {
 *   "countTotal": 46,
 *   "Folders": [{"code":"p76oybzr","title":"...", ...}],
 *   "Medias":  [{"Code":"nKg3scvB","Title":"...","Duration":46,"Poster":{"Url":"..."},...}]
 * }
 * ```
 */
@Serializable
internal data class FolderApiResponse(
    @SerialName("Status") val status: String? = null,
    @SerialName("Message") val message: String? = null,
    @SerialName("countTotal") val countTotal: Int = 0,
    @SerialName("Medias") val medias: List<ApiMediaItem> = emptyList(),
)
