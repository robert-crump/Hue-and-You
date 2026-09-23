package com.example.hueandyou.ui.rateclothing

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.ExtractedColor
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.colorspace.WhiteBalanceResult
import com.example.hueandyou.data.profile.Profile

sealed interface RateClothingUiState {
    data object LoadingProfiles : RateClothingUiState
    data object NoProfile : RateClothingUiState
    data class SelectingProfile(val profiles: List<Profile>) : RateClothingUiState

    data object PickingPhoto : RateClothingUiState
    data object LoadingPhoto : RateClothingUiState

    data class Calibrating(
        val bitmap: Bitmap,
        val calibration: WhiteBalanceResult? = null,
    ) : RateClothingUiState

    data class SelectingColor(val colors: List<ExtractedColor>) : RateClothingUiState

    data class ShowingResult(
        val argb: Int,
        val score: PaletteScore,
        val historyEntryId: Long,
        val historyEntryName: String,
    ) : RateClothingUiState
}
