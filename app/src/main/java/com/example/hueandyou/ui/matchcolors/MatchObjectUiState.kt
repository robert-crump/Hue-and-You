package com.example.hueandyou.ui.matchcolors

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel

sealed interface MatchObjectUiState {
    data object PickingPhoto : MatchObjectUiState
    data object LoadingPhoto : MatchObjectUiState
    data object ExtractingColors : MatchObjectUiState

    data class ShowingResult(
        val photo: Bitmap,
        val inputColorArgb: Int,
        /** The fixed top-3 chip row; only the highlight moves when a pick changes. */
        val chipColorsArgb: List<Int>,
        /** Where [inputColorArgb] was sampled from, normalized to [photo]'s size; null = center box. */
        val sampleX: Double?,
        val sampleY: Double?,
        val wheel: HarmonyWheel,
        val balance: HarmonyBalance,
        val historyEntryId: Long,
    ) : MatchObjectUiState
}
