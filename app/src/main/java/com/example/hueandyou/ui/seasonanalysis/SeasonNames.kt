package com.example.hueandyou.ui.seasonanalysis

import androidx.annotation.StringRes
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.Season

@StringRes
fun seasonNameRes(season: Season): Int = when (season) {
    Season.LIGHT_SPRING -> R.string.season_light_spring
    Season.TRUE_SPRING -> R.string.season_true_spring
    Season.BRIGHT_SPRING -> R.string.season_bright_spring
    Season.LIGHT_SUMMER -> R.string.season_light_summer
    Season.TRUE_SUMMER -> R.string.season_true_summer
    Season.SOFT_SUMMER -> R.string.season_soft_summer
    Season.SOFT_AUTUMN -> R.string.season_soft_autumn
    Season.TRUE_AUTUMN -> R.string.season_true_autumn
    Season.DEEP_AUTUMN -> R.string.season_deep_autumn
    Season.DEEP_WINTER -> R.string.season_deep_winter
    Season.TRUE_WINTER -> R.string.season_true_winter
    Season.BRIGHT_WINTER -> R.string.season_bright_winter
}
