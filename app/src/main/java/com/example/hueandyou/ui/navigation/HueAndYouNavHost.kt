package com.example.hueandyou.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import com.example.hueandyou.data.history.HistoryEntryType
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

private const val KEY_SCROLL_HISTORY_TO_TOP = "scrollHistoryToTop"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueAndYouNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Leaving a freshly created rating: its entry is the newest, so History should show it at the top.
    val finishResult: () -> Unit = {
        navController.previousBackStackEntry?.savedStateHandle?.set(KEY_SCROLL_HISTORY_TO_TOP, true)
        navController.popBackStack()
    }

    // History's undo snackbar shares the FAB's spot, so the FAB lifts by the snackbar's height while it shows.
    var snackbarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val fabOffset by animateDpAsState(
        targetValue = if (snackbarHeightPx > 0) with(density) { -(snackbarHeightPx.toDp() + 8.dp) } else 0.dp,
        label = "fabOffset"
    )

    val currentRoute = currentDestination?.route

    // Only the three tab roots get this bar; every other screen brings its own top bar.
    val rootTab = topLevelDestinations.firstOrNull { it.destination.route == currentRoute }

    Scaffold(
        topBar = {
            if (rootTab != null) {
                val barColor = MaterialTheme.colorScheme.primaryContainer
                // Tonal step toward primary: close to the bar color, but visibly its own band.
                val statusBarColor = lerp(barColor, MaterialTheme.colorScheme.primary, 0.25f)
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(statusBarColor)
                    )
                    TopAppBar(
                        title = { Text(stringResource(rootTab.labelRes), fontWeight = FontWeight.Bold) },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = barColor,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { topLevel ->
                    val selected = currentDestination?.hierarchy?.any { destination ->
                        topLevel.screens.any { it.route == destination.route }
                    } == true || (
                        currentRoute == Destination.HistoryDetail.route &&
                            navController.previousBackStackEntry?.destination?.route == topLevel.destination.route
                        )
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                // Re-tapping the active tab returns to its root screen.
                                navController.popBackStack(topLevel.destination.route, inclusive = false)
                            } else {
                                navController.navigate(topLevel.navRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
            val fabTarget = when (currentRoute) {
                Destination.Clothes.route -> Destination.RateClothing
                Destination.Objects.route -> Destination.MatchObject
                else -> null
            }
            if (fabTarget != null) {
                LargeFloatingActionButton(
                    onClick = { navController.navigate(fabTarget.route) },
                    modifier = Modifier.offset(y = fabOffset)
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.history_fab_content_description),
                        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize)
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Clothes.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        ) {
            listOf(
                Destination.Clothes to HistoryEntryType.CLOTHING,
                Destination.Objects to HistoryEntryType.OBJECT,
            ).forEach { (destination, type) ->
                composable(destination.route) { historyEntry ->
                    val scrollToTop by historyEntry.savedStateHandle
                        .getStateFlow(KEY_SCROLL_HISTORY_TO_TOP, false)
                        .collectAsState()
                    HistoryScreen(
                        type = type,
                        scrollToTopRequested = scrollToTop,
                        onScrolledToTop = { historyEntry.savedStateHandle[KEY_SCROLL_HISTORY_TO_TOP] = false },
                        onSnackbarHeightChange = { snackbarHeightPx = it },
                        onOpenEntry = { entryId ->
                            navController.navigate(Destination.HistoryDetail.route(entryId))
                        }
                    )
                }
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
                    onFinished = finishResult,
                    onNavigateToProfileSettings = {
                        navController.navigate(Destination.Settings.route(scrollToProfiles = true))
                    }
                )
            }
            composable(Destination.MatchObject.route) {
                MatchObjectScreen(onNavigateBack = { navController.popBackStack() }, onFinished = finishResult)
            }
        }
    }
}
