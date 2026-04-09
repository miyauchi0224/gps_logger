package com.example.gpslogger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gpslogger.ui.export.ExportScreen
import com.example.gpslogger.ui.home.HomeScreen
import com.example.gpslogger.ui.session.SessionListScreen
import com.example.gpslogger.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SESSION_LIST = "session_list"
    const val EXPORT = "export/{sessionId}/{sessionName}"
    const val SETTINGS = "settings"

    fun exportRoute(sessionId: String, sessionName: String) =
        "export/$sessionId/${sessionName.replace("/", "_")}"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSessions = { navController.navigate(Routes.SESSION_LIST) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SESSION_LIST) {
            SessionListScreen(
                onNavigateToExport = { id, name ->
                    navController.navigate(Routes.exportRoute(id, name))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.EXPORT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("sessionName") { type = NavType.StringType }
            )
        ) { backStack ->
            val sessionId = backStack.arguments?.getString("sessionId") ?: ""
            val sessionName = backStack.arguments?.getString("sessionName") ?: ""
            ExportScreen(
                sessionId = sessionId,
                sessionName = sessionName,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
