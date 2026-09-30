package com.example.hueandyou.ui.scanwardrobe

import android.graphics.Bitmap
import com.example.hueandyou.data.history.ClothingCategory

/** One photographed item of the batch; nothing is persisted until the whole batch is saved. */
data class ScannedItem(
    /** Stable within the batch, for list keys and addressing; not a database id. */
    val key: Long,
    val photo: Bitmap,
    /** The photo's top-3 chips; [argb] is one of them. */
    val chipColorsArgb: List<Int>,
    val argb: Int,
    /** "<Category> N", numbered in capture order; saved in place of a blank [name]. */
    val defaultName: String,
    val name: String = defaultName,
)

sealed interface ScanWardrobeStep {
    data object Viewfinder : ScanWardrobeStep

    /** Decoding the photo and extracting its colors. */
    data object Processing : ScanWardrobeStep

    /**
     * The per-photo screen for the item with [itemKey]: straight after its capture, or reopened
     * from the review list ([fromReview]), which is where back then returns to.
     */
    data class Photo(val itemKey: Long, val fromReview: Boolean) : ScanWardrobeStep

    data object Review : ScanWardrobeStep
    data object Saving : ScanWardrobeStep

    /** The batch was written to History; the flow closes. */
    data object Saved : ScanWardrobeStep
}

data class ScanWardrobeUiState(
    val category: ClothingCategory,
    val items: List<ScannedItem> = emptyList(),
    val step: ScanWardrobeStep = ScanWardrobeStep.Viewfinder,
) {
    /** The item on the per-photo screen, if that is the current step. */
    val currentItem: ScannedItem?
        get() = (step as? ScanWardrobeStep.Photo)?.let { photo -> items.firstOrNull { it.key == photo.itemKey } }
}
