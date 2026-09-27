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

/**
 * Selects only the first [slots] swatches - they arrive most-dominant first - so an import can
 * never push a profile list past its cap.
 */
fun preselectWithinSlots(swatches: List<ImportSwatch>, slots: Int): List<ImportSwatch> =
    swatches.mapIndexed { index, swatch -> swatch.copy(selected = index < slots) }

/** Toggles swatch [swatchId], unless selecting it would exceed [slots] selected swatches. */
fun toggleWithinSlots(swatches: List<ImportSwatch>, swatchId: Int, slots: Int): List<ImportSwatch> {
    val target = swatches.find { it.id == swatchId } ?: return swatches
    if (!target.selected && swatches.count { it.selected } >= slots) return swatches
    return swatches.map { if (it.id == swatchId) it.copy(selected = !it.selected) else it }
}

/** The centered rectangle (60% of each dimension) the marking step starts with. */
fun defaultMarkingRect(width: Int, height: Int): RectRegion = RectRegion(
    left = (width * 0.2f).toInt(),
    top = (height * 0.2f).toInt(),
    right = (width * 0.8f).toInt(),
    bottom = (height * 0.8f).toInt(),
)

sealed interface PaletteImportUiState {
    data object PickingPhoto : PaletteImportUiState
    data object LoadingPhoto : PaletteImportUiState

    data class MarkingArea(
        val bitmap: Bitmap,
        val kind: ColorKind,
        val rect: RectRegion = defaultMarkingRect(bitmap.width, bitmap.height),
    ) : PaletteImportUiState

    /** [bestSlots]/[avoidSlots] are how many more colors each profile list can take. */
    data class Reviewing(
        val bestSwatches: List<ImportSwatch>,
        val avoidSwatches: List<ImportSwatch>,
        val bestSlots: Int,
        val avoidSlots: Int,
    ) : PaletteImportUiState {
        fun swatches(kind: ColorKind): List<ImportSwatch> =
            if (kind == ColorKind.BEST) bestSwatches else avoidSwatches

        fun slots(kind: ColorKind): Int = if (kind == ColorKind.BEST) bestSlots else avoidSlots
    }

    data object Done : PaletteImportUiState
}
