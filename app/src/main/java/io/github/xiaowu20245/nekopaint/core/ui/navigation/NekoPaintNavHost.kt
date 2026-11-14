package io.github.xiaowu20245.nekopaint.core.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarAction
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarIconAction
import io.github.xiaowu20245.nekopaint.feature.generate.GenerateScreen
import io.github.xiaowu20245.nekopaint.feature.chat.ChatScreen
import io.github.xiaowu20245.nekopaint.feature.history.HistoryDetailScreen
import io.github.xiaowu20245.nekopaint.feature.history.HistoryScreen
import io.github.xiaowu20245.nekopaint.feature.queue.QueueScreen
import io.github.xiaowu20245.nekopaint.feature.server.ServerScreen
import io.github.xiaowu20245.nekopaint.feature.settings.AiAssistantSettingsScreen
import io.github.xiaowu20245.nekopaint.feature.settings.PromptLexiconScreen
import io.github.xiaowu20245.nekopaint.feature.settings.SettingsScreen
import io.github.xiaowu20245.nekopaint.feature.workflow.WorkflowDetailScreen
import io.github.xiaowu20245.nekopaint.feature.workflow.WorkflowsScreen

@Composable
fun NekoPaintNavHost(
    navController: NavHostController,
    onTopBarActionChange: (NekoTopBarAction?) -> Unit,
    modifier: Modifier = Modifier,
    onTopBarIconActionsChange: (List<NekoTopBarIconAction>) -> Unit = {},
    onBackHandlerChange: ((() -> Boolean)?) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = NekoPaintDestination.Server.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(90)) },
        popEnterTransition = { fadeIn(tween(120)) },
        popExitTransition = { fadeOut(tween(90)) },
    ) {
        composable(
            route = NekoPaintDestination.Server.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { ServerScreen() }
        composable(
            route = NekoPaintDestination.Workflows.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
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
                onOpenAiSettings = {
                    navController.navigate(NekoPaintDestination.AiAssistant.route)
                },
                onBack = { navController.popBackStack() },
                onBackHandlerChange = onBackHandlerChange,
            )
        }
        composable(NekoPaintDestination.Generate.route) { GenerateScreen() }
        composable(
            route = NekoPaintDestination.Queue.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
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
                onOpenWorkflow = { templateId ->
                    navController.navigate("workflow/$templateId")
                },
                onAskNekoI = { promptId ->
                    navController.navigate(chatRoute(promptId))
                },
                onTopBarIconActionsChange = onTopBarIconActionsChange,
            )
        }
        composable(
            route = "${NekoPaintDestination.Chat.route}?promptId={promptId}",
            arguments = listOf(
                navArgument("promptId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            ChatScreen(
                onOpenAiSettings = {
                    navController.navigate(NekoPaintDestination.AiAssistant.route)
                },
                onTopBarIconActionsChange = onTopBarIconActionsChange,
                promptId = entry.arguments?.getString("promptId"),
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
                onBack = { navController.popBackStack() },
                onOpenWorkflow = { templateId ->
                    navController.navigate("workflow/$templateId")
                },
                onAskNekoI = { promptId ->
                    navController.navigate(chatRoute(promptId))
                },
            )
        }
        composable(
            route = NekoPaintDestination.Settings.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            SettingsScreen(
                onOpenLexicon = {
                    navController.navigate(NekoPaintDestination.PromptLexicon.route)
                },
                onOpenAiSettings = {
                    navController.navigate(NekoPaintDestination.AiAssistant.route)
                },
            )
        }
        composable(NekoPaintDestination.PromptLexicon.route) {
            PromptLexiconScreen()
        }
        composable(NekoPaintDestination.AiAssistant.route) {
            AiAssistantSettingsScreen()
        }
    }
}

private fun chatRoute(promptId: String): String =
    "${NekoPaintDestination.Chat.route}?promptId=${Uri.encode(promptId)}"
