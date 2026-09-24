package com.example.hueandyou.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.example.hueandyou.ui.history.HistoryDetailScreen
import com.example.hueandyou.ui.history.HistoryScreen
import com.example.hueandyou.ui.matchcolors.MatchObjectScreen
import com.example.hueandyou.ui.paletteimport.PaletteImportScreen
import com.example.hueandyou.ui.profiles.ProfileEditorScreen
import com.example.hueandyou.ui.rateclothing.RateClothingScreen
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
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(visible = showFabMenu, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    navController.navigate(Destination.MatchObject.route)
                                },
                                modifier = Modifier.height(40.dp),
                                icon = { Icon(Icons.Filled.Chair, contentDescription = null) },
                                text = { Text(stringResource(R.string.history_fab_action_match_colors)) }
                            )
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    navController.navigate(Destination.RateClothing.route)
                                },
                                modifier = Modifier.height(40.dp),
                                icon = { Icon(Icons.Filled.Checkroom, contentDescription = null) },
                                text = { Text(stringResource(R.string.history_fab_action_rate_clothing)) }
                            )
                        }
                    }
                    FloatingActionButton(onClick = { showFabMenu = !showFabMenu }) {
                        Icon(
                            if (showFabMenu) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = stringResource(R.string.history_fab_content_description)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.History.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        ) {
            composable(Destination.History.route) {
                HistoryScreen(
                    onOpenEntry = { entryId ->
                        navController.navigate(Destination.HistoryDetail.route(entryId))
                    }
                )
            }
            composable(
                Destination.HistoryDetail.route,
                arguments = listOf(
                    navArgument(Destination.HistoryDetail.ARG_ENTRY_ID) { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getLong(Destination.HistoryDetail.ARG_ENTRY_ID) ?: 0L
                HistoryDetailScreen(
                    entryId = entryId,
                    onNavigateBack = { navController.popBackStack() }
                )
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
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToImportPalette = {
                        navController.navigate(Destination.PaletteImport.route(profileId))
                    }
                )
            }
            composable(
                Destination.PaletteImport.route,
                arguments = listOf(
                    navArgument(Destination.PaletteImport.ARG_PROFILE_ID) { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getLong(Destination.PaletteImport.ARG_PROFILE_ID) ?: 0L
                PaletteImportScreen(
                    profileId = profileId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Destination.RateClothing.route) {
                RateClothingScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProfileSettings = {
                        navController.navigate(Destination.Settings.route(scrollToProfiles = true))
                    }
                )
            }
            composable(Destination.MatchObject.route) {
                MatchObjectScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
