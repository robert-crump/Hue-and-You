package com.example.hueandyou.ui.matchcolors

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
import com.example.hueandyou.colorspace.ColorExtractor
import com.example.hueandyou.colorspace.PixelSource
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.settings.SettingsRepository
import com.example.hueandyou.ui.common.toPixelSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MAX_PHOTO_DIMENSION_PX = 1024

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

class MatchObjectViewModel(
    private val historyRepository: HistoryRepository,
    private val thumbnailStore: ThumbnailStore,
    private val settingsRepository: SettingsRepository,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val pixelSourceOf: (Bitmap) -> PixelSource = Bitmap::toPixelSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MatchObjectUiState>(MatchObjectUiState.PickingPhoto)
    val uiState: StateFlow<MatchObjectUiState> = _uiState.asStateFlow()

    /** Every candidate color from the current photo's center-box extraction, ranked by share. */
    private var candidates: List<Int> = emptyList()

    fun onPhotoPicked(contentResolver: ContentResolver, uri: Uri) {
        _uiState.value = MatchObjectUiState.LoadingPhoto
        viewModelScope.launch {
            val bitmap = withContext(backgroundDispatcher) { decodeBitmap(contentResolver, uri) }
            extractAndSaveResult(bitmap)
        }
    }

    private fun extractAndSaveResult(bitmap: Bitmap) {
        _uiState.value = MatchObjectUiState.ExtractingColors
        viewModelScope.launch {
            val extractedCandidates = withContext(backgroundDispatcher) {
                ColorExtractor.extractCandidates(pixelSourceOf(bitmap))
            }
            candidates = extractedCandidates.map { it.argb }
            val mainColorArgb = candidates.first()
            val defaults = settingsRepository.observeDefaults().first()
            val thumbnailPath = thumbnailStore.save(bitmap)
            val entry = historyRepository.saveObjectResult(
                thumbnailPath = thumbnailPath,
                inputColorsArgb = listOf(mainColorArgb),
                wheel = defaults.wheel,
                balance = defaults.balance,
            )
            _uiState.value = MatchObjectUiState.ShowingResult(
                photo = bitmap,
                inputColorArgb = entry.inputColorsArgb.first(),
                chipColorsArgb = ColorExtractor.selectTopColors(candidates),
                sampleX = null,
                sampleY = null,
                wheel = entry.wheel ?: defaults.wheel,
                balance = entry.balance ?: defaults.balance,
                historyEntryId = entry.id,
            )
        }
    }

    /** Re-picks the color from one of the chip alternatives (or the current color, a no-op). */
    fun pickCandidate(argb: Int) {
        val state = _uiState.value as? MatchObjectUiState.ShowingResult ?: return
        if (argb == state.inputColorArgb) return
        applyPick(state, argb, sampleX = null, sampleY = null)
    }

    /** Re-picks the color by sampling around a tap on the photo, at normalized [x]/[y] in [0, 1]. */
    fun pickAtPoint(x: Double, y: Double) {
        val state = _uiState.value as? MatchObjectUiState.ShowingResult ?: return
        viewModelScope.launch {
            val argb = withContext(backgroundDispatcher) {
                val pixels = pixelSourceOf(state.photo)
                ColorExtractor.extractColorAtPoint(
                    pixels,
                    x = (x * pixels.width).roundToInt(),
                    y = (y * pixels.height).roundToInt(),
                )
            }
            val latest = _uiState.value as? MatchObjectUiState.ShowingResult ?: return@launch
            applyPick(latest, argb, sampleX = x, sampleY = y)
        }
    }

    private fun applyPick(state: MatchObjectUiState.ShowingResult, argb: Int, sampleX: Double?, sampleY: Double?) {
        _uiState.value = state.copy(
            inputColorArgb = argb,
            sampleX = sampleX,
            sampleY = sampleY,
        )
        viewModelScope.launch { historyRepository.updateObjectPick(state.historyEntryId, argb, sampleX, sampleY) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (context.applicationContext as HueAndYouApplication).container
                MatchObjectViewModel(
                    historyRepository = container.historyRepository,
                    thumbnailStore = container.thumbnailStore,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
