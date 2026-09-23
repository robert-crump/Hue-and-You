package com.example.hueandyou.ui.paletteimport

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.RectRegion
import com.example.hueandyou.data.profile.ColorKind

/** One extracted (or manually added) swatch under review, keyed by a locally-unique [id]. */
data class ImportSwatch(
    val id: Int,
    val argb: Int,
    val share: Double,
    val selected: Boolean = true,
)

sealed interface PaletteImportUiState {
    data object PickingPhoto : PaletteImportUiState
    data object LoadingPhoto : PaletteImportUiState

    data class MarkingArea(
        val bitmap: Bitmap,
        val kind: ColorKind,
        val rect: RectRegion? = null,
    ) : PaletteImportUiState

    data class Reviewing(
        val bestSwatches: List<ImportSwatch>,
        val avoidSwatches: List<ImportSwatch>,
    ) : PaletteImportUiState

    data object Done : PaletteImportUiState
}
