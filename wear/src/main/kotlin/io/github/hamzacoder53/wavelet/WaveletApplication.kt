package io.github.hamzacoder53.wavelet

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.metrolist.innertube.YouTube
import com.metrolist.music.utils.InnerTubeXPlayer
import io.github.hamzacoder53.wavelet.playback.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class WaveletApplication : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: Settings
        private set

    lateinit var downloads: Downloads
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        settings = Settings(this)
        downloads = Downloads(this, settings)
        InnerTubeXPlayer.initialize(this)

        YouTube.visitorData = settings.visitorData
        appScope.launch {
            // Offline launches must not fail here; downloaded songs need no network.
            if (YouTube.visitorData == null) {
                YouTube.visitorData()
                    .onSuccess { visitorData ->
                        YouTube.visitorData = visitorData
                        settings.visitorData = visitorData
                    }.onFailure { Timber.w(it, "Could not fetch visitorData") }
            }
            runCatching { InnerTubeXPlayer.prewarm() }
                .onFailure { Timber.w(it, "Stream extractor prewarm failed") }
        }
    }

    // Small memory and disk caches: watch artwork is tiny and RAM is scarce.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.1).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork"))
                    .maxSizeBytes(20L * 1024 * 1024)
                    .build()
            }.crossfade(false)
            .build()
}
