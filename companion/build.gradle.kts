import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

// Wavelet phone companion: signs in to YouTube Music on the phone and sends the session to the
// watch app over the Wear Data Layer. Same applicationId and signing key as :wear (required by
// the Data Layer); different versionCodes (1000+) because both are uploaded to one Play listing.

val uploadSigning =
    Properties().apply {
        val path =
            System.getenv("WAVELET_SIGNING_PROPERTIES")
                ?: "${System.getProperty("user.home")}/.android/wavelet-upload.properties"
        file(path).takeIf { it.isFile }?.inputStream()?.use(::load)
    }

val playCredentials =
    file(
        System.getenv("WAVELET_PLAY_CREDENTIALS")
            ?: "${System.getProperty("user.home")}/.tumbler/play-service-account.json",
    )

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
    id("com.github.triplet.play") version "4.1.1"
}

android {
    namespace = "io.github.hamzacoder53.wavelet.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.hamzacoder53.wavelet"
        minSdk = 26
        targetSdk = 36
        versionCode = 1001
        versionName = "0.4.2"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (uploadSigning.isNotEmpty()) {
            create("upload") {
                storeFile = file(uploadSigning.getProperty("storeFile"))
                storePassword = uploadSigning.getProperty("storePassword")
                keyAlias = uploadSigning.getProperty("keyAlias")
                keyPassword = uploadSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug builds must share the release key too, or the watch's Data Layer won't talk to them.
        debug {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

play {
    // The phone track (the Wear OS app uses wear:internal).
    track.set("internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)
    defaultToAppBundles.set(true)
    if (playCredentials.isFile) {
        serviceAccountCredentials.set(playCredentials)
    } else {
        enabled.set(false)
    }
}

dependencies {
    implementation(project(":session"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation(libs.activity)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.material3)
}
