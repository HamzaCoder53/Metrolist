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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class WaveletApplication : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        settings = Settings(this)
        InnerTubeXPlayer.initialize(this)

        YouTube.visitorData = settings.visitorData
        appScope.launch {
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
