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
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.home.HomeScreen
import com.app.youtubego.presentation.library.LibraryScreen
import com.app.youtubego.presentation.player.PlayerScreen
import com.app.youtubego.presentation.search.SearchScreen
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurface

object Routes {
    const val HOME = "home"; const val SEARCH = "search"; const val PLAYER = "player/{videoId}"
    const val SHORTS = "shorts"; const val SUBS = "subs"; const val YOU = "you"
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
            composable(Routes.HOME) { HomeScreen(onVideoClick = openPlayer, onSearchClick = { nav.navigate(Routes.SEARCH) }) }
            composable(Routes.SHORTS) {
                EmptyState(Icons.Outlined.SlowMotionVideo, "Shorts segera hadir", "Video pendek layar penuh sedang disiapkan.")
            }
            composable(Routes.SUBS) {
                EmptyState(
                    Icons.Outlined.Subscriptions, "Belum ada langganan",
                    "Langganan channel favoritmu supaya video terbarunya muncul di sini.",
                    actionLabel = "Jelajahi video",
                    onAction = { nav.navigate(Routes.HOME) { launchSingleTop = true } },
                )
            }
            composable(Routes.YOU) { LibraryScreen() }
            composable(Routes.SEARCH) { SearchScreen(onBack = { nav.popBackStack() }, onVideoClick = openPlayer) }
            composable(Routes.PLAYER, arguments = listOf(navArgument("videoId") { type = NavType.StringType })) { entry ->
                PlayerScreen(videoId = entry.arguments?.getString("videoId").orEmpty(), onBack = { nav.popBackStack() }, onVideoClick = openPlayer)
            }
        }
    }
}

