package com.example.hueandyou.ui.matchcolors

import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel

sealed interface MatchObjectUiState {
    data object PickingPhoto : MatchObjectUiState
    data object LoadingPhoto : MatchObjectUiState
    data object ExtractingColors : MatchObjectUiState

    data class ShowingResult(
        val inputColorArgb: Int,
        val wheel: HarmonyWheel,
        val balance: HarmonyBalance,
        val historyEntryId: Long,
        val historyEntryName: String,
    ) : MatchObjectUiState
}
