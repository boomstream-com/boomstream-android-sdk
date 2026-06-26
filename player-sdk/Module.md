# Module player-sdk

Boomstream Player SDK — Media3/ExoPlayer wrapper with Composable + View surfaces,
Boomstream config endpoint integration, playlist support, and poster fallback for
unauthenticated access.

## Getting started

```kotlin
// 1. Initialise the SDK once in Application.onCreate()
Boomstream.init(this, BuildConfig.BOOMSTREAM_API_KEY)

// 2a. Compose (recommended)
BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
    // Optional: embed media-server-key in the User-Agent for authenticated HLS.
    userAgent = "BoomstreamSDK/1.0 ${BuildConfig.BOOMSTREAM_MEDIA_KEY}",
)

// 2b. View (XML / legacy)
// In XML layout: <com.boomstream.sdk.player.BoomstreamPlayerView ... />
playerView.load(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    userAgent = "BoomstreamSDK/1.0 ${BuildConfig.BOOMSTREAM_MEDIA_KEY}",
)
lifecycle.addObserver(playerView)   // auto-releases on ON_DESTROY
```

## Playback flow

1. `BoomstreamConfigClient.getConfig(mediaCode)` — fetches `play.boomstream.com/{mediaCode}/config`.
2. **Authenticated response** (`mediaData` present):
   - Single media: sets one `MediaItem` on ExoPlayer, starts HLS playback.
   - Playlist (`mediaData` array): sets N `MediaItem`s — ExoPlayer advances automatically.
3. **Unauthenticated response** (`mediaData` absent):
   - Displays the highest-resolution poster from `config.posters` via Coil.

## CSO security constraints

| Constraint | Description |
|---|---|
| **#1** | No raw `ExoPlayer` in public API — use [AdvancedPlayerOptions] for buffer tuning. |
| **#2** | No `HttpLoggingInterceptor` in SDK OkHttp client — integrators add their own in debug builds only via `BoomstreamOptions.additionalInterceptors`. |
| **#3** | Debug-variant publish is blocked at Gradle task level — only release AAR reaches GitLab Maven. |

## Packages

| Package | Contents |
|---|---|
| `com.boomstream.sdk.player` | Public API: [BoomstreamPlayer], [BoomstreamPlayerView], [PlayerState], [AdvancedPlayerOptions] |
| `com.boomstream.sdk.player.internal` | Internal engine (not part of public API) |
