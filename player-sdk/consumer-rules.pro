# Keep public API classes so consumers' R8 pass does not strip them.
-keep public class com.boomstream.sdk.player.PlayerState { *; }
-keep public class com.boomstream.sdk.player.PlayerState$* { *; }
-keep public class com.boomstream.sdk.player.AdvancedPlayerOptions { *; }
-keep public class com.boomstream.sdk.player.BoomstreamPlayerView { *; }
# BoomstreamPlayer is a Kotlin top-level Composable — keep the file class.
-keep public class com.boomstream.sdk.player.BoomstreamPlayerKt { *; }
