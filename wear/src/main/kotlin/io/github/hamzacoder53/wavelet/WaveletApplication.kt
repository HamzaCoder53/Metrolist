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
import io.github.hamzacoder53.wavelet.account.Account
import io.github.hamzacoder53.wavelet.account.AccountLibrary
import io.github.hamzacoder53.wavelet.playback.Downloads
import io.github.hamzacoder53.wavelet.playback.ListeningCache
import io.github.hamzacoder53.wavelet.playback.Loudness
import io.github.hamzacoder53.wavelet.playback.SleepTimer
import io.github.hamzacoder53.wavelet.playback.SyncedLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import io.github.hamzacoder53.wavelet.playback.DownloadSync
import timber.log.Timber

class WaveletApplication : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: Settings
        private set

    lateinit var downloads: Downloads
        private set

    lateinit var listeningCache: ListeningCache
        private set

    lateinit var account: Account
        private set

    lateinit var accountLibrary: AccountLibrary
        private set

    val lyrics = SyncedLyrics()
    val sleepTimer = SleepTimer()

    lateinit var loudness: Loudness
        private set

    lateinit var downloadSync: DownloadSync
        private set

    lateinit var updateChecker: UpdateChecker
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        settings = Settings(this)
        loudness = Loudness(this)
        downloads = Downloads(this, settings)
        listeningCache = ListeningCache(this, settings, downloads.databaseProvider)
        account = Account(this, settings)
        accountLibrary = AccountLibrary(this, settings, account)
        downloadSync = DownloadSync(settings, downloads, accountLibrary, account.state)
        appScope.launch {
            downloads.ready.first { it }
            downloadSync.syncIfDue()
            accountLibrary.snapshot.map { it.syncedAt }.distinctUntilChanged().drop(1).collect { downloadSync.sync() }
        }
        updateChecker = UpdateChecker(this, settings)
        updateChecker.checkOnAppStart(appScope)
        InnerTubeXPlayer.initialize(this)

        YouTube.visitorData = settings.visitorData
        account.applySavedSession()
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
