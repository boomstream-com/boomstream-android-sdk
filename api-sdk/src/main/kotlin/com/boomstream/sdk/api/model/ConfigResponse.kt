package com.boomstream.sdk.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Root response from `https://play.boomstream.com/{mediaCode}/config`.
 *
 * The [mediaData] field is polymorphic on the wire:
 * - **null** — unauthenticated access (only [posters] and [entity] are present)
 * - **JSON object** — authenticated single-media response
 * - **JSON array** — authenticated playlist response
 *
 * Use the typed accessors [mediaDataSingle] and [mediaDataPlaylist] rather than
 * reading [mediaData] directly.
 */
@Serializable
data class ConfigResponse(
    val code: String = "",
    val language: String = "en",
    val posters: List<Poster> = emptyList(),
    /** Raw `mediaData` JSON — either null, an object, or an array. */
    val mediaData: JsonElement? = null,
    val entity: Entity? = null,
    val error: ServerError? = null,
    val isLive: Boolean = false,
    @SerialName("streaming_protocol")
    val streamingProtocol: String = "hls",
    val encrypt: Boolean = false,
    val mediaType: String = "media",
    /** Fallback assets provided by the server when per-media assets are absent. */
    val defaults: ConfigDefaults? = null,
    /** Non-null when the server restricts access for the current viewer (PPV/subscription gate,
     *  preview-time expiry, etc.). Contains a localisation key and a server-provided fallback.
     *  Wire shape: `{ "message": "access_restricted", "translate": "<server text>" }`.
     *  Verified live contract 2026-06-22. */
    val accessRestricted: AccessRestricted? = null,
)

/**
 * Fallback assets from the `defaults` object in the config response.
 *
 * Verified 2026-06-21 against `https://play.boomstream.com/Il4lNOfL/config`:
 * `defaults.posters` uses the same `{width, height, link}` shape as top-level `posters`;
 * `height` is typically 0 for defaults (CDN sizes by width only).
 */
@Serializable
data class ConfigDefaults(
    val posters: List<Poster> = emptyList(),
)

/** Minimal entity descriptor included even in unauthenticated responses. */
@Serializable
data class Entity(
    val code: String = "",
    val title: String = "",
)

/** Server-side error envelope. A non-zero [code] indicates failure. */
@Serializable
data class ServerError(
    val code: Int = 0,
    val message: String = "",
    val translate: String = "",
)

/**
 * Access restriction descriptor returned by the config endpoint when the viewer cannot
 * play the content (PPV gate, subscription expiry, preview limit, etc.).
 *
 * @param message  Localisation key (e.g. `"access_restricted"`).  Used to look up a
 *                 locale-specific string in the player's message table.
 * @param translate Server-provided fallback string.  Used when [message] is not present
 *                 in the local message table.
 */
@Serializable
data class AccessRestricted(
    val message: String = "",
    val translate: String = "",
)

// ── Typed mediaData accessors ────────────────────────────────────────────────

private val mediaDataDecoder = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * `true` when the server identified this content as a playlist.
 *
 * Authoritative source: [mediaType] field from `play.boomstream.com/{code}/config`
 * (verified live 2026-06-20 — single video → `"media"`, playlist → `"playlist"`).
 * The [mediaData] array shape is a secondary indicator; [mediaType] takes precedence
 * so a single video whose [mediaData] happens to be an array is never treated as a
 * playlist by mistake.
 */
val ConfigResponse.isPlaylist: Boolean
    get() = mediaType == "playlist"

/** Decoded [MediaData] for a single-media response, or `null` for unauthenticated / playlist. */
val ConfigResponse.mediaDataSingle: MediaData?
    get() = if (mediaData is JsonObject) {
        runCatching { mediaDataDecoder.decodeFromJsonElement(MediaData.serializer(), mediaData as JsonObject) }.getOrNull()
    } else null

/** Decoded playlist items for a playlist response, or `null` for unauthenticated / single-media. */
val ConfigResponse.mediaDataPlaylist: List<MediaData>?
    get() = if (mediaData != null && mediaData !is JsonObject) {
        runCatching {
            mediaData!!.jsonArray.map { mediaDataDecoder.decodeFromJsonElement(MediaData.serializer(), it) }
        }.getOrNull()
    } else null

/**
 * The effective poster list: [posters] when non-empty, otherwise [ConfigDefaults.posters] from
 * [defaults]. Returns an empty list when both are absent.
 */
val ConfigResponse.effectivePosters: List<Poster>
    get() = posters.ifEmpty { defaults?.posters ?: emptyList() }
