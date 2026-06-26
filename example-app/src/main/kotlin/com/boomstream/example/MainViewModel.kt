package com.boomstream.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boomstream.sdk.api.Boomstream
import com.boomstream.sdk.offline.BoomstreamOfflineManager
import com.boomstream.sdk.offline.DownloadState
import com.boomstream.sdk.player.BoomstreamOfflineCache
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Unified display model used by [com.boomstream.example.ui.MainScreen] picker lists. */
data class ContentItem(
    val code: String,
    val title: String = "",
    val poster: String? = null,
    val duration: Int = 0,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Locale codes shown in the demo locale switcher. */
        val SUPPORTED_LOCALES = listOf("ru", "en")
    }

    private val _apiKeyMissing = MutableStateFlow(BuildConfig.BOOMSTREAM_API_KEY.isBlank())
    val apiKeyMissing: StateFlow<Boolean> = _apiKeyMissing.asStateFlow()

    private val _locale = MutableStateFlow<String>("ru")
    /** Currently selected player locale. Passed to [BoomstreamPlayer] for system message translation. */
    val locale: StateFlow<String> = _locale.asStateFlow()

    private val _mediaList = MutableStateFlow<List<ContentItem>>(emptyList())
    val mediaList: StateFlow<List<ContentItem>> = _mediaList.asStateFlow()

    private val _listLoading = MutableStateFlow(false)
    val listLoading: StateFlow<Boolean> = _listLoading.asStateFlow()

    private val _listError = MutableStateFlow<String?>(null)
    val listError: StateFlow<String?> = _listError.asStateFlow()

    private val _liveList = MutableStateFlow<List<ContentItem>>(emptyList())
    val liveList: StateFlow<List<ContentItem>> = _liveList.asStateFlow()

    private val _playlistList = MutableStateFlow<List<ContentItem>>(emptyList())
    val playlistList: StateFlow<List<ContentItem>> = _playlistList.asStateFlow()

    /** Currently playing media code. null until the folder listing loads. */
    private val _selectedMediaCode = MutableStateFlow<String?>(null)
    val selectedMediaCode: StateFlow<String?> = _selectedMediaCode.asStateFlow()

    private val allItems = combine(_mediaList, _liveList, _playlistList) { a, b, c -> a + b + c }

    val mediaTitle: StateFlow<String?> =
        combine(_selectedMediaCode, allItems) { code, list ->
            list.find { it.code == code }?.title?.takeIf { it.isNotBlank() }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mediaDescription: StateFlow<String?> =
        combine(_selectedMediaCode, allItems) { code, list ->
            list.find { it.code == code }?.let { item ->
                if (item.duration > 0) {
                    val min = item.duration / 60
                    val sec = item.duration % 60
                    "$min:${sec.toString().padStart(2, '0')}"
                } else {
                    null
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val offlineManager: BoomstreamOfflineManager? =
        (application as ExampleApp).offlineManager

    /** Passed to [com.boomstream.sdk.player.BoomstreamPlayer] to enable offline playback. */
    val offlineCache: BoomstreamOfflineCache? = offlineManager?.let {
        BoomstreamOfflineCache { it.downloadCache }
    }

    val downloadState: StateFlow<DownloadState> = _selectedMediaCode
        .filterNotNull()
        .flatMapLatest { code ->
            offlineManager?.getDownloadState(code) ?: flowOf(DownloadState.NotDownloaded)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadState.NotDownloaded)

    init {
        if (BuildConfig.BOOMSTREAM_API_KEY.isNotBlank()) {
            loadAllMedia()
        }
    }

    private fun loadAllMedia() {
        viewModelScope.launch {
            // Load videos with full loading/error UI; live + playlists load silently.
            _listLoading.value = true
            _listError.value = null

            val videosDeferred = async {
                // Empty BOOMSTREAM_MEDIA_FOLDER => root; otherwise list from that folder code.
                Boomstream.api.listFolder(BuildConfig.BOOMSTREAM_MEDIA_FOLDER.ifBlank { null })
                    .map { items -> items.map { ContentItem(it.code, it.title, it.poster, it.duration) } }
            }
            val liveDeferred = async { Boomstream.api.listLive() }
            val playlistsDeferred = async { Boomstream.api.listPlaylists() }

            videosDeferred.await()
                .onSuccess { items ->
                    val filtered = items.filter { it.poster != null }
                    _mediaList.value = filtered
                    if (filtered.isNotEmpty()) {
                        _selectedMediaCode.value = filtered.first().code
                    }
                }
                .onFailure {
                    _listError.value = "Не удалось загрузить список медиа"
                }
            _listLoading.value = false

            liveDeferred.await()
                .onSuccess { items ->
                    _liveList.value = items
                        .map { ContentItem(it.code, it.title, it.poster) }
                        .filter { it.poster != null }
                }

            playlistsDeferred.await()
                .onSuccess { items ->
                    _playlistList.value = items
                        .map { ContentItem(it.code, it.name, it.poster, it.durationSeconds) }
                        .filter { it.poster != null }
                }
        }
    }

    fun selectMedia(code: String) {
        _selectedMediaCode.value = code
    }

    fun startDownload() {
        val code = _selectedMediaCode.value ?: return
        viewModelScope.launch {
            try {
                offlineManager?.downloadVideoOffline(code)
            } catch (_: Exception) {
                // Terminal state is reflected in downloadState flow via DownloadState.Failed.
            }
        }
    }

    fun cancelDownload() {
        val code = _selectedMediaCode.value ?: return
        offlineManager?.cancelDownload(code)
    }

    fun setLocale(locale: String) {
        _locale.value = locale
    }
}
