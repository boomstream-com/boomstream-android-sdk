package com.boomstream.sdk.api.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A live broadcast entry as returned inside `Medias[]` by `POST /api/live/folder`. */
@Serializable
internal data class ApiLiveItem(
    @SerialName("Code") val code: String,
    @SerialName("Title") val title: String = "",
    @SerialName("Poster") val poster: ApiPoster? = null,
)
