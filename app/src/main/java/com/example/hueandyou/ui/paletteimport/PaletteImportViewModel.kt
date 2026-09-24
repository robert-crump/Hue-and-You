package com.example.hueandyou.ui.paletteimport

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.colorspace.CalibrationConfig
import com.example.hueandyou.colorspace.ColorExtractor
import com.example.hueandyou.colorspace.RectRegion
import com.example.hueandyou.colorspace.parseHexColor
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.ui.common.toPixelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PHOTO_DIMENSION_PX = 1024

class PaletteImportViewModel(
    private val profileRepository: ProfileRepository,
    private val profileId: Long,
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
        _uiState.value = if (state.kind == ColorKind.BEST) {
            pendingBestSwatches = swatches
            PaletteImportUiState.MarkingArea(state.bitmap, ColorKind.AVOID)
        } else {
            PaletteImportUiState.Reviewing(bestSwatches = pendingBestSwatches, avoidSwatches = swatches)
        }
    }

    private fun extractSwatches(bitmap: Bitmap, rect: RectRegion): List<ImportSwatch> {
        val extraction = ColorExtractor.extract(
            pixels = bitmap.toPixelSource(),
            correction = null,
            region = rect,
            clusterCount = CalibrationConfig.PALETTE_IMPORT_QUANTIZER_CLUSTER_COUNT,
            minShare = CalibrationConfig.PALETTE_IMPORT_MIN_COLOR_SHARE,
            maxColors = CalibrationConfig.PALETTE_IMPORT_MAX_COLORS,
        )
        return extraction.colors.map { ImportSwatch(id = nextSwatchId++, argb = it.argb, share = it.share) }
    }

    fun toggleSwatch(kind: ColorKind, swatchId: Int) {
        updateReviewing(kind) { swatches ->
            swatches.map { if (it.id == swatchId) it.copy(selected = !it.selected) else it }
        }
    }

    fun removeSwatch(kind: ColorKind, swatchId: Int) {
        updateReviewing(kind) { swatches -> swatches.filterNot { it.id == swatchId } }
    }

    /** Returns null on success, or an error message if [hex] isn't a valid hex color. */
    fun addManualSwatch(kind: ColorKind, hex: String, invalidHexMessage: String): String? {
        val argb = parseHexColor(hex) ?: return invalidHexMessage
        updateReviewing(kind) { swatches -> swatches + ImportSwatch(id = nextSwatchId++, argb = argb, share = 0.0) }
        return null
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
        viewModelScope.launch {
            state.bestSwatches.filter { it.selected }.forEach {
                profileRepository.addColor(profileId, ColorKind.BEST, it.argb)
            }
            state.avoidSwatches.filter { it.selected }.forEach {
                profileRepository.addColor(profileId, ColorKind.AVOID, it.argb)
            }
            _uiState.value = PaletteImportUiState.Done
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
        fun factory(context: Context, profileId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (context.applicationContext as HueAndYouApplication)
                    .container.profileRepository
                PaletteImportViewModel(repository, profileId)
            }
        }
    }
}
