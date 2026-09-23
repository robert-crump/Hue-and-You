package com.example.hueandyou.ui.rateclothing

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.ExtractedColor
import com.example.hueandyou.colorspace.WhiteBalanceResult

sealed interface RateClothingUiState {
    data object PickingPhoto : RateClothingUiState
    data object LoadingPhoto : RateClothingUiState

    data class Calibrating(
        val bitmap: Bitmap,
        val calibration: WhiteBalanceResult? = null,
    ) : RateClothingUiState

    data class SelectingColor(val colors: List<ExtractedColor>) : RateClothingUiState

    data class ShowingResult(val argb: Int) : RateClothingUiState
}
