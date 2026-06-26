package com.boomstream.example

import android.app.Application
import com.boomstream.sdk.api.Boomstream
import com.boomstream.sdk.api.BoomstreamOptions
import com.boomstream.sdk.offline.BoomstreamOfflineManager

class ExampleApp : Application() {

    /**
     * Lazily created only when the API key is present (guard in [onCreate]).
     * Returns null when [BuildConfig.BOOMSTREAM_API_KEY] is blank so the
     * ViewModel can gate on null without crashing on uninitialised SDK.
     */
    val offlineManager: BoomstreamOfflineManager? by lazy {
        if (BuildConfig.BOOMSTREAM_API_KEY.isBlank()) null
        else BoomstreamOfflineManager(
            context = this,
            configClient = Boomstream.configClient,
            userAgent = configUserAgent(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        val apiKey = BuildConfig.BOOMSTREAM_API_KEY
        // Fail-fast on blank key so the error surfaces at startup, not deep in a coroutine.
        // For real apps: validate the key before reaching production; for dev builds use
        // local.properties (see local.properties.example).
        if (apiKey.isNotBlank()) {
            Boomstream.init(
                this,
                apiKey,
                BoomstreamOptions(
                    // Pass the ua_allow / Clear Key DRM access token once at init time.
                    // Both the config client and segment/manifest client will use it automatically.
                    // Read from local.properties or CI env — never hardcode this value.
                    userAgentToken = BuildConfig.BOOMSTREAM_DRM_TOKEN.ifBlank { null },
                    apiBaseUrl = BuildConfig.BOOMSTREAM_API_URL,
                    configBaseUrl = BuildConfig.BOOMSTREAM_CONFIG_URL,
                ),
            )
        }
    }

    /** Builds the User-Agent string for the offline-sdk download client. */
    private fun configUserAgent(): String {
        val base = "BoomstreamExample/1.0"
        val token = BuildConfig.BOOMSTREAM_DRM_TOKEN
        return if (token.isBlank()) base else "$base $token"
    }
}
