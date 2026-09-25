package com.example.hueandyou.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Fallback seed colors, used only on devices without dynamic color support.
val SeedPrimaryLight = Color(0xFF6750A4)
val SeedOnPrimaryLight = Color(0xFFFFFFFF)
val SeedPrimaryDark = Color(0xFFD0BCFF)
val SeedOnPrimaryDark = Color(0xFF381E72)

// The Rate Clothing "Yes" verdict card isn't part of Material's dynamic (wallpaper-derived)
// scheme, so it gets its own fixed light/dark green rather than one of the scheme's roles.
val SuccessContainerLight = Color(0xFFC6EFCE)
val SuccessOnContainerLight = Color(0xFF0B4619)
val SuccessContainerDark = Color(0xFF1E4B2A)
val SuccessOnContainerDark = Color(0xFFB6F2C1)

internal data class SuccessColors(val container: Color, val onContainer: Color)

internal val LocalSuccessColors = staticCompositionLocalOf {
    SuccessColors(SuccessContainerLight, SuccessOnContainerLight)
}
