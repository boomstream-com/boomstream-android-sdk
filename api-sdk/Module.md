# Module api-sdk

Type-safe Kotlin client over the Boomstream API (`mcp.boomstream.com`) and player config endpoint (`play.boomstream.com/{mediaCode}/config`).

## Usage

```kotlin
// Initialise once in Application.onCreate()
Boomstream.init(
    context = this,
    apiKey = BuildConfig.BOOMSTREAM_API_KEY, // from local.properties or CI env
    options = BoomstreamOptions(
        userAgent = "MyApp/1.0 BoomstreamSDK/1.0",
        // Add debug-only interceptors here — never in release builds.
        // The SDK itself never registers HttpLoggingInterceptor.
        additionalInterceptors = if (BuildConfig.DEBUG) listOf(
            okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
            }
        ) else emptyList(),
    )
)

// Fetch config for a media code
lifecycleScope.launch {
    val result = Boomstream.configClient.getConfig("Il4lNOfL")
    result.fold(
        onSuccess = { config ->
            config.mediaDataSingle?.links?.hlsUrl?.let { hlsUrl ->
                // pass hlsUrl to ExoPlayer / MediaPlayer
            }
        },
        onFailure = { error ->
            when (error) {
                is BoomstreamApiError.Unauthorized -> showAuthError()
                is BoomstreamApiError.NotFound    -> showNotFound()
                is BoomstreamApiError.Network     -> showNetworkError()
                else                              -> showGenericError()
            }
        }
    )
}
```

## Authentication

Pass the API key to `Boomstream.init()`. The key is sent as `Authorization: Bearer <key>` on
every request. **Never hardcode the key in source** — read it from `local.properties` or a CI
secret and inject via `BuildConfig`:

```kotlin
// consumer app build.gradle.kts
val key = localProps.getProperty("BOOMSTREAM_API_KEY") ?: System.getenv("BOOMSTREAM_API_KEY") ?: ""
buildConfigField("String", "BOOMSTREAM_API_KEY", "\"$key\"")
```

## Config endpoint — unauthenticated fallback

For unauthenticated calls to `play.boomstream.com/{code}/config`, the server returns a
degraded response with posters only. The SDK does **not** throw `BoomstreamApiError.Unauthorized`
in this case — check `config.mediaDataSingle == null` to detect the fallback path and display
poster images without playback controls.
