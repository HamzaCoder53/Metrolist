// Shared by :wear and :companion: the YouTube Music session captured from a Google sign-in
// in a WebView, and the Wear Data Layer protocol used to send it from phone to watch.
plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.hamzacoder53.wavelet.session"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation(libs.junit)
    // Android's org.json is a throwing stub in JVM unit tests; use the real implementation there.
    testImplementation("org.json:json:20240303")
}
