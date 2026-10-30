plugins {
    id("com.android.library")
}

android {
    namespace = "com.metrolist.music.streaming"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":innertube"))
    api(libs.media3)
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.collection:collection:1.5.0")
    implementation("androidx.annotation:annotation:1.10.0")
    implementation(libs.ktor.client.okhttp)
    implementation(libs.timber)
    coreLibraryDesugaring(libs.desugaring)
    testImplementation(libs.junit)
}
