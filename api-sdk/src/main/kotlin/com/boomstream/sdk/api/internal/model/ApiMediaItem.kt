package com.boomstream.sdk.api.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A media entry as returned inside `Medias[]` by `POST /api/media/folder`. */
@Serializable
internal data class ApiMediaItem(
    @SerialName("Code") val code: String,
    @SerialName("Title") val title: String = "",
    @SerialName("Duration") val duration: Int = 0,
    @SerialName("MediaStatus") val mediaStatus: String? = null,
    @SerialName("Poster") val poster: ApiPoster? = null,
)
