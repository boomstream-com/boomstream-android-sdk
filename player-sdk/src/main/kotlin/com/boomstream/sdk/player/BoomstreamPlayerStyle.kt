package com.boomstream.sdk.player

import androidx.annotation.ColorInt

/**
 * Visual style overrides for the Boomstream player.
 *
 * All fields are nullable — `null` means "keep the SDK default" (no visible change).
 * Set via [BoomstreamPlayerView.style], [BoomstreamPlayerView.setLoaderColor], or pass to
 * [BoomstreamPlayer] via the `style` parameter.
 *
 * ## XML (View path)
 * ```xml
 * <com.boomstream.sdk.player.BoomstreamPlayerView
 *     xmlns:app="http://schemas.android.com/apk/res-auto"
 *     android:id="@+id/player"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:boomstreamLoaderColor="@color/brand_violet"
 *     app:boomstreamAccentColor="@color/brand_violet"
 *     app:boomstreamSeekBarPlayedColor="@color/brand_violet"
 *     app:boomstreamMessageTextColor="@android:color/white"
 *     app:boomstreamMessageBackgroundColor="#CC000000" />
 * ```
 *
 * ## Programmatic (View path)
 * ```kotlin
 * playerView.style = BoomstreamPlayerStyle(
 *     loaderColor = ContextCompat.getColor(this, R.color.brand_violet),
 *     accentColor = ContextCompat.getColor(this, R.color.brand_violet),
 * )
 * // or point-change:
 * playerView.setLoaderColor(ContextCompat.getColor(this, R.color.brand_violet))
 * ```
 *
 * ## Compose path
 * ```kotlin
 * BoomstreamPlayer(
 *     mediaCode = "Il4lNOfL",
 *     configClient = Boomstream.configClient,
 *     style = BoomstreamPlayerStyle(
 *         loaderColor = Color(0xFF662BFF).toArgb(),
 *         accentColor = Color(0xFF662BFF).toArgb(),
 *     ),
 * )
 * ```
 *
 * ## CSO constraint
 * All colour fields use standard `@ColorInt Int` — no `androidx.media3.*` types are exposed
 * (CSO constraint #1). Compose callers convert via [androidx.compose.ui.graphics.Color.toArgb].
 *
 * @property loaderColor            Tint of the loading spinner shown while buffering.
 *                                  Full control via [android.widget.ProgressBar.setIndeterminateTintList].
 * @property accentColor            Best-effort tint for playback control icons (play, pause, seek
 *                                  handle, navigation buttons). Applied via icon tint on the
 *                                  Media3 PlayerView buttons — coverage depends on the Media3 version
 *                                  and the active device theme. See [BoomstreamPlayerView.setAccentColor].
 * @property seekBarPlayedColor     Colour of the played portion of the seek bar.
 * @property seekBarScrubberColor   Colour of the scrubber thumb on the seek bar.
 * @property seekBarBufferedColor   Colour of the buffered (pre-loaded) portion of the seek bar.
 * @property messageTextColor       Text colour of the system-message overlay banner.
 * @property messageBackgroundColor Background colour of the system-message overlay banner.
 *                                  Default is `#CC000000` (80 % opaque black).
 */
class BoomstreamPlayerStyle @JvmOverloads constructor(
    @ColorInt val loaderColor: Int? = null,
    @ColorInt val accentColor: Int? = null,
    @ColorInt val seekBarPlayedColor: Int? = null,
    @ColorInt val seekBarScrubberColor: Int? = null,
    @ColorInt val seekBarBufferedColor: Int? = null,
    @ColorInt val messageTextColor: Int? = null,
    @ColorInt val messageBackgroundColor: Int? = null,
)

// ── Internal copy-helpers used by BoomstreamPlayerView convenience setters ──────

internal fun BoomstreamPlayerStyle.withLoaderColor(@ColorInt color: Int) = BoomstreamPlayerStyle(
    loaderColor = color, accentColor = accentColor,
    seekBarPlayedColor = seekBarPlayedColor, seekBarScrubberColor = seekBarScrubberColor,
    seekBarBufferedColor = seekBarBufferedColor, messageTextColor = messageTextColor,
    messageBackgroundColor = messageBackgroundColor,
)

internal fun BoomstreamPlayerStyle.withAccentColor(@ColorInt color: Int) = BoomstreamPlayerStyle(
    loaderColor = loaderColor, accentColor = color,
    seekBarPlayedColor = seekBarPlayedColor, seekBarScrubberColor = seekBarScrubberColor,
    seekBarBufferedColor = seekBarBufferedColor, messageTextColor = messageTextColor,
    messageBackgroundColor = messageBackgroundColor,
)

internal fun BoomstreamPlayerStyle.withSeekBarPlayedColor(@ColorInt color: Int) = BoomstreamPlayerStyle(
    loaderColor = loaderColor, accentColor = accentColor,
    seekBarPlayedColor = color, seekBarScrubberColor = seekBarScrubberColor,
    seekBarBufferedColor = seekBarBufferedColor, messageTextColor = messageTextColor,
    messageBackgroundColor = messageBackgroundColor,
)

internal fun BoomstreamPlayerStyle.withSeekBarScrubberColor(@ColorInt color: Int) = BoomstreamPlayerStyle(
    loaderColor = loaderColor, accentColor = accentColor,
    seekBarPlayedColor = seekBarPlayedColor, seekBarScrubberColor = color,
    seekBarBufferedColor = seekBarBufferedColor, messageTextColor = messageTextColor,
    messageBackgroundColor = messageBackgroundColor,
)

internal fun BoomstreamPlayerStyle.withSeekBarBufferedColor(@ColorInt color: Int) = BoomstreamPlayerStyle(
    loaderColor = loaderColor, accentColor = accentColor,
    seekBarPlayedColor = seekBarPlayedColor, seekBarScrubberColor = seekBarScrubberColor,
    seekBarBufferedColor = color, messageTextColor = messageTextColor,
    messageBackgroundColor = messageBackgroundColor,
)

internal fun BoomstreamPlayerStyle.withMessageTextColor(@ColorInt color: Int) = BoomstreamPlayerStyle(
    loaderColor = loaderColor, accentColor = accentColor,
    seekBarPlayedColor = seekBarPlayedColor, seekBarScrubberColor = seekBarScrubberColor,
    seekBarBufferedColor = seekBarBufferedColor, messageTextColor = color,
    messageBackgroundColor = messageBackgroundColor,
)

internal fun BoomstreamPlayerStyle.withMessageBackgroundColor(@ColorInt color: Int) = BoomstreamPlayerStyle(
    loaderColor = loaderColor, accentColor = accentColor,
    seekBarPlayedColor = seekBarPlayedColor, seekBarScrubberColor = seekBarScrubberColor,
    seekBarBufferedColor = seekBarBufferedColor, messageTextColor = messageTextColor,
    messageBackgroundColor = color,
)
