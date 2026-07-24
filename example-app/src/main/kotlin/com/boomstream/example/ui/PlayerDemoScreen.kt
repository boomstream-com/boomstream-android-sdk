package com.boomstream.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.boomstream.example.MainViewModel
import com.boomstream.sdk.api.Boomstream
import com.boomstream.sdk.player.AdvancedPlayerOptions
import com.boomstream.sdk.player.BoomstreamPlayer
import com.boomstream.sdk.player.BoomstreamPlayerStyle
import com.boomstream.sdk.player.BoomstreamSurfaceType
import com.boomstream.sdk.player.PlayerEvent
import com.boomstream.sdk.player.VideoQuality
import com.boomstream.sdk.player.rememberBoomstreamPlayerController

@Composable
fun PlayerDemoScreen(vm: MainViewModel) {
    val selectedCode by vm.selectedMediaCode.collectAsState()
    val context = LocalContext.current

    val controller = rememberBoomstreamPlayerController()
    val progress by controller.progressFlow.collectAsState()
    val availableQualities by controller.availableQualities.collectAsState()
    val currentQuality by controller.currentQuality.collectAsState()

    // Fullscreen tracking — synced from FullScreenChanged events
    var isFullScreen by remember { mutableStateOf(false) }

    // Volume slider state (0f..1f maps to 0..100)
    var volumeValue by remember { mutableFloatStateOf(1f) }

    // Surface backing toggle — flip to TextureView to test the rotation-crash workaround.
    // Held in the ViewModel so the choice survives the configuration-change recreation on rotation
    // (local remember/rememberSaveable snapped back to the default because the tab is rebuilt).
    // The player is recreated on change via key(surfaceType).
    val surfaceType by vm.surfaceType.collectAsState()
    val locale by vm.locale.collectAsState()

    // 70% trigger: auto-resets when selectedCode changes (remember key)
    var triggered70 by remember(selectedCode) { mutableStateOf(false) }

    // Event log: last 5 discrete events as formatted strings
    val eventLog = remember { mutableStateListOf<String>() }

    // Collect progressFlow for 70% trigger detection
    LaunchedEffect(controller) {
        controller.progressFlow.collect { p ->
            if (p.percent >= 0.7f && !triggered70) {
                triggered70 = true
            }
        }
    }

    // Collect events for: 70% seek-reset, fullscreen orientation, event log
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is PlayerEvent.Seeked -> {
                    val duration = controller.getDuration()
                    if (duration > 0L && event.positionMs.toFloat() / duration < 0.7f) {
                        triggered70 = false
                    }
                }
                is PlayerEvent.FullScreenChanged -> {
                    isFullScreen = event.isFullScreen
                    val activity = context as? Activity
                    activity?.requestedOrientation = if (event.isFullScreen) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
                else -> Unit
            }
            eventLog.add(0, formatEvent(event))
            if (eventLog.size > 5) eventLog.removeRange(5, eventLog.size)
        }
    }

    if (isFullScreen) {
        // Fullscreen layout: player fills the screen, system bars hidden
        FullscreenPlayerPane(
            mediaCode = selectedCode ?: "",
            vm = vm,
            controller = controller,
            surfaceType = surfaceType,
            locale = locale,
        )
        return
    }

    // Portrait layout: scrollable demo with player + controls
    if (selectedCode == null) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Выберите медиа на вкладке «Медиа»",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val mediaCode = selectedCode!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Player ──────────────────────────────────────────────────────────
        // key(surfaceType) recreates the player when the surface backing is toggled below.
        key(surfaceType) {
            BoomstreamPlayer(
                mediaCode = mediaCode,
                configClient = Boomstream.configClient,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                offlineCache = vm.offlineCache,
                surfaceType = surfaceType,
                locale = locale,
                // Demo: Boomstream violet (#662BFF) applied to loader, accent, and seek bar.
                // enableQualitySelector=true adds a Quality row to the settings panel (⚙).
                advancedOptions = AdvancedPlayerOptions(enableQualitySelector = true),
                style = BoomstreamPlayerStyle(
                    loaderColor = Color(0xFF662BFF).toArgb(),
                    accentColor = Color(0xFF662BFF).toArgb(),
                    seekBarPlayedColor = Color(0xFF662BFF).toArgb(),
                    seekBarScrubberColor = Color(0xFF662BFF).toArgb(),
                ),
                controller = controller,
                // onFullscreenToggle is non-null so the built-in fullscreen button is shown.
                // Orientation change is handled by the FullScreenChanged event collector above.
                onFullscreenToggle = {},
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── Surface type toggle (rotation-crash workaround) ──────────────
            Text(
                text = "Тип поверхности видео",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isTexture = surfaceType == BoomstreamSurfaceType.TEXTURE_VIEW
                val onSurface = { vm.setSurfaceType(BoomstreamSurfaceType.SURFACE_VIEW) }
                val onTexture = { vm.setSurfaceType(BoomstreamSurfaceType.TEXTURE_VIEW) }
                if (!isTexture) {
                    Button(onClick = onSurface, modifier = Modifier.weight(1f)) { Text("SurfaceView") }
                    OutlinedButton(onClick = onTexture, modifier = Modifier.weight(1f)) { Text("TextureView") }
                } else {
                    OutlinedButton(onClick = onSurface, modifier = Modifier.weight(1f)) { Text("SurfaceView") }
                    Button(onClick = onTexture, modifier = Modifier.weight(1f)) { Text("TextureView") }
                }
            }
            Text(
                text = "Текущий: ${
                    if (surfaceType == BoomstreamSurfaceType.TEXTURE_VIEW) {
                        "TextureView"
                    } else {
                        "SurfaceView (по умолчанию)"
                    }
                } — поверните экран для проверки",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Progress bar ─────────────────────────────────────────────────
            Text(
                text = "Прогресс",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress.percent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = formatProgress(progress.positionMs, progress.durationMs, progress.percent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── 70% trigger banner ───────────────────────────────────────────
            if (triggered70) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "✅ Достигнуто 70% видео — клиентский триггер сработал бы здесь",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Playback controls ────────────────────────────────────────────
            Text(
                text = "Управление",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Play / Pause
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { controller.play() }, modifier = Modifier.weight(1f)) {
                    Text("▶ Play")
                }
                Button(onClick = { controller.pause() }, modifier = Modifier.weight(1f)) {
                    Text("⏸ Pause")
                }
            }

            // Seek buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val pos = maxOf(0L, progress.positionMs - 10_000L)
                        controller.seekTo(pos)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("« −10s")
                }
                OutlinedButton(
                    onClick = { controller.seekTo(progress.positionMs + 10_000L) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+10s »")
                }
                OutlinedButton(
                    onClick = { controller.seekToPercent(0.5f) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("50%")
                }
            }

            // Mute / Unmute / Fullscreen
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { controller.mute() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("🔇 Mute")
                }
                OutlinedButton(
                    onClick = { controller.unmute() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("🔊 Unmute")
                }
                OutlinedButton(
                    onClick = {
                        // Use explicit state to avoid sync issues with player's internal toggle.
                        controller.setFullScreen(!isFullScreen)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("⛶ FullScr")
                }
            }

            // Volume slider
            Text(
                text = "Громкость: ${(volumeValue * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = volumeValue,
                onValueChange = { v ->
                    volumeValue = v
                    controller.setVolume((v * 100).toInt())
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Quality selection (Player API demo) ──────────────────────────
            Text(
                text = "Качество видео",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val currentLabel = when (val q = currentQuality) {
                is VideoQuality.Auto -> "Auto"
                is VideoQuality.Resolution -> q.label
            }
            Text(
                text = "Текущее: $currentLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (availableQualities.isEmpty()) {
                Text(
                    text = "— варианты появятся после загрузки медиа —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            } else {
                // Auto button + one button per quality rendition
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isAuto = currentQuality is VideoQuality.Auto
                    if (isAuto) {
                        Button(
                            onClick = { controller.selectAuto() },
                        ) { Text("Auto") }
                    } else {
                        OutlinedButton(
                            onClick = { controller.selectAuto() },
                        ) { Text("Auto") }
                    }
                    availableQualities.forEach { quality ->
                        if (quality is VideoQuality.Resolution) {
                            val isSelected = currentQuality == quality
                            if (isSelected) {
                                Button(
                                    onClick = { controller.selectQuality(quality) },
                                ) { Text(quality.label) }
                            } else {
                                OutlinedButton(
                                    onClick = { controller.selectQuality(quality) },
                                ) { Text(quality.label) }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Event log ────────────────────────────────────────────────────
            Text(
                text = "События (последние 5)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (eventLog.isEmpty()) {
                Text(
                    text = "— нажмите Play, чтобы начать воспроизведение —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            } else {
                eventLog.forEach { entry ->
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Fullscreen layout ─────────────────────────────────────────────────────────

@Composable
private fun FullscreenPlayerPane(
    mediaCode: String,
    vm: MainViewModel,
    controller: com.boomstream.sdk.player.BoomstreamPlayerController,
    surfaceType: BoomstreamSurfaceType,
    locale: String,
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsCtrl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsCtrl?.let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsCtrl?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        key(surfaceType) {
            BoomstreamPlayer(
                mediaCode = mediaCode,
                configClient = Boomstream.configClient,
                modifier = Modifier.fillMaxSize(),
                offlineCache = vm.offlineCache,
                surfaceType = surfaceType,
                locale = locale,
                controller = controller,
                // Fullscreen button exits fullscreen — orientation handled by FullScreenChanged event.
                onFullscreenToggle = {},
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatProgress(positionMs: Long, durationMs: Long, percent: Float): String {
    val posStr = formatMs(positionMs)
    val durStr = if (durationMs > 0L) formatMs(durationMs) else "--:--"
    val pct = (percent * 100).toInt()
    return "$posStr / $durStr ($pct%)"
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1_000L
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

private fun formatEvent(event: PlayerEvent): String = when (event) {
    is PlayerEvent.Loaded -> "Loaded  duration=${formatMs(event.durationMs)}"
    is PlayerEvent.Playing -> "Playing pos=${formatMs(event.positionMs)}"
    is PlayerEvent.Paused -> "Paused  pos=${formatMs(event.positionMs)}"
    is PlayerEvent.Ended -> "Ended"
    is PlayerEvent.Progress -> "Progress ${(event.percent * 100).toInt()}%"
    is PlayerEvent.Seeked -> "Seeked  pos=${formatMs(event.positionMs)}"
    is PlayerEvent.FullScreenChanged -> "FullScreenChanged  fs=${event.isFullScreen}"
    is PlayerEvent.QualityChanged -> when (val q = event.quality) {
        is VideoQuality.Auto -> "QualityChanged  Auto"
        is VideoQuality.Resolution -> "QualityChanged  ${q.label}"
    }
}
