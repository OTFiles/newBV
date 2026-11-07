package dev.otfiles.newbv.app.ui.screen.home

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.otfiles.newbv.app.ui.navigation.HomeRoute
import dev.otfiles.newbv.app.ui.screen.main.MainScreen

/**
 * 主页导航注册。
 *
 * [HomeRoute] 对应 [MainScreen]（NavigationDrawer + HomeContent）。
 */
fun NavGraphBuilder.homeScreen(navController: NavController) {
    composable<HomeRoute> {
        MainScreen(navController = navController)
    }
}
