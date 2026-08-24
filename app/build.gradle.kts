/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import java.util.Properties
import java.io.FileInputStream

// Read local.properties for Supabase credentials
val localProperties = Properties().also { props ->
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) props.load(FileInputStream(localFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
}

// H-10 FIX (reviewwhisper.md): export Room schemas so every future version bump ships
// with a reviewable schema diff and an explicit Migration. fallbackToDestructiveMigration
// was already removed (it wiped E2EE history on drift); without exported schemas the next
// bump would crash every installed client with "migration not found".
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.frerox.toolz"
    compileSdk = 37

    androidResources {
        noCompress += "tflite"
    }

    defaultConfig {
        applicationId = "com.frerox.toolz"
        minSdk = 31
        targetSdk = 36
        versionCode = 12
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase config — set these in local.properties:
        // SUPABASE_URL=https://your-project-id.supabase.co
        // SUPABASE_ANON_KEY=your-anon-key
        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"") 
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\"")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isEmpty) {
                // Fallback to debug for local development convenience
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.version"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += "ExtraTranslation"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
        aidl = true
    }
    
    androidResources {
        noCompress += "tflite"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileSdkMinor = 1
}


kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.media3.common.util.UnstableApi")
    }

    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.foundation.layout)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.viewbinding)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.palette.ktx)
    
    // Coil 3
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)

    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.exifinterface)
    implementation(libs.play.services.location)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.androidx.pdf.viewer)
    implementation(libs.androidx.pdf.viewer.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.moshi.kotlin)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)
    implementation(libs.barcode.scanning)
    // LiteRT (TFLite successor) — bundled runtime, works on every device with zero Play
    // Services dependency, and ships 16 KB-aligned native libs (Play requirement, Android 15+).
    // Same org.tensorflow.lite package / classic Interpreter API — no code change needed.
    implementation(libs.litert)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.devanagari)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.exp4j)
    implementation(libs.jsoup)
    implementation(libs.commonmark)
    implementation(libs.androidsvg)
    implementation(libs.zxing.core)
    implementation(libs.androidx.webkit)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Glance (Widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer.dash)

    // NewPipeExtractor
    implementation(libs.newpipe.extractor)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // Generative AI
    implementation(libs.generativeai)

    // Security Crypto
    implementation(libs.androidx.security.crypto)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // FFmpeg & Lottie
    implementation(libs.ffmpeg.kit.standard)
    implementation(libs.youtubedl.android)
    implementation(libs.youtubedl.android.ffmpeg)
    implementation(libs.smart.exception.java)
    implementation(libs.lottie.compose)

    // Password Vault - SQLCipher & Biometrics
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.framework)
    implementation(libs.androidx.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)

    // Firebase Cloud Messaging — Whisper push wake-ups (data-only; no content transits FCM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Supabase — Whisper messaging
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.realtime.kt)
    implementation(libs.supabase.storage.kt)
    implementation(libs.ktor.client.okhttp)
}
