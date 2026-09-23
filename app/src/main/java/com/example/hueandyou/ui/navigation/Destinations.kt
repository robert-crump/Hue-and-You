package com.example.hueandyou.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.hueandyou.R

sealed class Destination(val route: String) {
    data object History : Destination("history")
    data object Settings : Destination("settings")
    data object RateClothingPlaceholder : Destination("rate_clothing_placeholder")
    data object MatchColorsPlaceholder : Destination("match_colors_placeholder")
}

data class TopLevelDestination(
    val destination: Destination,
    val icon: ImageVector,
    val labelRes: Int
)

val topLevelDestinations = listOf(
    TopLevelDestination(Destination.History, Icons.Filled.History, R.string.nav_history),
    TopLevelDestination(Destination.Settings, Icons.Filled.Settings, R.string.nav_settings)
)
