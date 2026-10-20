package com.app.youtubego.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.home.HomeScreen
import com.app.youtubego.presentation.home.ShortsScreen
import com.app.youtubego.presentation.library.ChannelScreen
import com.app.youtubego.presentation.library.DownloadsScreen
import com.app.youtubego.presentation.library.CollectionScreen
import com.app.youtubego.presentation.library.LibraryItem
import com.app.youtubego.presentation.library.LibraryScreen
import com.app.youtubego.presentation.library.PlaylistDetailScreen
import com.app.youtubego.presentation.library.PlaylistsScreen
import com.app.youtubego.presentation.library.SettingsScreen
import com.app.youtubego.presentation.library.SubscriptionsScreen
import com.app.youtubego.presentation.player.PlayerScreen
import com.app.youtubego.presentation.search.SearchScreen
import com.app.youtubego.presentation.theme.*

object Routes {
    const val HOME = "home"; const val SEARCH = "search"; const val PLAYER = "player/{videoId}"
    const val SHORTS = "shorts"; const val SUBS = "subs"; const val YOU = "you"
    const val COLLECTION = "collection/{kind}"; const val SETTINGS = "settings"; const val CHANNEL = "channel/{ucid}"; const val DOWNLOADS = "downloads"; const val PLAYLISTS = "playlists"; const val PLAYLIST_DETAIL = "playlist/{playlistId}"
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
    val showBar = tabs.any { t -> dest?.hierarchy?.any { it.route == t.route } == true }
    val openPlayer: (String) -> Unit = { nav.navigate("player/$it") }
    val openChannel: (String) -> Unit = { nav.navigate("channel/$it") }

    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(YgBackground.copy(alpha = 0.98f))
                        .border(width = 0.6.dp, color = YgDivider, shape = RoundedCornerShape(0.dp))
                        .navigationBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    tabs.forEach { tab ->
                        val selected = dest?.hierarchy?.any { it.route == tab.route } == true
                        YgBottomTab(
                            tab = tab,
                            selected = selected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding())
        ) {
            composable(Routes.HOME) { HomeScreen(onVideoClick = openPlayer, onSearchClick = { nav.navigate(Routes.SEARCH) }, onChannelClick = openChannel) }
            composable(Routes.SHORTS) { ShortsScreen(onVideoClick = openPlayer) }
            composable(Routes.SUBS) {
                SubscriptionsScreen(
                    onExplore = { nav.navigate(Routes.HOME) { launchSingleTop = true } },
                    onVideoClick = openPlayer,
                    onChannelClick = openChannel
                )
            }
            composable(Routes.YOU) {
                LibraryScreen(onItemClick = { item ->
                    when (item) {
                        LibraryItem.SETTINGS -> nav.navigate(Routes.SETTINGS)
                        LibraryItem.DOWNLOADS -> nav.navigate(Routes.DOWNLOADS)
                        LibraryItem.PLAYLISTS -> nav.navigate(Routes.PLAYLISTS)
                        else -> nav.navigate("collection/${item.key}")
                    }
                })
            }
            composable(Routes.COLLECTION, arguments = listOf(navArgument("kind") { type = NavType.StringType })) {
                CollectionScreen(onBack = { nav.popBackStack() }, onVideoClick = openPlayer, onChannelClick = openChannel)
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.DOWNLOADS) { DownloadsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("playlist/$id") })
            }
            composable(Routes.PLAYLIST_DETAIL, arguments = listOf(navArgument("playlistId") { type = NavType.StringType })) {
                PlaylistDetailScreen(onBack = { nav.popBackStack() }, onVideoClick = openPlayer)
            }
            composable(Routes.SEARCH) { SearchScreen(onBack = { nav.popBackStack() }, onVideoClick = openPlayer, onChannelClick = openChannel) }
            composable(Routes.PLAYER, arguments = listOf(navArgument("videoId") { type = NavType.StringType })) { entry ->
                PlayerScreen(videoId = entry.arguments?.getString("videoId").orEmpty(), onBack = { nav.popBackStack() }, onVideoClick = openPlayer, onChannelClick = openChannel)
            }
            composable(Routes.CHANNEL, arguments = listOf(navArgument("ucid") { type = NavType.StringType })) {
                ChannelScreen(onBack = { nav.popBackStack() }, onVideoClick = openPlayer)
            }
        }
    }
}

@Composable
private fun YgBottomTab(tab: Tab, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val iconColor by animateColorAsState(if (selected) YgAccent else YgMuted, label = "tabIcon")
    val pillWidth by animateDpAsState(if (selected) 58.dp else 0.dp, label = "tabPill")
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(pillWidth)
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(if (selected) YgAccentGlow else Color.Transparent)
            )
            Icon(
                if (selected) tab.on else tab.off,
                contentDescription = tab.label,
                tint = iconColor,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            color = if (selected) YgOnSurface else YgMutedSoft,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.5.sp
            ),
            maxLines = 1
        )
    }
}
