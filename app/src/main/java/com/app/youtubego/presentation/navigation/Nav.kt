package com.app.youtubego.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.youtubego.presentation.channel.ChannelScreen
import com.app.youtubego.presentation.home.HomeScreen
import com.app.youtubego.presentation.library.DownloadsScreen
import com.app.youtubego.presentation.library.HistoryScreen
import com.app.youtubego.presentation.library.LibraryItem
import com.app.youtubego.presentation.library.LibraryScreen
import com.app.youtubego.presentation.library.LikedScreen
import com.app.youtubego.presentation.library.PlaylistDetailScreen
import com.app.youtubego.presentation.library.PlaylistsScreen
import com.app.youtubego.presentation.library.WatchLaterScreen
import com.app.youtubego.presentation.player.OfflinePlayerScreen
import com.app.youtubego.presentation.player.PlayerScreen
import com.app.youtubego.presentation.search.SearchScreen
import com.app.youtubego.presentation.settings.SettingsScreen
import com.app.youtubego.presentation.shorts.ShortsScreen
import com.app.youtubego.presentation.subs.SubscriptionsScreen
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurface

object Routes {
    const val HOME = "home"; const val SEARCH = "search"; const val PLAYER = "player/{videoId}"
    const val SHORTS = "shorts"; const val SUBS = "subs"; const val YOU = "you"
    const val CHANNEL = "channel/{channelId}"
    const val HISTORY = "library/history"; const val WATCH_LATER = "library/watchlater"; const val LIKED = "library/liked"
    const val DOWNLOADS = "library/downloads"; const val PLAYLISTS = "library/playlists"; const val PLAYLIST = "library/playlist/{playlistId}"
    const val SETTINGS = "settings"; const val OFFLINE = "offline/{videoId}"
}

private data class Tab(val route: String, val label: String, val off: ImageVector, val on: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Beranda", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.SHORTS, "Shorts", Icons.Outlined.SlowMotionVideo, Icons.Filled.SlowMotionVideo),
    Tab(Routes.SUBS, "Langganan", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions),
    Tab(Routes.YOU, "Anda", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
)

@Composable
fun YoutubeGoNavHost() {
    val nav = rememberNavController()
    val dest = nav.currentBackStackEntryAsState().value?.destination
    // Bar bawah hanya tampil di 4 halaman utama; Pemutar & Cari layar penuh.
    val showBar = tabs.any { t -> dest?.hierarchy?.any { it.route == t.route } == true }
    val openPlayer: (String) -> Unit = { nav.navigate("player/$it") }
    val openChannel: (String) -> Unit = { if (it.isNotBlank()) nav.navigate("channel/$it") }
    val back: () -> Unit = { nav.popBackStack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) NavigationBar(containerColor = YgSurface) {
                tabs.forEach { tab ->
                    val selected = dest?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) tab.on else tab.off, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = YgAccent, selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = YgAccent.copy(alpha = 0.18f),
                            unselectedIconColor = YgMuted, unselectedTextColor = YgMuted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable(Routes.HOME) {
                HomeScreen(onVideoClick = openPlayer, onSearchClick = { nav.navigate(Routes.SEARCH) }, onChannelClick = openChannel)
            }
            composable(Routes.SHORTS) { ShortsScreen(onChannelClick = openChannel) }
            composable(Routes.SUBS) {
                SubscriptionsScreen(
                    onVideoClick = openPlayer, onChannelClick = openChannel,
                    onExplore = { nav.navigate(Routes.HOME) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true } },
                )
            }
            composable(Routes.YOU) {
                LibraryScreen(onItemClick = { item ->
                    nav.navigate(when (item) {
                        LibraryItem.HISTORY -> Routes.HISTORY
                        LibraryItem.WATCH_LATER -> Routes.WATCH_LATER
                        LibraryItem.LIKED -> Routes.LIKED
                        LibraryItem.DOWNLOADS -> Routes.DOWNLOADS
                        LibraryItem.PLAYLISTS -> Routes.PLAYLISTS
                        LibraryItem.SETTINGS -> Routes.SETTINGS
                    })
                })
            }
            composable(Routes.HISTORY) { HistoryScreen(onBack = back, onVideoClick = openPlayer, onChannelClick = openChannel) }
            composable(Routes.WATCH_LATER) { WatchLaterScreen(onBack = back, onVideoClick = openPlayer, onChannelClick = openChannel) }
            composable(Routes.LIKED) { LikedScreen(onBack = back, onVideoClick = openPlayer, onChannelClick = openChannel) }
            composable(Routes.DOWNLOADS) { DownloadsScreen(onBack = back, onPlayOffline = { nav.navigate("offline/$it") }) }
            composable(Routes.PLAYLISTS) { PlaylistsScreen(onBack = back, onOpen = { nav.navigate("library/playlist/$it") }) }
            composable(Routes.PLAYLIST, arguments = listOf(navArgument("playlistId") { type = NavType.StringType })) { entry ->
                PlaylistDetailScreen(entry.arguments?.getString("playlistId").orEmpty(), onBack = back, onVideoClick = openPlayer, onChannelClick = openChannel)
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
            composable(Routes.CHANNEL, arguments = listOf(navArgument("channelId") { type = NavType.StringType })) {
                ChannelScreen(onBack = back, onVideoClick = openPlayer, onChannelClick = openChannel)
            }
            composable(Routes.OFFLINE, arguments = listOf(navArgument("videoId") { type = NavType.StringType })) { entry ->
                OfflinePlayerScreen(entry.arguments?.getString("videoId").orEmpty(), onBack = back)
            }
            composable(Routes.SEARCH) { SearchScreen(onBack = back, onVideoClick = openPlayer, onChannelClick = openChannel) }
            composable(Routes.PLAYER, arguments = listOf(navArgument("videoId") { type = NavType.StringType })) { entry ->
                PlayerScreen(videoId = entry.arguments?.getString("videoId").orEmpty(), onBack = back, onVideoClick = openPlayer, onChannelClick = openChannel)
            }
        }
    }
}

