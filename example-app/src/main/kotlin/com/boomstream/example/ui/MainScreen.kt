package com.boomstream.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.boomstream.example.BuildConfig
import com.boomstream.example.ContentItem
import com.boomstream.example.MainViewModel
import com.boomstream.example.R
import com.boomstream.sdk.api.Boomstream
import com.boomstream.sdk.offline.DownloadState
import com.boomstream.sdk.player.BoomstreamPlayer

@Composable
fun MainScreen(vm: MainViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val apiKeyMissing by vm.apiKeyMissing.collectAsState()

    if (apiKeyMissing) {
        NoApiKeyScreen()
        return
    }

    // movableContentOf preserves the player's ExoPlayer instance and buffered media when
    // the layout switches between portrait and landscape branches. Without this, the
    // if/else branches would each own a separate composition slot, causing the player to
    // be destroyed and recreated on every rotation — triggering a fresh config fetch that
    // fails with a DNS error when the device is offline.
    val boomPlayer = remember(vm) {
        movableContentOf<Modifier> { modifier ->
            val selectedCode by vm.selectedMediaCode.collectAsState()
            val locale by vm.locale.collectAsState()
            // Read LocalContext and LocalConfiguration inside this composable body so that
            // (a) context is always the current Activity, and (b) isLandscape is re-evaluated
            // on every orientation change, giving onFullscreenToggle a fresh capture.
            val context = LocalContext.current
            val playerIsLandscape =
                LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val code = selectedCode
            if (code != null) {
                BoomstreamPlayer(
                    mediaCode = code,
                    configClient = Boomstream.configClient,
                    modifier = modifier,
                    // allowClearKeyDRMtoken omitted — token is set via BoomstreamOptions.userAgentToken
                    // in ExampleApp.onCreate and carried automatically by Boomstream.configClient.
                    offlineCache = vm.offlineCache,
                    locale = locale,
                    onFullscreenToggle = {
                        val activity = context as? Activity
                        activity?.requestedOrientation = if (playerIsLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                )
            } else {
                Box(
                    modifier = modifier.background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }

    if (isLandscape) {
        LandscapePlayer(vm = vm, player = boomPlayer)
    } else {
        PortraitLayout(vm = vm, player = boomPlayer)
    }
}

// ── Portrait layout ───────────────────────────────────────────────────────────

@Composable
private fun PortraitLayout(vm: MainViewModel, player: @Composable (Modifier) -> Unit) {
    val mediaTitle by vm.mediaTitle.collectAsState()
    val mediaDescription by vm.mediaDescription.collectAsState()
    val downloadState by vm.downloadState.collectAsState()
    val selectedCode by vm.selectedMediaCode.collectAsState()
    val mediaList by vm.mediaList.collectAsState()
    val liveList by vm.liveList.collectAsState()
    val playlistList by vm.playlistList.collectAsState()
    val listLoading by vm.listLoading.collectAsState()
    val listError by vm.listError.collectAsState()
    val locale by vm.locale.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Player occupies a 16:9 area at the top of the screen.
        player(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = mediaTitle ?: selectedCode ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (mediaDescription != null) {
                Text(
                    text = mediaDescription!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LocaleSwitcher(
                currentLocale = locale,
                locales = MainViewModel.SUPPORTED_LOCALES,
                onLocaleSelected = vm::setLocale,
            )

            Spacer(modifier = Modifier.height(4.dp))

            MediaPickerSection(
                items = mediaList,
                selectedCode = selectedCode,
                loading = listLoading,
                error = listError,
                label = stringResource(R.string.media_list_label),
                onSelect = vm::selectMedia,
            )

            MediaPickerSection(
                items = liveList,
                selectedCode = selectedCode,
                loading = false,
                error = null,
                label = stringResource(R.string.live_list_label),
                onSelect = vm::selectMedia,
            )

            MediaPickerSection(
                items = playlistList,
                selectedCode = selectedCode,
                loading = false,
                error = null,
                label = stringResource(R.string.playlist_list_label),
                onSelect = vm::selectMedia,
            )

            Spacer(modifier = Modifier.height(4.dp))

            OfflineDownloadSection(
                state = downloadState,
                onDownload = vm::startDownload,
                onCancel = vm::cancelDownload,
            )
        }
    }
}

// ── Landscape layout — fullscreen immersive player ────────────────────────────

@Composable
private fun LandscapePlayer(vm: MainViewModel, player: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val insetsCtrl = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
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
        player(Modifier.fillMaxSize())
    }
}

// ── Media picker section ──────────────────────────────────────────────────────

@Composable
private fun MediaPickerSection(
    items: List<ContentItem>,
    selectedCode: String?,
    loading: Boolean,
    error: String?,
    label: String,
    onSelect: (String) -> Unit,
) {
    when {
        loading && items.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        error != null && items.isEmpty() -> {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        items.isNotEmpty() -> {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items, key = { it.code }) { item ->
                    MediaCard(
                        item = item,
                        selected = item.code == selectedCode,
                        onClick = { onSelect(item.code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    item: ContentItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = shape,
        tonalElevation = if (selected) 4.dp else 1.dp,
        modifier = Modifier
            .width(140.dp)
            .clip(shape)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title.ifBlank { item.code },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.duration > 0) {
                    val min = item.duration / 60
                    val sec = item.duration % 60
                    Text(
                        text = "$min:${sec.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Offline download section ──────────────────────────────────────────────────

@Composable
private fun OfflineDownloadSection(
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        is DownloadState.NotDownloaded -> {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.download_offline))
            }
        }

        is DownloadState.Queued -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.downloading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel_download))
                }
            }
        }

        is DownloadState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel_download))
                }
            }
        }

        is DownloadState.Completed -> {
            Text(
                text = stringResource(R.string.download_complete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }

        is DownloadState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.download_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.download_offline))
                }
            }
        }

        is DownloadState.Removing -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

// ── Locale switcher ───────────────────────────────────────────────────────────

@Composable
private fun LocaleSwitcher(
    currentLocale: String,
    locales: List<String>,
    onLocaleSelected: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.locale_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        locales.forEach { loc ->
            val selected = loc == currentLocale
            Button(
                onClick = { if (!selected) onLocaleSelected(loc) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = loc.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ── No API key banner ─────────────────────────────────────────────────────────

@Composable
private fun NoApiKeyScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.no_api_key),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

