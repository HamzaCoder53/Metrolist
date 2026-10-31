package io.github.hamzacoder53.wavelet

import android.content.Context
import android.os.Build
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

class UpdateChecker(
    private val context: Context,
    private val settings: Settings,
) {
    data class UpdateInfo(val version: String, val releaseUrl: String)

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable.asStateFlow()

    init {
        // Restore cached update if valid and newer than current version
        val cachedVersion = settings.latestAvailableVersion
        val cachedUrl = settings.latestReleaseUrl
        if (cachedVersion != null && cachedUrl != null && isVersionNewer(cachedVersion, BuildConfig.VERSION_NAME)) {
            _updateAvailable.value = UpdateInfo(cachedVersion, cachedUrl)
        }
    }

    fun checkOnAppStart(scope: CoroutineScope) {
        if (isInstalledByGooglePlay(context)) {
            Timber.d("App was installed by Google Play; skipping GitHub update check")
            return
        }

        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheckTime < CHECK_INTERVAL_MS) {
            Timber.d("Update check skipped; checked within last 24h")
            return
        }

        scope.launch(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient.Builder().proxy(YouTube.proxy).build()
                val request =
                    Request.Builder()
                        .url(GITHUB_LATEST_RELEASE_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Wavelet")
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("Update check failed with HTTP %d", response.code)
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val tagName = json.getString("tag_name")
                    val releaseUrl = json.optString("html_url", DEFAULT_RELEASE_URL)
                    val latestVersion = parseVersionFromTag(tagName)

                    settings.lastUpdateCheckTime = System.currentTimeMillis()
                    settings.latestAvailableVersion = latestVersion
                    settings.latestReleaseUrl = releaseUrl

                    if (isVersionNewer(latestVersion, BuildConfig.VERSION_NAME)) {
                        _updateAvailable.value = UpdateInfo(latestVersion, releaseUrl)
                        Timber.i("Update available: %s (%s)", latestVersion, releaseUrl)
                    } else {
                        _updateAvailable.value = null
                    }
                }
            }.onFailure {
                Timber.w(it, "Update check failed (offline or network error)")
            }
        }
    }

    companion object {
        const val GOOGLE_PLAY_INSTALLER = "com.android.vending"
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/HamzaCoder53/Metrolist/releases/latest"
        const val DEFAULT_RELEASE_URL = "https://github.com/HamzaCoder53/Metrolist/releases/latest"

        fun parseVersionFromTag(tagName: String): String =
            tagName
                .removePrefix("wavelet-")
                .removePrefix("v")
                .trim()

        fun isVersionNewer(latestVersion: String, currentVersion: String): Boolean {
            val latestParts = latestVersion.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val length = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until length) {
                val latest = latestParts.getOrElse(i) { 0 }
                val current = currentParts.getOrElse(i) { 0 }
                if (latest > current) return true
                if (latest < current) return false
            }
            return false
        }

        fun isInstalledByGooglePlay(context: Context): Boolean =
            runCatching {
                val pm = context.packageManager
                val packageName = context.packageName
                val installer =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pm.getInstallSourceInfo(packageName).installingPackageName
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(packageName)
                    }
                installer == GOOGLE_PLAY_INSTALLER
            }.getOrDefault(false)
    }
}
