import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

// Wavelet: standalone Wear OS client built on Metrolist's :streaming and :innertube modules.
// Wear-only versions live here rather than in gradle/libs.versions.toml so upstream merges stay clean.
val wearCompose = "1.7.1"
val wearOngoing = "1.1.0"
val wearInput = "1.2.0"

// Play upload key lives outside the repo; override the location with WAVELET_SIGNING_PROPERTIES.
val uploadSigning =
    Properties().apply {
        val path =
            System.getenv("WAVELET_SIGNING_PROPERTIES")
                ?: "${System.getProperty("user.home")}/.android/wavelet-upload.properties"
        file(path).takeIf { it.isFile }?.inputStream()?.use(::load)
    }

// Play service-account key (shared with Tumbler); override with WAVELET_PLAY_CREDENTIALS.
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
    namespace = "io.github.hamzacoder53.wavelet"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.hamzacoder53.wavelet"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use the Play upload key when present; otherwise debug keys so local release builds still install.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(21)
}

play {
    // Wear OS has its own form-factor tracks in the Play API; the phone "internal" track is unused.
    track.set("wear:internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)
    defaultToAppBundles.set(true)
    if (playCredentials.isFile) {
        serviceAccountCredentials.set(playCredentials)
    } else {
        enabled.set(false)
    }
}

dependencies {
    implementation(project(":streaming"))

    implementation("androidx.wear.compose:compose-material3:$wearCompose")
    implementation("androidx.wear.compose:compose-foundation:$wearCompose")
    implementation("androidx.wear.compose:compose-navigation:$wearCompose")
    implementation("androidx.wear:wear-ongoing:$wearOngoing")
    implementation("androidx.wear:wear-input:$wearInput")
    implementation(libs.activity)
    implementation(libs.viewmodel.compose)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation(libs.coroutines.guava)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.timber)

    coreLibraryDesugaring(libs.desugaring)
}
