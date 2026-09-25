package com.example.hueandyou.ui.rateclothing

import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.profile.Profile

sealed interface RateClothingUiState {
    data object LoadingProfiles : RateClothingUiState
    data object NoProfile : RateClothingUiState
    data class SelectingProfile(val profiles: List<Profile>) : RateClothingUiState

    data object PickingPhoto : RateClothingUiState
    data object LoadingPhoto : RateClothingUiState
    data object ExtractingColors : RateClothingUiState

    data class ShowingResult(
        val argb: Int,
        val score: PaletteScore,
        val historyEntryId: Long,
        val historyEntryName: String,
    ) : RateClothingUiState
}
