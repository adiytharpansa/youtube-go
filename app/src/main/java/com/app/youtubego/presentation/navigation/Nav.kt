package com.app.youtubego.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.ui.graphics.graphicsLayer
import com.app.youtubego.presentation.theme.pressScaleOf
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
import com.app.youtubego.presentation.player.MiniPlayerBar
import com.app.youtubego.presentation.player.PlaybackHost
import com.app.youtubego.presentation.player.PlayerScreen
import com.app.youtubego.presentation.search.SearchScreen
import com.app.youtubego.presentation.settings.SettingsScreen
import com.app.youtubego.presentation.shorts.ShortsScreen
import com.app.youtubego.presentation.subs.SubscriptionsScreen
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgOutline
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
    Tab(Routes.SHORTS, "Shorts", Icons.Outlined.SmartDisplay, Icons.Filled.SmartDisplay),
    Tab(Routes.SUBS, "Langganan", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions),
    Tab(Routes.YOU, "Anda", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
)

private fun isTabRoute(route: String?): Boolean = route != null && tabs.any { it.route == route }

@Composable
fun YoutubeGoNavHost() {
    val nav = rememberNavController()
    val haptic = LocalHapticFeedback.current
    val tabReselect = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }
    val dest = nav.currentBackStackEntryAsState().value?.destination
    // Bar bawah hanya tampil di 4 halaman utama; Pemutar & Cari layar penuh.
    val showBar = tabs.any { t -> dest?.hierarchy?.any { it.route == t.route } == true }
    val openPlayer: (String) -> Unit = { id ->
        val cur = nav.currentDestination
        nav.navigate("player/$id") {
            // Dari pemutar ke video lain: ganti entri lama (tidak menumpuk), supaya Kembali tidak memutar ulang video lama.
            if (cur?.route?.startsWith("player/") == true) popUpTo(cur.id) { inclusive = true }
            launchSingleTop = true
        }
    }
    val openChannel: (String) -> Unit = { if (it.isNotBlank()) nav.navigate("channel/$it") }
    val back: () -> Unit = { nav.popBackStack() }
    val route = dest?.route.orEmpty()
    val onPlayerRoute = route.startsWith("player/")
    // Shorts & pemutar offline punya pemutar sendiri: pemutar utama dijeda agar tidak bersuara ganda.
    val pauseMain = route == Routes.SHORTS || route.startsWith("offline/")
    PlaybackHost(pauseMain, openPlayer)
    // Di tab Shorts bar bawah menyatu dengan video: hitam pekat, tanpa garis pemisah, ikon & label putih.
    val onShorts = route == Routes.SHORTS
    val barColor by animateColorAsState(if (onShorts) Color.Black else YgSurface, tween(200), label = "barColor")
    val unselectedColor by animateColorAsState(if (onShorts) Color.White.copy(alpha = 0.66f) else YgMuted, tween(200), label = "navUnselected")
    val selectedTextColor by animateColorAsState(if (onShorts) Color.White else MaterialTheme.colorScheme.onSurface, tween(200), label = "navSelectedText")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
            if (!onPlayerRoute && !pauseMain) MiniPlayerBar(onOpen = openPlayer)
            if (showBar && !onShorts) HorizontalDivider(color = YgOutline, thickness = 0.5.dp)
            if (showBar) NavigationBar(containerColor = barColor, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    val selected = dest?.hierarchy?.any { it.route == tab.route } == true
                    val navSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val navScale = pressScaleOf(navSource, 0.9f)
                    NavigationBarItem(
                        selected = selected,
                        interactionSource = navSource,
                        modifier = Modifier.graphicsLayer { scaleX = navScale; scaleY = navScale },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (selected) {
                                // Ketuk ulang tab aktif: layar menggulir ke atas (tanpa membuat tujuan navigasi baru).
                                tabReselect.tryEmit(tab.route)
                            } else nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) tab.on else tab.off, contentDescription = null) },
                        label = {
                            // 12sp tanpa jarak huruf tambahan: label tidak renggang & "Langganan" muat satu baris.
                            Text(tab.label, maxLines = 1, softWrap = false, fontSize = 12.sp, letterSpacing = 0.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = YgAccent, selectedTextColor = selectedTextColor,
                            indicatorColor = YgAccent.copy(alpha = if (onShorts) 0.24f else 0.14f),
                            unselectedIconColor = unselectedColor, unselectedTextColor = unselectedColor,
                        ),
                    )
                }
            }
            }
        },
    ) { pad ->
        CompositionLocalProvider(LocalTabReselect provides tabReselect) {
            NavHost(
                nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
                // Transisi bawaan 700 ms terasa lambat. Antar tab utama: pudar + sedikit membesar (220 ms, halus);
                // halaman lain tetap pudar cepat 90-160 ms supaya terasa instan.
                enterTransition = {
                    if (isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route))
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                    else fadeIn(tween(160))
                },
                exitTransition = {
                    if (isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route)) fadeOut(tween(120)) else fadeOut(tween(90))
                },
                popEnterTransition = { fadeIn(tween(160)) },
                popExitTransition = { fadeOut(tween(90)) },
            ) {
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
                    LibraryScreen(onVideoClick = openPlayer, onItemClick = { item ->
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
}
