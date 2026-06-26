package com.boomstream.sdk.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A media item entry returned by the `api/media/folder` listing endpoint. */
@Serializable
data class FolderMediaItem(
    @SerialName("code") val code: String,
    @SerialName("title") val title: String = "",
    @SerialName("duration") val duration: Int = 0,
    @SerialName("poster") val poster: String? = null,
    @SerialName("mediaStatus") val mediaStatus: String? = null,
)
