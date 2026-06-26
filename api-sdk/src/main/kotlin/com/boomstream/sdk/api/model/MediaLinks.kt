package com.boomstream.sdk.api.model

import android.util.Base64
import kotlinx.serialization.Serializable

/**
 * Playback links for a media item.
 *
 * All link values are Base64-encoded on the wire and decoded on access.
 */
@Serializable
data class MediaLinks(
    /** Base64-encoded HLS manifest URL. Use [hlsUrl] for the decoded value. */
    val hls: String? = null,
) {
    /** Decoded HLS manifest URL, or `null` if not present. */
    val hlsUrl: String?
        get() = hls?.let {
            runCatching { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }.getOrNull()
        }
}
