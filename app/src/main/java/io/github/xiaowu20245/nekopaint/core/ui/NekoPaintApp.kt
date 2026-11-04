package io.github.xiaowu20245.nekopaint.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.xiaowu20245.nekopaint.core.ui.navigation.NekoPaintDestination
import io.github.xiaowu20245.nekopaint.core.ui.navigation.NekoPaintNavHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NekoPaintApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = NekoPaintDestination.fromRoute(backStackEntry?.destination?.route)
    val isMainDestination = NekoPaintDestination.mainDestinations.any {
        it.route == backStackEntry?.destination?.route
    }
    var topBarAction by remember { mutableStateOf<NekoTopBarAction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(currentDestination.labelRes)) },
                navigationIcon = {
                    if (!isMainDestination) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = {
                    topBarAction?.let { action ->
                        TextButton(
                            onClick = action.onClick,
                            enabled = action.enabled,
                        ) {
                            Text(action.label)
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isMainDestination) {
                NavigationBar {
                    NekoPaintDestination.mainDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == currentDestination,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NekoPaintNavHost(
            navController = navController,
            onTopBarActionChange = { topBarAction = it },
            modifier = Modifier.padding(innerPadding),
        )
    }
}
