package com.example.hueandyou.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.hueandyou.R

sealed class Destination(val route: String) {
    data object Clothes : Destination("clothes")
    data object Objects : Destination("objects")
    data object HistoryDetail : Destination("history_detail/{entryId}") {
        const val ARG_ENTRY_ID = "entryId"
        fun route(entryId: Long) = "history_detail/$entryId"
    }
    data object Settings : Destination("settings?scrollToProfiles={scrollToProfiles}") {
        const val ARG_SCROLL_TO_PROFILES = "scrollToProfiles"
        fun route(scrollToProfiles: Boolean = false) = "settings?scrollToProfiles=$scrollToProfiles"
    }
    data object ProfileEditor : Destination("profile_editor/{profileId}") {
        const val ARG_PROFILE_ID = "profileId"
        fun route(profileId: Long) = "profile_editor/$profileId"
    }
    data object PaletteImport : Destination("palette_import/{profileId}") {
        const val ARG_PROFILE_ID = "profileId"
        fun route(profileId: Long) = "palette_import/$profileId"
    }
    data object RateClothing : Destination("rate_clothing")
    data object MatchObject : Destination("match_object")
}

/** [screens] are all destinations that keep this tab highlighted, so exactly one tab is always active. */
data class TopLevelDestination(
    val destination: Destination,
    val navRoute: String,
    val icon: ImageVector,
    val labelRes: Int,
    val screens: List<Destination>,
)

val topLevelDestinations = listOf(
    TopLevelDestination(
        Destination.Clothes,
        Destination.Clothes.route,
        Icons.Filled.Checkroom,
        R.string.nav_clothes,
        screens = listOf(Destination.Clothes, Destination.RateClothing),
    ),
    TopLevelDestination(
        Destination.Objects,
        Destination.Objects.route,
        Icons.Filled.Chair,
        R.string.nav_objects,
        screens = listOf(Destination.Objects, Destination.MatchObject),
    ),
    TopLevelDestination(
        Destination.Settings,
        Destination.Settings.route(scrollToProfiles = false),
        Icons.Filled.Settings,
        R.string.nav_settings,
        screens = listOf(
            Destination.Settings,
            Destination.ProfileEditor,
            Destination.PaletteImport,
        ),
    )
)
