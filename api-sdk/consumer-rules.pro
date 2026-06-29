-keep class com.boomstream.sdk.api.** { *; }
-keepclassmembers class com.boomstream.sdk.api.** { *; }

# kotlinx.serialization — required for @Serializable data classes in minified builds
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable *;
}
