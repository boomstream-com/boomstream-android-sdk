package com.boomstream.sdk.api.error

/**
 * Sealed hierarchy of typed errors returned by all Boomstream SDK public APIs.
 *
 * All public API functions return `Result<T>` — callers should `fold` or `getOrElse` on the
 * result and handle these error subtypes explicitly.
 */
sealed class BoomstreamApiError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /**
     * The Boomstream API returned `{"Status":"Failed","Message":"..."}`.
     *
     * This is a semantic error from the API layer (not an HTTP error).
     *
     * @param apiMessage The `Message` field from the API response.
     */
    class ApiError(val apiMessage: String) :
        BoomstreamApiError("Boomstream API error: $apiMessage")

    /**
     * The request was rejected with HTTP 401/403.
     *
     * For the config endpoint, an unauthenticated call returns a degraded response with
     * posters only rather than throwing this error — see [BoomstreamConfigClient] docs.
     */
    class Unauthorized(message: String = "Unauthorized — check your API key") :
        BoomstreamApiError(message)

    /**
     * The requested resource (media code, API path) was not found (HTTP 404).
     */
    class NotFound(val path: String, message: String = "Not found: $path") :
        BoomstreamApiError(message)

    /**
     * A network-level failure occurred before a response was received (timeout, DNS, TLS, etc.).
     */
    class Network(message: String, cause: Throwable? = null) :
        BoomstreamApiError(message, cause)

    /**
     * An unexpected HTTP status code or response body was received.
     *
     * @param httpStatus The HTTP status code, or null if the error occurred before HTTP.
     */
    class Unknown(message: String, val httpStatus: Int? = null, cause: Throwable? = null) :
        BoomstreamApiError(message, cause)
}
