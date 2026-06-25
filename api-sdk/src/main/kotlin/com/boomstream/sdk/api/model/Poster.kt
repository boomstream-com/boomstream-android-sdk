package com.boomstream.sdk.api.model

import kotlinx.serialization.Serializable

/**
 * A single poster/thumbnail image at a specific resolution.
 *
 * @property width  Pixel width of the image.
 * @property height Pixel height of the image.
 * @property link   Absolute URL of the image.
 */
@Serializable
data class Poster(
    val width: Int,
    val height: Int,
    val link: String,
)
