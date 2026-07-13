package com.boomstream.sdk.api.internal

/**
 * Internal SPI bridge for cross-module access to the User-Agent DRM token.
 *
 * Implemented by [com.boomstream.sdk.api.BoomstreamConfigClient] so that `player-sdk` and
 * `offline-sdk` can retrieve the init-time token (set via
 * [com.boomstream.sdk.api.BoomstreamOptions.userAgentToken]) without that token being part of
 * the public API surface of `BoomstreamConfigClient`.
 *
 * The property [userAgentToken] is gated by [InternalBoomstreamApi] both here and on the
 * implementing class.  SDK modules access it with `@OptIn(InternalBoomstreamApi::class)`;
 * application code will receive a compile-time error if it attempts to do the same.
 */
@InternalBoomstreamApi
interface UserAgentTokenProvider {
    /**
     * The DRM / ua_allow token supplied at SDK initialisation time, or `null` when none was
     * configured.  Used by the player modules as the default `allowClearKeyDRMtoken` when the
     * caller does not provide a per-call override.
     */
    val userAgentToken: String?
}
