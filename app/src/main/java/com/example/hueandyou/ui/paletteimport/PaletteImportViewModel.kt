package com.example.hueandyou.ui.paletteimport

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.colorspace.RectRegion
import com.example.hueandyou.colorspace.SwatchExtractor
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.MAX_COLORS_PER_KIND
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.ui.common.toPixelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PHOTO_DIMENSION_PX = 1024

private fun remainingSlots(existing: Int): Int = (MAX_COLORS_PER_KIND - existing).coerceAtLeast(0)

/**
 * Imports swatches into profile [profileId], or - when it is null (new-profile mode) - into a new
 * profile named [newProfileName] that is only created once the user confirms the review step.
 */
class PaletteImportViewModel(
    private val profileRepository: ProfileRepository,
    private val profileId: Long?,
    private val newProfileName: String = "",
) : ViewModel() {
    private val _uiState = MutableStateFlow<PaletteImportUiState>(PaletteImportUiState.PickingPhoto)
    val uiState: StateFlow<PaletteImportUiState> = _uiState.asStateFlow()

    private var pendingBestSwatches: List<ImportSwatch> = emptyList()
    private var nextSwatchId = 0

    fun onPhotoPicked(contentResolver: ContentResolver, uri: Uri) {
        _uiState.value = PaletteImportUiState.LoadingPhoto
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { decodeBitmap(contentResolver, uri) }
            _uiState.value = PaletteImportUiState.MarkingArea(bitmap, ColorKind.BEST)
        }
    }

    fun updateMarkedRect(rect: RectRegion) {
        val state = _uiState.value as? PaletteImportUiState.MarkingArea ?: return
        _uiState.value = state.copy(rect = rect)
    }

    fun confirmArea() {
        val state = _uiState.value as? PaletteImportUiState.MarkingArea ?: return
        advanceAfterArea(state, extractSwatches(state.bitmap, state.rect))
    }

    fun skipArea() {
        val state = _uiState.value as? PaletteImportUiState.MarkingArea ?: return
        advanceAfterArea(state, emptyList())
    }

    private fun advanceAfterArea(state: PaletteImportUiState.MarkingArea, swatches: List<ImportSwatch>) {
        if (state.kind == ColorKind.BEST) {
            pendingBestSwatches = swatches
            _uiState.value = PaletteImportUiState.MarkingArea(state.bitmap, ColorKind.AVOID)
            return
        }
        startReview(pendingBestSwatches, swatches)
    }

    @VisibleForTesting
    internal fun startReview(bestSwatches: List<ImportSwatch>, avoidSwatches: List<ImportSwatch>) {
        viewModelScope.launch {
            val profile = profileId?.let { profileRepository.observeProfile(it).first() }
            val bestSlots = remainingSlots(profile?.bestColors?.size ?: 0)
            val avoidSlots = remainingSlots(profile?.avoidColors?.size ?: 0)
            _uiState.value = PaletteImportUiState.Reviewing(
                bestSwatches = preselectWithinSlots(bestSwatches, bestSlots),
                avoidSwatches = preselectWithinSlots(avoidSwatches, avoidSlots),
                bestSlots = bestSlots,
                avoidSlots = avoidSlots,
            )
        }
    }

    private fun extractSwatches(bitmap: Bitmap, rect: RectRegion): List<ImportSwatch> {
        val colors = SwatchExtractor.extract(pixels = bitmap.toPixelSource(), region = rect)
        return colors.map { ImportSwatch(id = nextSwatchId++, argb = it.argb, share = it.share) }
    }

    fun toggleSwatch(kind: ColorKind, swatchId: Int) {
        val slots = (_uiState.value as? PaletteImportUiState.Reviewing)?.slots(kind) ?: return
        updateReviewing(kind) { swatches -> toggleWithinSlots(swatches, swatchId, slots) }
    }

    fun removeSwatch(kind: ColorKind, swatchId: Int) {
        updateReviewing(kind) { swatches -> swatches.filterNot { it.id == swatchId } }
    }

    /** Adds a selected swatch, unless [kind]'s list has no free slot left. */
    fun addManualSwatch(kind: ColorKind, argb: Int) {
        val state = _uiState.value as? PaletteImportUiState.Reviewing ?: return
        if (state.swatches(kind).count { it.selected } >= state.slots(kind)) return
        updateReviewing(kind) { swatches -> swatches + ImportSwatch(id = nextSwatchId++, argb = argb, share = 0.0) }
    }

    private fun updateReviewing(kind: ColorKind, transform: (List<ImportSwatch>) -> List<ImportSwatch>) {
        val state = _uiState.value as? PaletteImportUiState.Reviewing ?: return
        _uiState.value = when (kind) {
            ColorKind.BEST -> state.copy(bestSwatches = transform(state.bestSwatches))
            ColorKind.AVOID -> state.copy(avoidSwatches = transform(state.avoidSwatches))
        }
    }

    fun confirmImport() {
        val state = _uiState.value as? PaletteImportUiState.Reviewing ?: return
        // Leave Reviewing right away so a double tap can't create a second profile.
        _uiState.value = PaletteImportUiState.Saving
        viewModelScope.launch {
            val targetId = profileId ?: profileRepository.createProfile(newProfileName)
            state.bestSwatches.filter { it.selected }.forEach {
                profileRepository.addColor(targetId, ColorKind.BEST, it.argb)
            }
            state.avoidSwatches.filter { it.selected }.forEach {
                profileRepository.addColor(targetId, ColorKind.AVOID, it.argb)
            }
            _uiState.value = PaletteImportUiState.Done(targetId)
        }
    }

    private fun decodeBitmap(contentResolver: ContentResolver, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longestSide = maxOf(info.size.width, info.size.height)
            if (longestSide > MAX_PHOTO_DIMENSION_PX) {
                val scale = MAX_PHOTO_DIMENSION_PX.toFloat() / longestSide
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }

    companion object {
        fun factory(context: Context, profileId: Long?, newProfileName: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (context.applicationContext as HueAndYouApplication)
                    .container.profileRepository
                PaletteImportViewModel(repository, profileId, newProfileName)
            }
        }
    }
}
