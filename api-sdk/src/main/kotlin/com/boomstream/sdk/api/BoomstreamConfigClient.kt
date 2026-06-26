package com.boomstream.sdk.api

import com.boomstream.sdk.api.error.BoomstreamApiError
import com.boomstream.sdk.api.internal.BoomstreamConfigService
import com.boomstream.sdk.api.model.ConfigResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for the Boomstream player config endpoint:
 * `https://play.boomstream.com/{mediaCode}/config`
 *
 * This endpoint is used by the player-sdk and offline-sdk to resolve playback URLs
 * and poster images for a given media code.
 *
 * **Authentication behaviour:**
 * - Authenticated (valid API key): [ConfigResponse.mediaDataSingle] (or [ConfigResponse.mediaDataPlaylist]
 *   for playlists) will contain full [com.boomstream.sdk.api.model.MediaData] with
 *   [com.boomstream.sdk.api.model.MediaLinks].
 * - Unauthenticated / invalid key: the endpoint returns a degraded response where
 *   [ConfigResponse.mediaData] is null and only [ConfigResponse.posters] is populated.
 *   The SDK does **not** throw [BoomstreamApiError.Unauthorized] in this case — check
 *   [ConfigResponse.mediaDataSingle] for null to detect the unauthenticated case.
 *
 * Obtain an instance via [Boomstream.configClient].
 */
class BoomstreamConfigClient internal constructor(
    private val service: BoomstreamConfigService,
    /**
     * Token from [BoomstreamOptions.userAgentToken], carried here so that
     * [com.boomstream.sdk.player.BoomstreamPlayer] and
     * [com.boomstream.sdk.player.BoomstreamPlayerView] can use it as the default
     * `allowClearKeyDRMtoken` when the caller passes the `configClient` but omits the per-call
     * token parameter.  `null` when no token was configured at init time.
     */
    val userAgentToken: String? = null,
) {

    // In-memory cache of successful responses keyed by mediaCode. Survives Activity
    // recreation (orientation change) so a new player instance can start playback
    // without a second network round-trip. Only successful responses are cached;
    // network errors are never stored so a subsequent call retries the network.
    private val responseCache = ConcurrentHashMap<String, ConfigResponse>()

    /**
     * Fetches the config for [mediaCode].
     *
     * Successful responses are cached in memory for the lifetime of this instance so
     * that a player recreated due to orientation change (or any other config-change)
     * can resume playback without a second network call — safe for offline playback.
     *
     * @param forceRefresh When `true`, the in-memory cache is bypassed and a fresh network
     *   request is made. Used by the live-stream polling loop and by the source-lost recovery
     *   path to check whether the broadcast has come online since the last fetch.
     *   On success the cache is still updated so subsequent non-forced calls return the
     *   fresh value without another round-trip.
     * @return [Result.success] with a [ConfigResponse] on success.
     * @return [Result.failure] with a typed [BoomstreamApiError] on error.
     */
    suspend fun getConfig(mediaCode: String, forceRefresh: Boolean = false): Result<ConfigResponse> {
        if (!forceRefresh) {
            responseCache[mediaCode]?.let { return Result.success(it) }
        }
        return withContext(Dispatchers.IO) {
            runCatching { service.getConfig(mediaCode) }
                .mapError(mediaCode)
                .onSuccess { responseCache[mediaCode] = it }
        }
    }
}

/** Maps raw [Throwable]s from Retrofit/OkHttp to typed [BoomstreamApiError]s. */
internal fun <T> Result<T>.mapError(path: String = ""): Result<T> =
    mapCatching { it }
        .recoverCatching { cause ->
            when {
                cause is HttpException && cause.code() == 401 ->
                    throw BoomstreamApiError.Unauthorized()
                cause is HttpException && cause.code() == 403 ->
                    throw BoomstreamApiError.Unauthorized("Forbidden — check your API key")
                cause is HttpException && cause.code() == 404 ->
                    throw BoomstreamApiError.NotFound(path)
                cause is HttpException ->
                    throw BoomstreamApiError.Unknown(
                        "HTTP ${cause.code()}: ${cause.message()}",
                        httpStatus = cause.code(),
                        cause = cause,
                    )
                cause is IOException ->
                    throw BoomstreamApiError.Network(cause.message ?: "Network error", cause)
                else ->
                    throw BoomstreamApiError.Unknown(cause.message ?: "Unknown error", cause = cause)
            }
        }
