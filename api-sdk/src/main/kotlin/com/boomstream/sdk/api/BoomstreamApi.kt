package com.boomstream.sdk.api

import com.boomstream.sdk.api.error.BoomstreamApiError
import com.boomstream.sdk.api.internal.BoomstreamApiService
import com.boomstream.sdk.api.internal.model.FolderApiRequest
import com.boomstream.sdk.api.model.FolderMediaItem
import com.boomstream.sdk.api.model.LiveMediaItem
import com.boomstream.sdk.api.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Type-safe Kotlin client for the Boomstream API at `https://boomstream.com/api/`.
 *
 * All functions are suspending and safe to call from any coroutine context — IO dispatch
 * is handled internally.
 *
 * Obtain an instance via [Boomstream.api] or [BoomstreamSdk.api].
 *
 * ## API contract
 * Calls use Mode 2 (POST + JSON body). Authentication is via `Authorization: Bearer` header,
 * added transparently by the SDK — callers only supply the key once at [Boomstream.init].
 */
class BoomstreamApi internal constructor(
    private val service: BoomstreamApiService,
) {

    /**
     * Returns media items from a folder in the account's video library.
     *
     * Calls `POST https://boomstream.com/api/media/folder`. When [folderCode] is null the
     * root folder is used. Only media entries are returned; sub-folder entries are omitted.
     *
     * API contract source: live-verified 2026-06-19.
     * Real response: `{countTotal, Folders:[...], Medias:[{Code,Title,Duration,MediaStatus,Poster:{Url}}]}`.
     *
     * @param folderCode Optional folder code. Null = root.
     * @return [Result.success] with a list of [FolderMediaItem]s (may be empty).
     * @return [Result.failure] with a typed [com.boomstream.sdk.api.error.BoomstreamApiError].
     */
    suspend fun listFolder(
        folderCode: String? = null,
    ): Result<List<FolderMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            service.listFolder(FolderApiRequest(code = folderCode))
        }
            .mapError("api/media/folder")
            .mapCatching { response ->
                if (response.status == "Failed") {
                    throw BoomstreamApiError.ApiError(response.message ?: "API error")
                }
                response.medias.map { item ->
                    FolderMediaItem(
                        code = item.code,
                        title = item.title,
                        duration = item.duration,
                        poster = item.poster?.url,
                        mediaStatus = item.mediaStatus,
                    )
                }
            }
    }

    /**
     * Returns live broadcast items from the account's live folder.
     *
     * Calls `POST https://boomstream.com/api/live/folder`. When [folderCode] is null the
     * root folder is used.
     *
     * API contract source: live-verified 2026-06-20.
     * Real response: `{countTotal, Medias:[{Code,Title,Poster:{Url}|null,...}], Status:"Success"}`.
     * Items have no Duration or MediaStatus fields.
     *
     * @param folderCode Optional folder code. Null = root.
     * @return [Result.success] with a list of [LiveMediaItem]s (may be empty).
     * @return [Result.failure] with a typed [com.boomstream.sdk.api.error.BoomstreamApiError].
     */
    suspend fun listLive(
        folderCode: String? = null,
    ): Result<List<LiveMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            service.listLive(FolderApiRequest(code = folderCode))
        }
            .mapError("api/live/folder")
            .mapCatching { response ->
                if (response.status == "Failed") {
                    throw BoomstreamApiError.ApiError(response.message ?: "API error")
                }
                response.medias.map { item ->
                    LiveMediaItem(
                        code = item.code,
                        title = item.title,
                        poster = item.poster?.url,
                    )
                }
            }
    }

    /**
     * Returns playlist items from the account.
     *
     * Calls `POST https://boomstream.com/api/playlist/list`.
     *
     * API contract source: live-verified 2026-06-20; Poster field added.
     * Real response: `{Items:[{Code,Name,Duration:"<ms>",Poster:{Url}|null,AmountFiles,...}], Status:"Success"}`.
     * Note: `Duration` is total playlist length in milliseconds, serialised as a string.
     * Note: `Name` (not `Title`) identifies playlists. `Poster` follows the same `{Url}` shape as other endpoints.
     *
     * @return [Result.success] with a list of [PlaylistItem]s (may be empty).
     * @return [Result.failure] with a typed [com.boomstream.sdk.api.error.BoomstreamApiError].
     */
    suspend fun listPlaylists(): Result<List<PlaylistItem>> = withContext(Dispatchers.IO) {
        runCatching {
            service.listPlaylists(FolderApiRequest())
        }
            .mapError("api/playlist/list")
            .mapCatching { response ->
                if (response.status == "Failed") {
                    throw BoomstreamApiError.ApiError(response.message ?: "API error")
                }
                response.items.map { item ->
                    PlaylistItem(
                        code = item.code,
                        name = item.name,
                        durationSeconds = item.durationMs.toLongOrNull()?.div(1000)?.toInt() ?: 0,
                        poster = item.poster?.url,
                    )
                }
            }
    }
}
