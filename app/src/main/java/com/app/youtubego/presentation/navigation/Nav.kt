package com.app.youtubego.presentation.navigation
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.youtubego.presentation.home.HomeScreen
import com.app.youtubego.presentation.player.PlayerScreen
import com.app.youtubego.presentation.search.SearchScreen
object Routes { const val HOME = "home"; const val SEARCH = "search"; const val PLAYER = "player/{videoId}" }
@Composable
fun YoutubeGoNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(onVideoClick = { nav.navigate("player/$it") }, onSearchClick = { nav.navigate(Routes.SEARCH) }) }
        composable(Routes.SEARCH) { SearchScreen(onBack = { nav.popBackStack() }, onVideoClick = { nav.navigate("player/$it") }) }
        composable(Routes.PLAYER, arguments = listOf(navArgument("videoId") { type = NavType.StringType })) { entry ->
            PlayerScreen(videoId = entry.arguments?.getString("videoId").orEmpty(), onBack = { nav.popBackStack() }, onVideoClick = { nav.navigate("player/$it") })
        }
    }
}
