package com.boomstream.sdk.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.boomstream.sdk.offline.internal.DownloadManagerProvider

/**
 * Media3 [DownloadService] that manages HLS offline downloads for the Boomstream SDK.
 *
 * This service is automatically registered in the consumer app's manifest via the SDK's
 * merged `AndroidManifest.xml`. No additional manifest entry is required.
 *
 * ## Customising the foreground notification
 *
 * Override [getForegroundNotification] in a subclass and register the subclass instead:
 *
 * ```xml
 * <!-- Override the SDK-registered entry in your app's AndroidManifest.xml -->
 * <service
 *     android:name=".MyDownloadService"
 *     android:exported="false"
 *     android:foregroundServiceType="dataSync"
 *     tools:replace="android:name" />
 * ```
 *
 * ## WorkManager scheduler (optional)
 *
 * [getScheduler] returns `null` by default, meaning downloads run only while this service
 * is alive. To resume downloads after the process is killed, subclass this service and return
 * a `WorkManagerScheduler` from `media3-exoplayer-workmanager`.
 *
 * ## CSO security constraints
 *
 * - **Constraint #2:** No HTTP-logging interceptor is added to the OkHttp client used for
 *   segment downloads (enforced in [com.boomstream.sdk.offline.internal.OfflineDataSourceFactory]).
 * - **Constraint #3:** DRM keyset material is persisted via
 *   [com.boomstream.sdk.offline.internal.OfflineLicenseHelper] (EncryptedSharedPreferences).
 */
@OptIn(UnstableApi::class)
open class BoomstreamVideoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_CHANNEL_ID,
    R.string.boomstream_offline_download_channel_name,
    0,
) {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Returns the application-scoped [DownloadManager] singleton.
     *
     * [DownloadManagerProvider] is configured with the [User-Agent][configure] string set by
     * [BoomstreamOfflineManager] before the first download is requested. If no configuration
     * has been applied the default `"BoomstreamSDK/1.0"` agent is used.
     */
    override fun getDownloadManager(): DownloadManager =
        DownloadManagerProvider.getInstance(applicationContext)

    /**
     * Returns `null` — downloads are only processed while this service is alive.
     *
     * Subclasses can override to return a `WorkManagerScheduler` from
     * `androidx.media3:media3-exoplayer-workmanager` for background-scheduled downloads.
     */
    override fun getScheduler(): Scheduler? = null

    /**
     * Builds the foreground [Notification] shown while downloads are active.
     *
     * The default implementation shows a progress bar with the count of active downloads.
     * Override this in a subclass to customise the icon, text, or actions.
     *
     * @param downloads All current downloads (may include queued, downloading, completed).
     * @param notMetRequirements Bitmask of [androidx.media3.exoplayer.offline.DownloadRequest] requirements
     *   that are not currently met (e.g. network unavailable). `0` means all requirements are met.
     */
    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val active = downloads.filter { it.state == Download.STATE_DOWNLOADING }
        val progress = if (active.isNotEmpty()) {
            (active.sumOf { it.percentDownloaded.toDouble() } / active.size).toInt()
        } else {
            0
        }
        val indeterminate = active.isEmpty() && downloads.any { it.state == Download.STATE_QUEUED }
        val contentText = when {
            active.isNotEmpty() -> resources.getQuantityString(
                R.plurals.boomstream_offline_downloading_count,
                active.size,
                active.size,
            )
            notMetRequirements != 0 -> getString(R.string.boomstream_offline_waiting_for_network)
            else -> getString(R.string.boomstream_offline_download_queued)
        }

        return NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.boomstream_offline_download_notification_title))
            .setContentText(contentText)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(DOWNLOAD_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        DOWNLOAD_CHANNEL_ID,
                        getString(R.string.boomstream_offline_download_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "boomstream_offline_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 0xBD_0FF1 // boomstream-offline
    }
}
