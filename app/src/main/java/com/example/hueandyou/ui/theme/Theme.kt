package com.example.hueandyou.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun HueAndYouTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = SeedPrimaryDark,
            onPrimary = SeedOnPrimaryDark
        )
        else -> lightColorScheme(
            primary = SeedPrimaryLight,
            onPrimary = SeedOnPrimaryLight
        )
    }

    val successColors = if (darkTheme) {
        SuccessColors(SuccessContainerDark, SuccessOnContainerDark)
    } else {
        SuccessColors(SuccessContainerLight, SuccessOnContainerLight)
    }

    CompositionLocalProvider(LocalSuccessColors provides successColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HueAndYouTypography,
            content = content
        )
    }
}
