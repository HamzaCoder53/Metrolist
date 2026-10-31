package io.github.hamzacoder53.wavelet.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.github.hamzacoder53.wavelet.ui.account.AccountScreen
import io.github.hamzacoder53.wavelet.ui.account.CodeSignInScreen
import io.github.hamzacoder53.wavelet.ui.account.PhoneSignInScreen
import io.github.hamzacoder53.wavelet.ui.account.PlaylistsScreen
import io.github.hamzacoder53.wavelet.ui.account.WebSignInScreen
import io.github.hamzacoder53.wavelet.ui.account.YourLibraryScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val NOW_PLAYING = "now_playing"
    const val HEADPHONES = "headphones"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val COLLECTION = "collection/{kind}/{id}"
    const val LIBRARY = "library"
    const val CACHE_SIZE = "cache_size"
    const val ACCOUNT = "account"
    const val WEB_SIGN_IN = "sign_in/web"
    const val PHONE_SIGN_IN = "sign_in/phone"
    const val CODE_SIGN_IN = "sign_in/code"
    const val YOUR_LIBRARY = "your_library"
    const val SLEEP_TIMER = "sleep_timer"
    const val PLAYLISTS = "playlists"
    const val LIBRARY_COLLECTION = "library/{collectionId}"

    fun collection(kind: String, id: String) = "collection/$kind/${Uri.encode(id)}"

    fun libraryCollection(id: String) = "library/${Uri.encode(id)}"
}

class MainActivity : ComponentActivity() {
    /** Song id from a Tile or complication tap, consumed once by the UI. */
    private val pendingMediaId = mutableStateOf<String?>(null)

    /** Set by a complication tap that needs the headphones prompt before resuming. */
    private val pendingResume = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        readExtras(intent)
        setContent { WaveletApp(pendingMediaId, pendingResume) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent?) {
        intent?.getStringExtra(EXTRA_PLAY_MEDIA_ID)?.let { pendingMediaId.value = it }
        if (intent?.getBooleanExtra(EXTRA_RESUME, false) == true) pendingResume.value = true
    }

    companion object {
        const val EXTRA_PLAY_MEDIA_ID = "play_media_id"
        const val EXTRA_RESUME = "resume"
    }
}

