package com.example.hueandyou.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.hueandyou.R

sealed class Destination(val route: String) {
    data object History : Destination("history")
    data object Settings : Destination("settings?scrollToProfiles={scrollToProfiles}") {
        const val ARG_SCROLL_TO_PROFILES = "scrollToProfiles"
        fun route(scrollToProfiles: Boolean = false) = "settings?scrollToProfiles=$scrollToProfiles"
    }
    data object ProfileEditor : Destination("profile_editor/{profileId}") {
        const val ARG_PROFILE_ID = "profileId"
        fun route(profileId: Long) = "profile_editor/$profileId"
    }
    data object RateClothing : Destination("rate_clothing")
    data object MatchColorsPlaceholder : Destination("match_colors_placeholder")
}

data class TopLevelDestination(
    val destination: Destination,
    val navRoute: String,
    val icon: ImageVector,
    val labelRes: Int
)

val topLevelDestinations = listOf(
    TopLevelDestination(Destination.History, Destination.History.route, Icons.Filled.History, R.string.nav_history),
    TopLevelDestination(
        Destination.Settings,
        Destination.Settings.route(scrollToProfiles = false),
        Icons.Filled.Settings,
        R.string.nav_settings
    )
)
