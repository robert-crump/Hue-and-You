package com.example.hueandyou.ui.rateclothing

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.profile.Profile

sealed interface RateClothingUiState {
    data object LoadingProfiles : RateClothingUiState
    data object NoProfile : RateClothingUiState

    data object PickingPhoto : RateClothingUiState
    data object LoadingPhoto : RateClothingUiState
    data object ExtractingColors : RateClothingUiState

    data class ShowingResult(
        val photo: Bitmap,
        val argb: Int,
        val alternativesArgb: List<Int>,
        /** Where [argb] was sampled from, normalized to [photo]'s size; null = center box. */
        val sampleX: Double?,
        val sampleY: Double?,
        val score: PaletteScore,
        val historyEntryId: Long,
        /** Every profile, for the "For: <name>" switcher; the "For:" line is hidden below 2. */
        val profiles: List<Profile>,
        val selectedProfile: Profile?,
    ) : RateClothingUiState
}
