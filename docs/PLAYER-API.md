# Boomstream Player API — Events & Controls

Full reference for programmatic player observation and control in `player-sdk` v1.2.0+.

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

## Backward compatibility

- `BoomstreamPlayerView.stateFlow` remains unchanged and is not deprecated.
- `BoomstreamPlayerController.state` mirrors `stateFlow` for consumers who only hold the controller.
- `PlayerEvent.Progress` is available on `events` but `progressFlow` is the recommended primary source for UI because it is a `StateFlow` and never misses the latest value on late subscription.
