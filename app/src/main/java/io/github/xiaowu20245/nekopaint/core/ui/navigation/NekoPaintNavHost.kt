package io.github.xiaowu20245.nekopaint.core.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarAction
import io.github.xiaowu20245.nekopaint.feature.generate.GenerateScreen
import io.github.xiaowu20245.nekopaint.feature.history.HistoryDetailScreen
import io.github.xiaowu20245.nekopaint.feature.history.HistoryScreen
import io.github.xiaowu20245.nekopaint.feature.queue.QueueScreen
import io.github.xiaowu20245.nekopaint.feature.server.ServerScreen
import io.github.xiaowu20245.nekopaint.feature.settings.SettingsScreen
import io.github.xiaowu20245.nekopaint.feature.workflow.WorkflowDetailScreen
import io.github.xiaowu20245.nekopaint.feature.workflow.WorkflowsScreen

@Composable
fun NekoPaintNavHost(
    navController: NavHostController,
    onTopBarActionChange: (NekoTopBarAction?) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = NekoPaintDestination.Server.route,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(260),
            ) + fadeIn(tween(120))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(260),
            ) + fadeOut(tween(120))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(260),
            ) + fadeIn(tween(120))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(260),
            ) + fadeOut(tween(120))
        },
    ) {
        composable(NekoPaintDestination.Server.route) { ServerScreen() }
        composable(NekoPaintDestination.Workflows.route) {
            WorkflowsScreen(
                onOpenTemplate = { templateId ->
                    navController.navigate("workflow/$templateId")
                },
            )
        }
        composable(
            route = "workflow/{templateId}",
            arguments = listOf(
                navArgument("templateId") { type = NavType.StringType },
            ),
        ) { entry ->
            WorkflowDetailScreen(
                templateId = entry.arguments?.getString("templateId").orEmpty(),
                onTopBarActionChange = onTopBarActionChange,
            )
        }
        composable(NekoPaintDestination.Generate.route) { GenerateScreen() }
        composable(NekoPaintDestination.Queue.route) {
            QueueScreen(
                onOpenHistory = {
                    navController.navigate(NekoPaintDestination.History.route)
                },
            )
        }
        composable(NekoPaintDestination.History.route) {
            HistoryScreen(
                onOpenDetail = { promptId ->
                    navController.navigate("history/$promptId")
                },
            )
        }
        composable(
            route = "history/{promptId}",
            arguments = listOf(
                navArgument("promptId") { type = NavType.StringType },
            ),
        ) { entry ->
            HistoryDetailScreen(
                promptId = entry.arguments?.getString("promptId").orEmpty(),
            )
        }
        composable(NekoPaintDestination.Settings.route) { SettingsScreen() }
    }
}
