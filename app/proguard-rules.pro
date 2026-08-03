# ==============================================================================
# TOOLZ COMPLETE STABILITY & CRASH-FREE PROGUARD / R8 RULES
# ==============================================================================

# Preserve line numbers and source file attributes for stack traces & reflection
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations
-renamesourcefileattribute SourceFile

# Ignore missing JVM-desktop classes not present on Android
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ------------------------------------------------------------------------------
# 1. TOOLZ APPLICATION CODE & DATA MODELS (PREVENTS ALL RUNTIME CRASHES)
# ------------------------------------------------------------------------------

# Keep all data models, DTOs, entities, and state classes in com.frerox.toolz
-keep class com.frerox.toolz.data.** { *; }
-keepclassmembers class com.frerox.toolz.data.** { *; }

# Keep all ViewModels, Hilt Injected classes, and UI screens
-keep class com.frerox.toolz.ui.** { *; }
-keepclassmembers class com.frerox.toolz.ui.** { *; }

# Keep all WorkManager Workers and Glance App Widgets
-keep class com.frerox.toolz.worker.** { *; }
-keepclassmembers class com.frerox.toolz.worker.** { *; }
-keep class com.frerox.toolz.widget.** { *; }
-keepclassmembers class com.frerox.toolz.widget.** { *; }

# Keep Services, Receivers, Application, and MainActivity
-keep class com.frerox.toolz.service.** { *; }
-keepclassmembers class com.frerox.toolz.service.** { *; }
-keep class com.frerox.toolz.di.** { *; }
-keepclassmembers class com.frerox.toolz.di.** { *; }
-keep class com.frerox.toolz.ToolzApplication { *; }
-keep class com.frerox.toolz.MainActivity { *; }

# Keep BuildConfig fields for reflection
-keepclassmembers class com.frerox.toolz.BuildConfig {
    public static final java.lang.String *_DEFAULT;
}

# ------------------------------------------------------------------------------
# 2. DAGGER & HILT DEPENDENCY INJECTION
# ------------------------------------------------------------------------------
-keep class * extends javax.inject.Provider
-keep class dagger.hilt.** { *; }
-keepclassmembers class dagger.hilt.** { *; }
-keep class androidx.hilt.** { *; }
-keepclassmembers class androidx.hilt.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @dagger.Provides <methods>;
    @dagger.Binds <methods>;
}
-keep @dagger.hilt.Migration *
-keep @dagger.hilt.EntryPoint *
-keep @dagger.hilt.GeneratesRootInput *
-keep @dagger.hilt.internal.UnstableApi *

# ------------------------------------------------------------------------------
# 3. ROOM DATABASE & SQLCIPHER
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class net.sqlcipher.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclassmembers class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ------------------------------------------------------------------------------
# 4. MOSHI & KOTLINX SERIALIZATION (JSON PARSING)
# ------------------------------------------------------------------------------
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-keep @interface com.squareup.moshi.JsonQualifier
-keep @interface com.squareup.moshi.JsonClass
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.SerialName <fields>;
}

# ------------------------------------------------------------------------------
# 5. RETROFIT, OKHTTP & DNS
# ------------------------------------------------------------------------------
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-dontwarn okhttp3.**

# ------------------------------------------------------------------------------
# 6. MEDIA3 & EXOPLAYER (AUDIO / VIDEO PLAYER TOOLS)
# ------------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ------------------------------------------------------------------------------
# 7. NEWPIPE EXTRACTOR & YOUTUBE-DL & FFMPEG (DOWNLOADER & MEDIA CONVERTER)
# ------------------------------------------------------------------------------
-keep class com.github.TeamNewPipe.** { *; }
-keepclassmembers class com.github.TeamNewPipe.** { *; }
-dontwarn com.github.TeamNewPipe.**
-keep class com.yaedd.youtubedl_android.** { *; }
-keepclassmembers class com.yaedd.youtubedl_android.** { *; }
-dontwarn com.yaedd.youtubedl_android.**
-keep class com.junkfood.youtubedl_android.** { *; }
-keepclassmembers class com.junkfood.youtubedl_android.** { *; }
-dontwarn com.junkfood.youtubedl_android.**
-keep class com.arthenica.ffmpegkit.** { *; }
-keepclassmembers class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# ------------------------------------------------------------------------------
# 8. MLKIT VISION (OCR & BARCODE SCANNER TOOLS)
# ------------------------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-keepclassmembers class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.android.gms.vision.**

# ------------------------------------------------------------------------------
# 9. SHIZUKU API (SYSTEM / ADB TOOLS)
# ------------------------------------------------------------------------------
-keep class dev.rikka.shizuku.** { *; }
-keepclassmembers class dev.rikka.shizuku.** { *; }
-dontwarn dev.rikka.shizuku.**
-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }

# ------------------------------------------------------------------------------
# 10. OTHER SPECIFIC LIBRARIES (EXP4J, JSOUP, COMMONMARK, ANDROIDSVG, ZXING)
# ------------------------------------------------------------------------------
-keep class net.objecthunter.exp4j.** { *; }
-keepclassmembers class net.objecthunter.exp4j.** { *; }
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
-keep class org.commonmark.** { *; }
-keepclassmembers class org.commonmark.** { *; }
-keep class com.caverock.androidsvg.** { *; }
-keepclassmembers class com.caverock.androidsvg.** { *; }
-keep class com.google.zxing.** { *; }
-keepclassmembers class com.google.zxing.** { *; }
-keep class androidx.biometric.** { *; }
-keepclassmembers class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class androidx.security.crypto.** { *; }