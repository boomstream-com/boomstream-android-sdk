package com.boomstream.sdk.api.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Poster object as returned by `POST /api/media/folder` Medias[].Poster. */
@Serializable
internal data class ApiPoster(
    @SerialName("Url") val url: String? = null,
)
