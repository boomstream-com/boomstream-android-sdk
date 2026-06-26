package com.boomstream.sdk.offline

/**
 * Configuration for [BoomstreamOfflineManager].
 *
 * @property storageLocation Where downloaded HLS segments live on disk.
 *   Default [StorageLocation.INTERNAL] keeps content app-scoped + protected from
 *   removable-SD extraction. Opt into [StorageLocation.EXTERNAL] on devices where
 *   internal partition size (≤2 GB on low-end) is the bottleneck — segments can
 *   reach hundreds of MB per video.
 */
data class BoomstreamOfflineConfig(
    val storageLocation: StorageLocation = StorageLocation.INTERNAL,
) {
    enum class StorageLocation { INTERNAL, EXTERNAL }
}
