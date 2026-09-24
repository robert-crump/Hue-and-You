package com.example.hueandyou.ui.matchcolors

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.ExtractedColor
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.WhiteBalanceResult

sealed interface MatchObjectUiState {
    data object PickingPhoto : MatchObjectUiState
    data object LoadingPhoto : MatchObjectUiState
    data object ExtractingColors : MatchObjectUiState

    data class Calibrating(
        val bitmap: Bitmap,
        val calibration: WhiteBalanceResult? = null,
    ) : MatchObjectUiState

    data class SelectingColors(
        val colors: List<ExtractedColor>,
        val selectedArgb: Set<Int> = emptySet(),
    ) : MatchObjectUiState

    data class ShowingResult(
        val inputColorsArgb: List<Int>,
        val wheel: HarmonyWheel,
        val balance: HarmonyBalance,
        val historyEntryId: Long,
        val historyEntryName: String,
    ) : MatchObjectUiState
}
