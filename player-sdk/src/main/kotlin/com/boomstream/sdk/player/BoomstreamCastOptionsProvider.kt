package com.boomstream.sdk.player

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Cast options provider for Boomstream SDK v1.
 *
 * Uses the default Chromecast receiver application (`CC1AD845`) which supports unprotected HLS
 * streams out of the box. DRM-protected content is planned for a future release.
 *
 * ## Setup
 *
 * Add the following `<meta-data>` entry inside the `<application>` block of your app's
 * `AndroidManifest.xml`:
 *
 * ```xml
 * <meta-data
 *     android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
 *     android:value="com.boomstream.sdk.player.BoomstreamCastOptionsProvider" />
 * ```
 *
 * Once registered, any `MediaRouteButton` you place in your own layout will automatically
 * discover and connect to Chromecast devices — no additional SDK code is required.
 *
 * ## Controller API
 *
 * After [com.boomstream.sdk.player.BoomstreamPlayer] / [BoomstreamPlayerView] initialises, collect
 * the Cast state from your controller:
 *
 * ```kotlin
 * val isCasting by controller.isCasting.collectAsState()
 * val deviceName by controller.castDeviceName.collectAsState()
 * ```
 *
 * ## v1 limitation
 *
 * Only unprotected content is supported in this release. If [BoomstreamPlayer] was started with a
 * `allowClearKeyDRMtoken`, Cast handoff is suppressed and playback remains on the device.
 */
class BoomstreamCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> =
        emptyList()
}
