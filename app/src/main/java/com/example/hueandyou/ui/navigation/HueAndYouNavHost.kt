package com.example.hueandyou.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.hueandyou.R
import com.example.hueandyou.ui.history.HistoryScreen
import com.example.hueandyou.ui.matchcolors.MatchColorsPlaceholderScreen
import com.example.hueandyou.ui.profiles.ProfileEditorScreen
import com.example.hueandyou.ui.rateclothing.RateClothingPlaceholderScreen
import com.example.hueandyou.ui.settings.SettingsScreen

@Composable
fun HueAndYouNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var showFabMenu by remember { mutableStateOf(false) }

    val onHistory = currentDestination?.hierarchy?.any { it.route == Destination.History.route } == true

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { topLevel ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == topLevel.destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(topLevel.navRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                topLevel.icon,
                                contentDescription = stringResource(topLevel.labelRes)
                            )
                        },
                        label = { Text(stringResource(topLevel.labelRes)) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (onHistory) {
                Box {
                    FloatingActionButton(onClick = { showFabMenu = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.history_fab_content_description)
                        )
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_fab_action_rate_clothing)) },
                            onClick = {
                                showFabMenu = false
                                navController.navigate(Destination.RateClothingPlaceholder.route)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_fab_action_match_colors)) },
                            onClick = {
                                showFabMenu = false
                                navController.navigate(Destination.MatchColorsPlaceholder.route)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.History.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.History.route) {
                HistoryScreen()
            }
            composable(
                Destination.Settings.route,
                arguments = listOf(
                    navArgument(Destination.Settings.ARG_SCROLL_TO_PROFILES) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val scrollToProfiles = backStackEntry.arguments
                    ?.getBoolean(Destination.Settings.ARG_SCROLL_TO_PROFILES) == true
                SettingsScreen(
                    scrollToProfiles = scrollToProfiles,
                    onOpenProfile = { profileId ->
                        navController.navigate(Destination.ProfileEditor.route(profileId))
                    }
                )
            }
            composable(
                Destination.ProfileEditor.route,
                arguments = listOf(
                    navArgument(Destination.ProfileEditor.ARG_PROFILE_ID) { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getLong(Destination.ProfileEditor.ARG_PROFILE_ID) ?: 0L
                ProfileEditorScreen(
                    profileId = profileId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Destination.RateClothingPlaceholder.route) {
                RateClothingPlaceholderScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Destination.MatchColorsPlaceholder.route) {
                MatchColorsPlaceholderScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