@Composable
fun WaveletApp(
    pendingMediaId: MutableState<String?> = remember { mutableStateOf(null) },
    pendingResume: MutableState<Boolean> = remember { mutableStateOf(false) },
) {
    MaterialTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            val player: PlayerViewModel = viewModel()
            val playerState by player.state.collectAsState()
            val context = LocalContext.current

            LaunchedEffect(pendingMediaId.value) {
                val id = pendingMediaId.value ?: return@LaunchedEffect
                val app = context.applicationContext as io.github.hamzacoder53.wavelet.WaveletApplication
                val song = app.listeningCache.recentHistory(50).firstOrNull { it.id == id }
                if (song != null && player.awaitConnected()) {
                    if (player.playItems(listOf(song.toMediaItem()), 0)) navController.navigate(Routes.NOW_PLAYING)
                }
                pendingMediaId.value = null
            }

            LaunchedEffect(pendingResume.value) {
                if (!pendingResume.value) return@LaunchedEffect
                if (player.awaitConnected()) player.resume()
                pendingResume.value = false
            }

            LaunchedEffect(playerState.needsHeadphones) {
                if (playerState.needsHeadphones &&
                    navController.currentDestination?.route != Routes.HEADPHONES
                ) {
                    navController.navigate(Routes.HEADPHONES)
                }
            }

            SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        nowPlaying = playerState.mediaItem,
                        onSearch = { navController.navigate(Routes.SEARCH) },
                        onNowPlaying = { navController.navigate(Routes.NOW_PLAYING) },
                        onPlaylists = { navController.navigate(Routes.PLAYLISTS) },
                        onOpenCollection = { kind, id -> navController.navigate(Routes.collection(kind, id)) },
                        onDownloads = { navController.navigate(Routes.LIBRARY) },
                        onAccount = { navController.navigate(Routes.ACCOUNT) },
                        onSleepTimer = { navController.navigate(Routes.SLEEP_TIMER) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                        onAbout = { navController.navigate(Routes.ABOUT) },
                        onPlay = { songs, index ->
                            if (player.playSongs(songs, index)) navController.navigate(Routes.NOW_PLAYING)
                        },
                    )
                }
                composable(Routes.SLEEP_TIMER) { SleepTimerScreen() }
                composable(Routes.PLAYLISTS) {
                    PlaylistsScreen(
                        onPlay = { songs, index ->
                            if (player.playSongs(songs, index)) navController.navigate(Routes.NOW_PLAYING)
                        },
                        onOpenPlaylist = { id ->
                            navController.navigate(Routes.collection(CollectionViewModel.KIND_ACCOUNT_PLAYLIST, id))
                        },
                        onSignIn = { navController.navigate(Routes.ACCOUNT) },
                    )
                }
                val playSongs: (List<com.metrolist.innertube.models.SongItem>, Int) -> Unit = { songs, index ->
                    if (player.playSongs(songs, index)) navController.navigate(Routes.NOW_PLAYING)
                }
                val playDownloads: (List<io.github.hamzacoder53.wavelet.playback.DownloadedSong>, Int) -> Unit =
                    { songs, index ->
                        if (player.playItems(songs.map { it.toMediaItem() }, index)) {
                            navController.navigate(Routes.NOW_PLAYING)
                        }
                    }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onPlay = playSongs,
                        onOpenCollection = { kind, id -> navController.navigate(Routes.collection(kind, id)) },
                    )
                }
                composable(Routes.COLLECTION) { CollectionScreen(onPlay = playSongs) }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onPlay = playDownloads,
                        onOpenCollection = { navController.navigate(Routes.libraryCollection(it)) },
                        onPlayCached = { songs, index ->
                            if (player.playItems(songs.map { it.toMediaItem() }, index)) {
                                navController.navigate(Routes.NOW_PLAYING)
                            }
                        },
                    )
                }
                composable(Routes.LIBRARY_COLLECTION) { entry ->
                    LibraryScreen(
                        onPlay = playDownloads,
                        onOpenCollection = {},
                        collectionId = entry.arguments?.getString("collectionId"),
                    )
                }
                composable(Routes.NOW_PLAYING) { NowPlayingScreen(player) }
                composable(Routes.HEADPHONES) {
                    HeadphonesScreen(
                        onConnected = {
                            player.onHeadphonesConnected()
                            navController.navigate(Routes.NOW_PLAYING) {
                                popUpTo(Routes.HEADPHONES) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen(onOpenCacheSize = { navController.navigate(Routes.CACHE_SIZE) }) }
                composable(Routes.CACHE_SIZE) { CacheSizeScreen() }
                composable(Routes.ACCOUNT) {
                    AccountScreen(
                        onPhoneSignIn = { navController.navigate(Routes.PHONE_SIGN_IN) },
                        onWebSignIn = { navController.navigate(Routes.WEB_SIGN_IN) },
                        onCodeSignIn = { navController.navigate(Routes.CODE_SIGN_IN) },
                        onOpenLibrary = { navController.navigate(Routes.YOUR_LIBRARY) },
                    )
                }
                val afterSignIn: () -> Unit = {
                    navController.navigate(Routes.YOUR_LIBRARY) { popUpTo(Routes.ACCOUNT) }
                }
                composable(Routes.PHONE_SIGN_IN) { PhoneSignInScreen(onDone = afterSignIn) }
                composable(Routes.WEB_SIGN_IN) { WebSignInScreen(onDone = afterSignIn) }
                composable(Routes.CODE_SIGN_IN) { CodeSignInScreen(onDone = afterSignIn) }
                composable(Routes.YOUR_LIBRARY) {
                    YourLibraryScreen(
                        onPlay = playSongs,
                        onOpenPlaylist = { id ->
                            navController.navigate(Routes.collection(CollectionViewModel.KIND_ACCOUNT_PLAYLIST, id))
                        },
                        onSignInAgain = { navController.navigate(Routes.ACCOUNT) },
                    )
                }
                composable(Routes.ABOUT) { AboutScreen() }
            }
        }
    }
}

/** The media controller connects asynchronously; wait briefly for it. */
private suspend fun PlayerViewModel.awaitConnected(): Boolean {
    repeat(20) {
        if (isConnected) return true
        kotlinx.coroutines.delay(250)
    }
    return isConnected
}
