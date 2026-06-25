# ProGuard/R8 consumer rules for offline-sdk.
# These rules are bundled into the AAR and applied automatically by consumer apps.

# Keep all public API classes so consumers' R8 pass does not strip them.
-keep public class com.boomstream.sdk.offline.BoomstreamOfflineManager { *; }
-keep public class com.boomstream.sdk.offline.DownloadState { *; }
-keep public class com.boomstream.sdk.offline.DownloadState$* { *; }
-keep public class com.boomstream.sdk.offline.DownloadQuality { *; }
-keep public class com.boomstream.sdk.offline.BoomstreamOfflineException { *; }
-keep public class com.boomstream.sdk.offline.BoomstreamOfflineException$* { *; }

# Keep BoomstreamVideoDownloadService so it survives ProGuard when referenced from AndroidManifest.
-keep public class com.boomstream.sdk.offline.BoomstreamVideoDownloadService { *; }
-keep class * extends com.boomstream.sdk.offline.BoomstreamVideoDownloadService { *; }
