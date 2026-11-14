package io.github.xiaowu20245.nekopaint.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.xiaowu20245.nekopaint.core.ui.navigation.NekoPaintDestination
import io.github.xiaowu20245.nekopaint.core.ui.navigation.NekoPaintNavHost
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.R

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
    var topBarIconActions by remember { mutableStateOf<List<NekoTopBarIconAction>>(emptyList()) }
    var screenBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(currentDestination.labelRes),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (currentDestination == NekoPaintDestination.Chat) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_beta),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (!isMainDestination) {
                        IconButton(
                            onClick = {
                                val handled = screenBackHandler?.invoke() == true
                                if (!handled) {
                                    navController.popBackStack()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = {
                    if (isMainDestination) {
                        IconButton(
                            onClick = {
                                navController.navigate(NekoPaintDestination.Chat.route) {
                                    launchSingleTop = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = NekoIcons.Sparkles,
                                contentDescription = stringResource(R.string.chat_title),
                            )
                        }
                    }
                    topBarIconActions.forEach { action ->
                        when {
                            action.menuItems.isNotEmpty() -> NekoTopBarOverflowAction(action)
                            action.label != null -> TextButton(
                                onClick = action.onClick,
                                enabled = action.enabled,
                            ) {
                                Text(action.label)
                            }
                            action.icon != null -> IconButton(
                                onClick = action.onClick,
                                enabled = action.enabled,
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.contentDescription,
                                    tint = action.tint ?: LocalContentColor.current,
                                )
                            }
                        }
                    }
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
            onTopBarIconActionsChange = { topBarIconActions = it },
            onBackHandlerChange = { screenBackHandler = it },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun NekoTopBarOverflowAction(action: NekoTopBarIconAction) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = action.enabled,
        ) {
            Icon(
                imageVector = action.icon ?: Icons.Outlined.MoreVert,
                contentDescription = action.contentDescription,
                tint = action.tint ?: LocalContentColor.current,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            action.menuItems.forEach { item ->
                val itemColor = when {
                    !item.enabled -> LocalContentColor.current.copy(alpha = 0.38f)
                    item.destructive -> MaterialTheme.colorScheme.error
                    else -> LocalContentColor.current
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            color = itemColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = item.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = itemColor,
                            )
                        }
                    },
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
