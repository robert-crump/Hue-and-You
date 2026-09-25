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
        val alternativesArgb: List<Int>,
        /** Where [inputColorArgb] was sampled from, normalized to [photo]'s size; null = center box. */
        val sampleX: Double?,
        val sampleY: Double?,
        val wheel: HarmonyWheel,
        val balance: HarmonyBalance,
        val historyEntryId: Long,
    ) : MatchObjectUiState
}
