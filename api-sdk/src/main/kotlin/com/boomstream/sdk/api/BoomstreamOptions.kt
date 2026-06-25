package com.boomstream.sdk.api

import okhttp3.Interceptor

/**
 * Configuration options for the Boomstream SDK.
 *
 * Pass a customised instance to [Boomstream.init] to override defaults.
 *
 * @param userAgent Custom `User-Agent` header sent with every request.
 *   Defaults to `"BoomstreamSDK/1.0"`. Override to embed a media-server-key
 *   or consumer-app identifier: `"BoomstreamSDK/1.0 ConsumerApp/1.0"`.
 *   **This value may carry sensitive key material — do not log or externally expose it.**
 * @param connectTimeoutSeconds TCP connect timeout in seconds. Default 15.
 * @param readTimeoutSeconds HTTP read timeout in seconds. Default 30.
 * @param additionalInterceptors OkHttp [Interceptor]s added by the integrator on top of
 *   the SDK's own chain. Intended for debug logging or custom telemetry interceptors
 *   provided by `okhttp3:logging-interceptor` or similar.
 *
 *   **Headers visible to these interceptors** (injected by the SDK before yours run):
 *   - Config requests (`play.boomstream.com`): `User-Agent` only — no Authorization header is sent.
 *   - API requests (`boomstream.com/api/`): `Authorization: Bearer <api-key>` and `User-Agent`; body contains only `ver` and request parameters — no secrets in body.
 *   The Bearer token only travels on `boomstream.com/api/` — never log API-path requests at `HEADERS` level in release builds.
 *   Restrict any logging interceptor to `BuildConfig.DEBUG`-gated code:
 *   ```
 *   val interceptors = buildList {
 *       if (BuildConfig.DEBUG) {
 *           add(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
 *       }
 *   }
 *   Boomstream.init(this, BuildConfig.BOOMSTREAM_API_KEY,
 *       BoomstreamOptions(additionalInterceptors = interceptors))
 *   ```
 *   The SDK itself never registers a logging interceptor (CSO-mandated constraint #2).
 * @param apiBaseUrl Base URL for the Boomstream media API (`api/media/folder`, etc.).
 *   Defaults to `"https://boomstream.com/"`. Override to point at a staging or
 *   local-dev server. Read from `local.properties` via `BuildConfig.BOOMSTREAM_API_URL`
 *   in the example-app (falls back to the production URL when the property is absent).
 * @param configBaseUrl Base URL for the Boomstream config service (`{mediaCode}/config`).
 *   Defaults to `"https://play.boomstream.com/"`. Override to point at a staging or
 *   local-dev server. Read from `local.properties` via `BuildConfig.BOOMSTREAM_CONFIG_URL`
 *   in the example-app (falls back to the production URL when the property is absent).
 */
class BoomstreamOptions(
    val userAgent: String = "BoomstreamSDK/1.0",
    val connectTimeoutSeconds: Long = 15L,
    val readTimeoutSeconds: Long = 30L,
    val additionalInterceptors: List<Interceptor> = emptyList(),
    val apiBaseUrl: String = "https://boomstream.com/",
    val configBaseUrl: String = "https://play.boomstream.com/",
) {
    override fun toString(): String =
        "BoomstreamOptions(userAgent=***, connectTimeoutSeconds=$connectTimeoutSeconds, " +
            "readTimeoutSeconds=$readTimeoutSeconds, " +
            "additionalInterceptors=${additionalInterceptors.size} interceptor(s))"
}
