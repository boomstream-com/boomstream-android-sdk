package com.boomstream.sdk.offline.internal

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.boomstream.sdk.offline.BoomstreamOfflineConfig
import java.io.File
import java.util.concurrent.Executor

/**
 * Application-scoped singleton for the Media3 [DownloadManager] + download cache.
 *
 * Media3 requires exactly one [DownloadManager] per process and one [SimpleCache] per directory.
 * Multiple instances sharing the same directory corrupt the download database. This singleton
 * enforces the constraint.
 *
 * **Thread safety:** [getInstance] is safe to call from any thread. The instance is lazily
 * created with double-checked locking under [lock].
 *
 * **Lifecycle:** the [DownloadManager] intentionally lives for the duration of the process.
 * Releasing it while a download is in flight would corrupt the download database. Consumers
 * who need fine-grained lifecycle control should not use this SDK module.
 *
 * **Configuration:** call [configure] with the desired [userAgent] and [storageLocation]
 * **before** the first [getInstance] call (i.e. during `Application.onCreate` or before the
 * first download request). Subsequent [configure] calls are ignored once the instance is created.
 */
@OptIn(UnstableApi::class)
internal object DownloadManagerProvider {

    private val lock = Any()

    @Volatile private var userAgent: String = "BoomstreamSDK/1.0"
    @Volatile private var storageLocation: BoomstreamOfflineConfig.StorageLocation =
        BoomstreamOfflineConfig.StorageLocation.INTERNAL
    @Volatile private var downloadManager: DownloadManager? = null
    @Volatile private var downloadCache: SimpleCache? = null

    /**
     * Sets the `User-Agent` string and storage location used for downloads.
     *
     * Must be called before [getInstance]; subsequent calls after the instance is created
     * have no effect (the DownloadManager is already built). Idempotent if the instance
     * has not been created yet.
     */
    internal fun configure(
        userAgent: String,
        storageLocation: BoomstreamOfflineConfig.StorageLocation,
    ) {
        if (downloadManager == null) {
            this.userAgent = userAgent
            this.storageLocation = storageLocation
        }
    }

    /**
     * Returns (and lazily creates) the singleton [DownloadManager].
     *
     * @param context Any [Context]; the application context is extracted internally.
     */
    internal fun getInstance(context: Context): DownloadManager =
        downloadManager ?: synchronized(lock) {
            downloadManager ?: create(context.applicationContext).also { downloadManager = it }
        }

    /** Returns the shared [SimpleCache] used for storing downloaded segments. */
    internal fun getCache(context: Context): SimpleCache =
        downloadCache ?: synchronized(lock) {
            // Ensure DownloadManager is created first (it initialises the cache).
            getInstance(context)
            downloadCache!!
        }

    /**
     * Package-private factory used directly by unit tests to verify cache-root resolution
     * without going through the singleton (avoids cross-test state pollution).
     */
    internal fun create(
        appContext: Context,
        storageLocation: BoomstreamOfflineConfig.StorageLocation,
    ): DownloadManager = createInternal(appContext, storageLocation)

    private fun create(appContext: Context): DownloadManager =
        createInternal(appContext, storageLocation)

    private fun createInternal(
        appContext: Context,
        storageLocation: BoomstreamOfflineConfig.StorageLocation,
    ): DownloadManager {
        val databaseProvider = StandaloneDatabaseProvider(appContext)

        val downloadDir = when (storageLocation) {
            BoomstreamOfflineConfig.StorageLocation.INTERNAL -> appContext.filesDir
            BoomstreamOfflineConfig.StorageLocation.EXTERNAL ->
                appContext.getExternalFilesDir(null) ?: appContext.filesDir
        }
        val cache = SimpleCache(
            File(downloadDir, "boomstream_downloads"),
            NoOpCacheEvictor(),
            databaseProvider,
        ).also { downloadCache = it }

        val dataSourceFactory = OfflineDataSourceFactory(userAgent)

        return DownloadManager(
            appContext,
            databaseProvider,
            cache,
            dataSourceFactory,
            Executor(Runnable::run),
        ).apply {
            maxParallelDownloads = 3
        }
    }
}
