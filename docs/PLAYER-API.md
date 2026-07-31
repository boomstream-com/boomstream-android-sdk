# Boomstream Player API — Events & Controls

Full reference for programmatic player observation and control in `player-sdk` v1.2.0+.
Video quality selection was added in v1.5.0 (see [Video quality selection](#video-quality-selection)).
Player styling / theming was added in v1.6.0 (see [Styling / theming](#styling--theming)).
Google Cast support (v1) was added in v1.7.0 (see [Google Cast](#google-cast)).

- [Controller](#controller)
  - [Compose path](#compose-path)
  - [View path](#view-path)
- [Events](#events)
- [Progress](#progress)
- [Control calls](#control-calls)
- [Recipes](#recipes)
  - [Progress bar + 70% trigger](#progress-bar--70-trigger)
  - [Basic play / pause / seek](#basic-play--pause--seek)
  - [Fullscreen handling](#fullscreen-handling)
- [Web → Native mapping](#web--native-mapping)
- [Surface type (SurfaceView vs TextureView)](#surface-type-surfaceview-vs-textureview)
- [Video quality selection](#video-quality-selection)
- [Styling / theming](#styling--theming)
- [Google Cast](#google-cast)
- [Backward compatibility](#backward-compatibility)

---

## Controller

`BoomstreamPlayerController` is the single interface for event observation and programmatic control.

### Compose path

```kotlin
@Composable
fun VideoScreen(mediaCode: String) {
    val controller = rememberBoomstreamPlayerController()

    BoomstreamPlayer(
        mediaCode = mediaCode,
        configClient = Boomstream.configClient,
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        controller = controller,
    )

    // Observe progress
    val progress by controller.progressFlow.collectAsState()

    // Observe events
    LaunchedEffect(controller) {
        controller.events.collect { event -> /* handle */ }
    }
}
```

`rememberBoomstreamPlayerController()` returns a stable instance across recompositions. Control methods (`play`, `pause`, `seekTo`, …) are no-ops until the composable has attached the controller.

### View path

```kotlin
val playerView = findViewById<BoomstreamPlayerView>(R.id.player)
playerView.load(mediaCode = "Il4lNOfL", configClient = Boomstream.configClient)

val controller: BoomstreamPlayerController = playerView.controller

// Convenience proxies also available directly on the view:
playerView.play()
playerView.pause()
playerView.seekTo(30_000L)
```

The `controller` property is stable across `load()` calls — safe to capture before calling `load()`.

---

## Events

`BoomstreamPlayerController.events` is a hot `SharedFlow<PlayerEvent>` with `replay = 0`.
Late subscribers **do not** receive past events — start collecting before playback if you need `Loaded`.

| Event | Fields | When emitted |
|---|---|---|
| `PlayerEvent.Loaded` | `durationMs: Long` | Media is ready and duration is known. Emitted once per media item on first `STATE_READY`. |
| `PlayerEvent.Playing` | `positionMs: Long` | Playback started or resumed (play/autoplay). |
| `PlayerEvent.Paused` | `positionMs: Long` | User or host paused playback. Not emitted when playback ends naturally. |
| `PlayerEvent.Ended` | — | All items in the queue have finished playing. |
| `PlayerEvent.Progress` | `positionMs: Long`, `durationMs: Long`, `percent: Float` | Periodic progress tick (~300 ms) while playing. Prefer `progressFlow` for UI binding — it is a `StateFlow` and always holds the latest value. |
| `PlayerEvent.Seeked` | `positionMs: Long` | A programmatic or user-initiated seek completed. |
| `PlayerEvent.FullScreenChanged` | `isFullScreen: Boolean` | Fullscreen state changed via `setFullScreen()` or `toggleFullScreen()`. |
| `PlayerEvent.QualityChanged` | `quality: VideoQuality` | Active video quality changed via `selectQuality()` or `selectAuto()`. Available in `player-sdk` v1.5.0+. |

`percent` in `Progress` is in the range `[0f..1f]`.

---

## Progress

`BoomstreamPlayerController.progressFlow` is a `StateFlow<PlaybackProgress>` — always holds the latest value, safe for late subscribers and `collectAsState()`.

```kotlin
data class PlaybackProgress(
    val positionMs: Long,   // current playback position in ms
    val durationMs: Long,   // total duration in ms; -1 if not yet known
    val percent: Float,     // [0f..1f]; 0f when durationMs ≤ 0
)
```

Updated every ~300 ms while playing. The polling loop freezes when paused — no unnecessary updates.
`durationMs` is `-1` until `PlayerEvent.Loaded` arrives or for live streams with indeterminate length.

---

## Control calls

All methods must be called from the **main thread** (mirrors the ExoPlayer threading contract).
Methods are no-ops when the player has not yet loaded media, unless otherwise noted.

| Method | Parameters | Effect |
|---|---|---|
| `getCurrentPosition()` | — | Returns current position in ms, or `0` if not yet loaded. |
| `getDuration()` | — | Returns total duration in ms, or `-1` if unknown. |
| `play()` | — | Resumes playback. |
| `pause()` | — | Pauses playback. |
| `seekTo(positionMs)` | `positionMs: Long` | Seeks to absolute position in ms. |
| `seekToPercent(percent)` | `percent: Float` | Seeks to `percent × duration`. Clamped to `[0f..1f]`. No-op if duration unknown. |
| `setVolume(percent)` | `percent: Int` | Sets audio volume. Clamped to `[0..100]`. |
| `mute()` | — | Mutes audio (equivalent to `setVolume(0)`). |
| `unmute()` | — | Restores full volume (equivalent to `setVolume(100)`). |
| `next()` | — | Advances to next playlist item. No-op for single-item media. |
| `previous()` | — | Returns to previous playlist item. No-op for single-item media. |
| `setFullScreen(isFullScreen)` | `isFullScreen: Boolean` | Sets fullscreen state; emits `PlayerEvent.FullScreenChanged`. The SDK does **not** change orientation or window flags — the host Activity owns that. |
| `toggleFullScreen()` | — | Toggles fullscreen state; emits `PlayerEvent.FullScreenChanged`. |

---

## Recipes

### Progress bar + 70% trigger

The canonical client-side completion-trigger pattern:

```kotlin
@Composable
fun VideoScreen(mediaCode: String) {
    val controller = rememberBoomstreamPlayerController()
    val progress by controller.progressFlow.collectAsState()
    var triggered70 by remember(mediaCode) { mutableStateOf(false) }

    // 70% trigger — reset when user seeks back below threshold
    LaunchedEffect(controller) {
        controller.progressFlow.collect { p ->
            if (p.percent >= 0.7f && !triggered70) {
                triggered70 = true
                // fire your campaign / completion webhook here
            }
        }
    }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            if (event is PlayerEvent.Seeked) {
                val dur = controller.getDuration()
                if (dur > 0L && event.positionMs.toFloat() / dur < 0.7f) triggered70 = false
            }
        }
    }

    BoomstreamPlayer(
        mediaCode = mediaCode,
        configClient = Boomstream.configClient,
        controller = controller,
    )
    LinearProgressIndicator(
        progress = { progress.percent.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (triggered70) {
        Text("✅ 70% reached")
    }
}
```

### Basic play / pause / seek

```kotlin
val controller = rememberBoomstreamPlayerController()

// Play / pause
controller.play()
controller.pause()

// Seek to absolute position
controller.seekTo(30_000L)   // 30 seconds

// Seek to percentage
controller.seekToPercent(0.5f) // 50%

// Volume
controller.setVolume(80)  // 80%
controller.mute()
controller.unmute()
```

For the View path, call the same methods on `playerView.controller` or directly on the view:

```kotlin
playerView.play()
playerView.seekTo(30_000L)
playerView.setVolume(80)
```

### Fullscreen handling

The SDK emits `PlayerEvent.FullScreenChanged` and leaves orientation/window management to the host Activity.

```kotlin
LaunchedEffect(controller) {
    controller.events.collect { event ->
        if (event is PlayerEvent.FullScreenChanged) {
            val activity = context as? Activity
            activity?.requestedOrientation = if (event.isFullScreen) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
}

// Wire the fullscreen button (pass a non-null lambda to show the built-in button)
BoomstreamPlayer(
    mediaCode = mediaCode,
    configClient = Boomstream.configClient,
    controller = controller,
    onFullscreenToggle = {}, // SDK calls controller.toggleFullScreen() internally
)
```

---

## Web → Native mapping

For teams migrating from the Boomstream web Player API or coordinating between web and native:

| Web event / call | Native equivalent | Notes |
|---|---|---|
| `player.on('ready', fn)` | `PlayerEvent.Loaded` | Native fires after first `STATE_READY`; `durationMs` is always set. |
| `player.on('play', fn)` | `PlayerEvent.Playing` | Fires on play/resume; `positionMs` included. |
| `player.on('pause', fn)` | `PlayerEvent.Paused` | Not fired on natural end (see `Ended`). |
| `player.on('ended', fn)` | `PlayerEvent.Ended` | Fires after all items in queue. |
| `player.on('timeupdate', fn)` | `PlayerEvent.Progress` / `progressFlow` | Prefer `progressFlow` for UI binding (StateFlow, no missed events). |
| `player.on('seeked', fn)` | `PlayerEvent.Seeked` | Fires after seek completes. |
| `player.on('fullscreenchange', fn)` | `PlayerEvent.FullScreenChanged` | Native fires on programmatic or user toggle only. |
| `player.play()` | `controller.play()` | — |
| `player.pause()` | `controller.pause()` | — |
| `player.seek(seconds)` | `controller.seekTo(seconds * 1000L)` | Web uses seconds; native uses **milliseconds**. |
| `player.setVolume(0..1)` | `controller.setVolume((v * 100).toInt())` | Web uses `[0..1]`; native uses `[0..100]`. |
| `player.mute()` / `player.unmute()` | `controller.mute()` / `controller.unmute()` | — |
| `player.next()` / `player.previous()` | `controller.next()` / `controller.previous()` | Playlist only. |
| `player.toggleFullscreen()` | `controller.toggleFullScreen()` | Native does not change orientation automatically. |
| `player.getCurrentTime()` | `controller.getCurrentPosition() / 1000.0` | Web returns seconds; native returns **milliseconds**. |
| `player.getDuration()` | `controller.getDuration() / 1000.0` | Web returns seconds; native returns **milliseconds** (`-1` if unknown). |

---

## Surface type (SurfaceView vs TextureView)

_Available in `player-sdk` v1.4.0+._

The player renders into a Media3 `PlayerView`, which can be backed either by a **`SurfaceView`**
(the default) or a **`TextureView`**. The choice is exposed via `BoomstreamSurfaceType`.

| | `SURFACE_VIEW` (default) | `TEXTURE_VIEW` |
|---|---|---|
| Power / performance | Best — dedicated compositing layer | Slightly higher (off-screen composite) |
| HDR / secure (Widevine L1) surfaces | Supported | **Not** supported |
| Behaviour on rotation / relayout | Surface is destroyed & recreated; Media3 re-attaches the codec to the new surface | Surface **survives** relayout — no codec hand-off |
| Rare native crash on rotation | Possible on some vendor codecs (see below) | Avoided |

### When to choose `TEXTURE_VIEW`

On a small number of vendor codec HALs — observed on some **Android 16** devices — the
`SurfaceView` surface hand-off on device rotation can crash the **native** codec process. The symptom
is a hard `PROCESS ENDED` with **no Java stack trace** (the JVM never sees it; it's a native
`SIGSEGV`), sometimes right after `onConfigurationChanged`. Switching to `TEXTURE_VIEW` renders into
a `SurfaceTexture` that survives relayout without destroying/recreating the surface, sidestepping the
hand-off entirely. Boomstream's Clear Key playback is non-secure, so the loss of secure-surface
support does not apply.

### How to set it

**Compose** — the `surfaceType` parameter:

```kotlin
BoomstreamPlayer(
    mediaCode = mediaCode,
    configClient = Boomstream.configClient,
    surfaceType = BoomstreamSurfaceType.TEXTURE_VIEW,
)
```

**View — XML attribute** (recommended; applied before the first frame):

```xml
<com.boomstream.sdk.player.BoomstreamPlayerView
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/player"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:boomstreamSurfaceType="texture_view" />
```

**View — in code** (set before `load()`):

```kotlin
val player = findViewById<BoomstreamPlayerView>(R.id.player)
player.surfaceType = BoomstreamSurfaceType.TEXTURE_VIEW
player.load(mediaCode = "Il4lNOfL", configClient = Boomstream.configClient)
```

Assigning `surfaceType` after playback has begun transparently recreates the internal `PlayerView`
and re-attaches the current player, but the switch is briefly visible — prefer setting it up front.

If you let the user pick the surface type at runtime in Compose, hold the choice somewhere that
survives configuration changes — a `ViewModel` is the safest home. A plain `remember`/`rememberSaveable`
inside a screen that is rebuilt on rotation (e.g. a tab/pager page) can snap back to the default; the
`ViewModel` is retained across configuration-change recreation and keeps the choice stable. (The XML
attribute on `BoomstreamPlayerView` needs no such care — it is re-read on every inflation.)

> **Interim workaround without an SDK upgrade:** removing `orientation` from the host Activity's
> `android:configChanges` lets Android recreate the Activity on rotation instead of driving a live
> surface hand-off, which also avoids the crash. Prefer `TEXTURE_VIEW` for a seamless rotation.

---

## Video quality selection

_Available in `player-sdk` v1.5.0+._

The controller exposes the HLS renditions discovered from the master manifest for programmatic
selection, plus an opt-in Quality row in the built-in settings panel.

### Model

```kotlin
sealed class VideoQuality {
    /** Adaptive quality — ExoPlayer picks the best rendition for the current bandwidth. */
    object Auto : VideoQuality()

    /** Locked to a specific rendition. */
    data class Resolution(
        val height: Int,        // vertical pixels, e.g. 1080, 720, 480
        val bitrate: Long = -1L, // peak bitrate in bits/s, -1 if unknown
        val label: String = "${height}p",
    ) : VideoQuality()
}
```

The type is intentionally **media3-free** — no `androidx.media3.common.Format` /
`TrackSelectionParameters` leaks into the public API (CSO constraint #1).

### Controller surface

| Member | Type | Description |
|---|---|---|
| `availableQualities` | `StateFlow<List<VideoQuality>>` | Renditions from the current media's HLS master manifest. Empty until the first track-ready event; reset to empty on each `load()`. Sorted highest-resolution first. |
| `currentQuality` | `StateFlow<VideoQuality>` | Currently selected quality. `VideoQuality.Auto` by default and after each `load()`. Updated synchronously by `selectQuality` / `selectAuto`. |
| `selectQuality(quality)` | `fun` | Locks playback to `quality`. Takes effect on the next segment boundary — no reload. Emits `PlayerEvent.QualityChanged`. No-op if the player has not yet loaded media. |
| `selectAuto()` | `fun` | Clears the override and returns to adaptive bitrate selection. Emits `PlayerEvent.QualityChanged` with `VideoQuality.Auto`. No-op if the player has not yet loaded media. |
| `PlayerEvent.QualityChanged` | `event` | Fires whenever the active quality changes via `selectQuality` / `selectAuto`. |

`availableQualities` reflects **only** the renditions the server advertises in the HLS master
manifest — it does not manufacture options. For a single-rendition stream the list stays
empty and the Quality row in the settings panel (if enabled) is hidden.

### Reading available options

`availableQualities` starts empty and populates once the first track-ready event arrives.
Consumers should collect it as a `StateFlow` rather than reading once at load time.

```kotlin
val controller = rememberBoomstreamPlayerController()
val options by controller.availableQualities.collectAsState()
val current by controller.currentQuality.collectAsState()

// options is empty until playback is ready
if (options.isNotEmpty()) {
    Row {
        Text("Current: ${(current as? VideoQuality.Resolution)?.label ?: "Auto"}")
        options.forEach { q ->
            when (q) {
                is VideoQuality.Resolution -> Button(onClick = { controller.selectQuality(q) }) {
                    Text(q.label)
                }
                VideoQuality.Auto -> Unit // Auto is always available via selectAuto()
            }
        }
        Button(onClick = { controller.selectAuto() }) { Text("Auto") }
    }
}
```

### Reacting to changes

```kotlin
LaunchedEffect(controller) {
    controller.events.collect { event ->
        if (event is PlayerEvent.QualityChanged) {
            when (val q = event.quality) {
                is VideoQuality.Resolution -> analytics.log("quality:${q.height}p")
                VideoQuality.Auto -> analytics.log("quality:auto")
            }
        }
    }
}
```

### Quality in the player settings panel (opt-in)

The player's gear button (⚙) opens a unified settings panel containing **Speed** and, when
multiple audio tracks are present, **Audio**. Set `AdvancedPlayerOptions.enableQualitySelector = true`
to add a **Quality** row to that same panel. There is no separate quality button — everything lives
behind one gear icon, which is what the client requested.

```kotlin
// Compose
BoomstreamPlayer(
    mediaCode = mediaCode,
    configClient = Boomstream.configClient,
    advancedOptions = AdvancedPlayerOptions(enableQualitySelector = true),
)

// View — pass advancedOptions to load()
playerView.load(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    advancedOptions = AdvancedPlayerOptions(enableQualitySelector = true),
)
```

The default is `false` — only the programmatic API is exposed. The Quality row is hidden
automatically when the HLS master manifest contains a single rendition (`availableQualities`
stays empty).

---

## Styling / theming

_Available in `player-sdk` v1.6.0+._

Pass a `BoomstreamPlayerStyle` to control the visual appearance of the player. All fields are
nullable — `null` keeps the SDK default for that colour.

### Style model

```kotlin
class BoomstreamPlayerStyle(
    @ColorInt val loaderColor: Int? = null,           // loading spinner tint
    @ColorInt val accentColor: Int? = null,           // control buttons (best-effort)
    @ColorInt val seekBarPlayedColor: Int? = null,    // played portion of the seek bar
    @ColorInt val seekBarScrubberColor: Int? = null,  // seek bar thumb
    @ColorInt val seekBarBufferedColor: Int? = null,  // buffered portion of the seek bar
    @ColorInt val messageTextColor: Int? = null,      // system-message overlay text
    @ColorInt val messageBackgroundColor: Int? = null // system-message overlay background
)
```

All colours use `@ColorInt Int` — no `androidx.media3.*` types are exposed (CSO constraint #1).

**Accent note:** `accentColor` is applied best-effort to Media3 playback control button icons
(play, pause, previous, next, fast-forward, rewind, settings, fullscreen). Coverage depends on
the Media3 version and the host app's theme — some tints may be overridden by the theme's
`colorControlNormal`.

### Compose — `style` parameter

```kotlin
val brandViolet = Color(0xFF662BFF).toArgb()

BoomstreamPlayer(
    mediaCode = "Il4lNOfL",
    configClient = Boomstream.configClient,
    style = BoomstreamPlayerStyle(
        loaderColor = brandViolet,
        accentColor = brandViolet,
        seekBarPlayedColor = brandViolet,
        seekBarScrubberColor = brandViolet,
    ),
)
```

Compose callers convert `androidx.compose.ui.graphics.Color` values via `.toArgb()`.

### View — XML attributes

```xml
<com.boomstream.sdk.player.BoomstreamPlayerView
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/player"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:boomstreamLoaderColor="@color/brand_violet"
    app:boomstreamAccentColor="@color/brand_violet"
    app:boomstreamSeekBarPlayedColor="@color/brand_violet"
    app:boomstreamSeekBarScrubberColor="@color/brand_violet"
    app:boomstreamMessageTextColor="@android:color/white"
    app:boomstreamMessageBackgroundColor="#CC000000" />
```

### View — programmatic

Set the `style` property (replaces the whole style object) or use the point-change setters
(preserve all other fields):

```kotlin
val player = findViewById<BoomstreamPlayerView>(R.id.player)

// Replace entire style:
player.style = BoomstreamPlayerStyle(
    loaderColor = ContextCompat.getColor(this, R.color.brand_violet),
    accentColor = ContextCompat.getColor(this, R.color.brand_violet),
)

// Point change (preserves other style fields):
player.setLoaderColor(ContextCompat.getColor(this, R.color.brand_violet))
player.setAccentColor(ContextCompat.getColor(this, R.color.brand_violet))
player.setSeekBarPlayedColor(ContextCompat.getColor(this, R.color.brand_violet))
player.setSeekBarScrubberColor(ContextCompat.getColor(this, R.color.brand_violet))
player.setSeekBarBufferedColor(ContextCompat.getColor(this, R.color.brand_buffered))
player.setMessageTextColor(Color.WHITE)
player.setMessageBackgroundColor(Color.argb(0xCC, 0, 0, 0))
```

Style changes apply immediately — no reload required. The style survives `surfaceType` toggles.

### XML attribute reference

| Attribute | Surfaces | Notes |
|---|---|---|
| `boomstreamLoaderColor` | Loading spinner | Full control via `ProgressBar.indeterminateTintList` |
| `boomstreamAccentColor` | Control button icons | Best-effort icon tint |
| `boomstreamSeekBarPlayedColor` | Seek bar played portion | Media3 `DefaultTimeBar.setPlayedColor` |
| `boomstreamSeekBarScrubberColor` | Seek bar thumb | Media3 `DefaultTimeBar.setScrubberColor` |
| `boomstreamSeekBarBufferedColor` | Seek bar buffered portion | Media3 `DefaultTimeBar.setBufferedColor` |
| `boomstreamMessageTextColor` | Overlay banner text | Full control |
| `boomstreamMessageBackgroundColor` | Overlay banner background | Full control; default `#CC000000` |

---

## Google Cast

> **v1 limitation — unprotected content only.** Cast v1 uses the default Chromecast receiver
> (`CC1AD845`) which streams HLS without DRM. Projects with download-protection disabled work
> out of the box. DRM-protected playback on Cast requires a custom receiver and is planned for
> a future release.

### 0. Declare and request nearby-device permissions (Android 13+)

> **Required on Android 13+ (API 33+).** Google Cast uses mDNS (`_googlecast._tcp.local`)
> to discover devices on the local network. On API 33+, this requires the
> `NEARBY_WIFI_DEVICES` runtime permission. Without it the Cast picker shows
> **"No devices found"** even when a Chromecast is on the same Wi-Fi network.

The Boomstream Player SDK declares both permissions in its AAR merged manifest, so they
are automatically merged into your app. If you target a `minSdk` below 33 and want to
be explicit, add them to your own `AndroidManifest.xml` as well:

```xml
<!-- Required for Cast mDNS discovery on Android 13+ (API 33+). -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" />
<!-- Required by the Cast framework for network state checks. -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

`android:usesPermissionFlags="neverForLocation"` tells the system that your app does
not infer physical location from Wi-Fi scan results, which avoids the full
location-permission rationale dialog on supported devices.

**You must also request `NEARBY_WIFI_DEVICES` at runtime on API 33+.** The manifest
declaration alone is not sufficient — `NEARBY_WIFI_DEVICES` is a dangerous permission
and requires an explicit runtime grant. Request it before the user opens the Cast picker:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

// Inside the Composable that shows your Cast button:
val context = LocalContext.current
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    // Update UI / log if needed
}

LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.NEARBY_WIFI_DEVICES
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}
```

### 1. Register the options provider in your manifest

Add the following `<meta-data>` inside the `<application>` block of your
`AndroidManifest.xml`. This tells the Cast framework to use Boomstream's `CastOptions`
(the default receiver app ID) and must be present for `CastContext` to initialise.

```xml
<meta-data
    android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
    android:value="com.boomstream.sdk.player.BoomstreamCastOptionsProvider" />
```

### 2. Use an AppCompat activity and theme

`MediaRouteButton` requires the host activity to extend `AppCompatActivity` and the
window theme to inherit from an AppCompat variant:

```xml
<!-- res/values/themes.xml -->
<style name="Theme.MyApp" parent="Theme.AppCompat.Light.NoActionBar">
    …
</style>
```

```kotlin
class MainActivity : AppCompatActivity() { … }
```

If you already use `AppCompatActivity` (common in projects with fragments or Material
Components), no change is needed.

### 3. Add your own `MediaRouteButton`

The SDK does **not** inject a Cast button — you own the UI. Place a `MediaRouteButton`
wherever your design requires it.

> **Required:** call `CastButtonFactory.setUpMediaRouteButton(context, button)` on every
> `MediaRouteButton` you create. This wires the button to the Cast route selector. **Without
> it the button's chooser uses an empty selector and shows "No devices available" even though
> `CastContext` discovers nearby Chromecasts.** Initialising `CastContext` alone does **not**
> configure the button.

`CastButtonFactory` comes from `com.google.android.gms:play-services-cast-framework` — add
that dependency to your app module (the SDK keeps it as `implementation` so no Cast types leak
onto its public API).

**Compose (via `AndroidView`):**

```kotlin
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

AndroidView(
    factory = { ctx ->
        MediaRouteButton(ctx).also { button ->
            CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, button)
        }
    },
    modifier = Modifier.size(48.dp),
)
```

**XML layout:**

```xml
<androidx.mediarouter.app.MediaRouteButton
    android:id="@+id/castButton"
    android:layout_width="48dp"
    android:layout_height="48dp" />
```

```kotlin
// In onCreate(), after setContentView:
CastButtonFactory.setUpMediaRouteButton(applicationContext, findViewById(R.id.castButton))
```

### 4. Observe Cast state from the controller

`BoomstreamPlayerController` exposes two `StateFlow` properties:

| Property | Type | Description |
|---|---|---|
| `isCasting` | `StateFlow<Boolean>` | `true` while playback is routed to a Chromecast device |
| `castDeviceName` | `StateFlow<String?>` | Friendly name of the connected device, or `null` when not casting |

Both flows update synchronously — `castDeviceName` is non-null exactly when `isCasting` is
`true`.

```kotlin
val isCasting by controller.isCasting.collectAsState()
val deviceName by controller.castDeviceName.collectAsState()

if (isCasting) {
    Text("Casting to ${deviceName ?: "Chromecast"}")
}
```

The player handles session handoff automatically: when a Cast session starts, local
playback hands off to the receiver and resumes from the current position; when the
session ends, playback returns to the device.

---

## Backward compatibility

- `BoomstreamPlayerView.stateFlow` remains unchanged and is not deprecated.
- `BoomstreamPlayerController.state` mirrors `stateFlow` for consumers who only hold the controller.
- `PlayerEvent.Progress` is available on `events` but `progressFlow` is the recommended primary source for UI because it is a `StateFlow` and never misses the latest value on late subscription.
