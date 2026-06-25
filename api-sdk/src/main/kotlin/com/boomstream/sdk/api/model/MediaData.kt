package com.boomstream.sdk.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Metadata and streaming info for a single media item.
 *
 * Returned inside [ConfigResponse.mediaDataSingle] when the media code
 * resolves to a single video. For playlists, see [ConfigResponse.mediaDataPlaylist].
 *
 * Live-stream state contract (verified 2026-06-22 against play.boomstream.com/vWktqOGl/config):
 * - [isLive] `true` → content is a live broadcast.
 * - [isPublish] `true` → broadcast has been published (started) by operator → can play.
 * - [isPublish] `false` → broadcast not yet started/published → offline.
 * - [source] — encoder source metadata (external RTMP/SRT link), NOT an availability flag.
 *   `false` (JSON boolean) means no external source URL is configured; playback is always
 *   through [MediaLinks.hlsUrl] regardless of this field.
 * Use [isLiveOffline] to check the offline condition.
 */
@Serializable
data class MediaData(
    val title: String = "",
    val code: String = "",
    val duration: Int = 0,
    val posters: List<Poster> = emptyList(),
    val links: MediaLinks? = null,
    val mediaType: String = "media",
    val width: Int? = null,
    val height: Int? = null,
    val ratio: String? = null,
    val isLive: Boolean = false,
    val token: String? = null,
    val thumbnails: String? = null,
    /** `true` when the broadcast has been published (started) by the operator. */
    val isPublish: Boolean = false,
    /**
     * Encoder source metadata for the live stream.
     *
     * Wire type is polymorphic: JSON `false` (Boolean) when no external RTMP/SRT source URL
     * is configured; a URL string when an external source is set. This is NOT an availability
     * indicator — use [isLiveOffline] for offline state. Playback always goes through
     * [MediaLinks.hlsUrl] regardless of this field.
     */
    val source: JsonElement? = null,
    /**
     * Recordings of this live stream available for playback when the broadcast is offline.
     *
     * Each element has the same shape as [MediaData] itself (verified 2026-06-22 against
     * the Boomstream config API). When non-empty and [isLiveOffline] is `true`, the SDK
     * plays these as a playlist instead of showing the offline poster.
     */
    val records: List<MediaData> = emptyList(),
) {
    /**
     * `true` when this is a live item ([isLive]) that is currently offline —
     * i.e. not published by the operator (`!isPublish`).
     *
     * Deliberately ignores [source]: `source` is encoder-source metadata (external RTMP/SRT
     * link type), not an availability flag. Real stream unavailability (broken HLS, encoder
     * error) is handled through playback-error paths, not here.
     */
    val isLiveOffline: Boolean
        get() = isLive && !isPublish
}
