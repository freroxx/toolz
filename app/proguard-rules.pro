# ==============================================================================
# TOOLZ ULTIMATE STABILITY R8 CONFIGURATION
# Focus: 100% Reliability, Polymorphic Serialization Fix, Gemini & Media3 Safety
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. CORE R8 SETTINGS & GENERAL STABILITY
# ------------------------------------------------------------------------------

# Preserve essential attributes for reflection, stack traces, and generics
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations
-renamesourcefileattribute SourceFile
-keepnames class * implements java.io.Serializable

# Ignore missing JVM-only classes not present on Android
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn javax.script.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**

# ------------------------------------------------------------------------------
# 2. KOTLINX SERIALIZATION (CRITICAL FOR STABILITY)
# ------------------------------------------------------------------------------

# Keep all serializable classes and their generated code
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    *** Companion;
    *** $serializer;
}

# Keep the KSerializer implementations and common serialization classes
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keep class * extends kotlinx.serialization.KSerializer { *; }
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Fix for "Cannot serialize abstract class" - Keep all polymorphic info
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Transient <fields>;
}

# ------------------------------------------------------------------------------
# 3. GOOGLE AI (GEMINI) SDK & MLKIT
# ------------------------------------------------------------------------------

# Google AI (Gemini) SDK
-keep class com.google.ai.client.generativeai.** { *; }
-keep interface com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# MediaPipe & TFLite (Maximum Industrial Stability)
# ------------------------------------------------------------------------------
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Explicitly keep all Task Vision classes and members
-keep class com.google.mediapipe.tasks.vision.** { *; }
-keepclassmembers class com.google.mediapipe.tasks.vision.** { *; }
-keep class com.google.mediapipe.tasks.core.** { *; }
-keepclassmembers class com.google.mediapipe.tasks.core.** { *; }

# Explicitly ignore missing proto classes that MediaPipe references but doesn't always use
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto**
-dontwarn com.google.mediapipe.proto.GraphTemplateProto**

# Preserve all native methods and the annotations that mark them.
-keep @interface com.google.mediapipe.framework.NativeMethod
-keepclassmembers class * {
    @com.google.mediapipe.framework.NativeMethod *;
    native <methods>;
}

# Critical for stack walking and internal linkage
-keep class com.google.mediapipe.framework.NativeLibraryLoader { *; }
-keepclassmembers class com.google.mediapipe.framework.NativeLibraryLoader { *; }
-keep class com.google.mediapipe.framework.Graph { *; }
-keepclassmembers class com.google.mediapipe.framework.Graph { *; }
-keep class com.google.mediapipe.framework.Packet { *; }
-keep class com.google.mediapipe.framework.AndroidPacketCreator { *; }
-keep class com.google.mediapipe.framework.AndroidAssetUtil { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keepclassmembers class com.google.mediapipe.tasks.** { *; }

# TFLite Runtime
-keep class org.tensorflow.** { *; }
-keep interface org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ------------------------------------------------------------------------------
# Preserve Gemini's internal Part and Content serialization (fixes "Cannot serialize abstract class")
-keep class com.google.ai.client.generativeai.type.Part { *; }
-keep class com.google.ai.client.generativeai.type.TextPart { *; }
-keep class com.google.ai.client.generativeai.type.BlobPart { *; }
-keep class com.google.ai.client.generativeai.type.FileDataPart { *; }
-keep class com.google.ai.client.generativeai.type.FunctionCallPart { *; }
-keep class com.google.ai.client.generativeai.type.FunctionResponsePart { *; }

# MLKit (Barcode, Text Recognition, Common)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-dontwarn com.google.mlkit.**

# ------------------------------------------------------------------------------
# 4. DAGGER HILT & VIEWMODELS
# ------------------------------------------------------------------------------

# Keep Hilt EntryPoints, Modules, and InstallIn
-keep @dagger.hilt.EntryPoint class *
-keep @dagger.hilt.InstallIn class *
-keep @dagger.Module class *

# Keep all ViewModels and their constructors
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
    *;
}

# Keep all injected fields and constructors
-keepclassmembers class * {
    @javax.inject.Inject *;
}

# ------------------------------------------------------------------------------
# 5. ROOM DATABASE, SQLCIPHER & RETROFIT
# ------------------------------------------------------------------------------

# Keep all Room components
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
    *;
}

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
-keep class androidx.sqlite.db.SupportSQLite* { *; }

# Retrofit & OkHttp
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# ------------------------------------------------------------------------------
# 6. MEDIA3, CAMERA X & LOTTIE
# ------------------------------------------------------------------------------

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

-keep class com.airbnb.lottie.** { *; }

# ------------------------------------------------------------------------------
# 7. COIL 3 (USES KOTLINX SERIALIZATION)
# ------------------------------------------------------------------------------

-keep class coil3.** { *; }
-dontwarn coil3.**

# ------------------------------------------------------------------------------
# 8. OTHER LIBRARIES (FFMPEG, SHIZUKU, NEWPIPE, ETC.)
# ------------------------------------------------------------------------------

# FFmpeg & YouTube-DL
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.yaedd.youtubedl_android.** { *; }
-keep class com.junkfood.youtubedl_android.** { *; }

# Shizuku API
-keep class dev.rikka.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn dev.rikka.shizuku.**
-dontwarn rikka.shizuku.**

# NewPipe Extractor
-keep class com.github.TeamNewPipe.Extractor.** { *; }
-dontwarn com.github.TeamNewPipe.Extractor.**

# Exp4j, Jsoup, Commonmark, SVG, ZXing
-keep class net.objecthunter.exp4j.** { *; }
-keep class org.jsoup.** { *; }
-keep class org.commonmark.** { *; }
-keep class com.caverock.androidsvg.** { *; }
-keep class com.google.zxing.** { *; }

# ------------------------------------------------------------------------------
# 9. PROJECT-SPECIFIC DATA & UI (SAFETY OVERRIDE)
# ------------------------------------------------------------------------------

# Keep all project code to guarantee 100% runtime stability
-keep class com.frerox.toolz.** { *; }

# Keep Parcelable and BuildConfig
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class com.frerox.toolz.BuildConfig {
    public static final java.lang.String *;
}
