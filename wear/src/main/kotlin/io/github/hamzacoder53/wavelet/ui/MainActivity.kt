package io.github.hamzacoder53.wavelet.ui

import android.Manifest
import android.content.pm.PackageManager
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

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val NOW_PLAYING = "now_playing"
    const val HEADPHONES = "headphones"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent { WaveletApp() }
    }
}

@Composable
fun WaveletApp() {
    MaterialTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            val player: PlayerViewModel = viewModel()
            val playerState by player.state.collectAsState()

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
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                        onAbout = { navController.navigate(Routes.ABOUT) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onPlay = { songs, index ->
                            if (player.playSongs(songs, index)) navController.navigate(Routes.NOW_PLAYING)
                        },
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
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.ABOUT) { AboutScreen() }
            }
        }
    }
}
