package com.example.hueandyou.ui.matchcolors

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
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
import com.example.hueandyou.ui.common.decodePhoto
import com.example.hueandyou.ui.common.toPixelSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private var saveJob: Job? = null

    fun onPhotoPicked(contentResolver: ContentResolver, uri: Uri) {
        _uiState.value = MatchObjectUiState.LoadingPhoto
        viewModelScope.launch {
            val bitmap = withContext(backgroundDispatcher) { decodePhoto(contentResolver, uri) }
            extractResult(bitmap)
        }
    }

    /** Shows the result without saving it: only [save] writes it to History. */
    private fun extractResult(bitmap: Bitmap) {
        _uiState.value = MatchObjectUiState.ExtractingColors
        viewModelScope.launch {
            val extractedCandidates = withContext(backgroundDispatcher) {
                ColorExtractor.extractCandidates(pixelSourceOf(bitmap))
            }
            candidates = extractedCandidates.map { it.argb }
            val defaults = settingsRepository.observeDefaults().first()
            _uiState.value = MatchObjectUiState.ShowingResult(
                photo = bitmap,
                inputColorArgb = candidates.first(),
                chipColorsArgb = ColorExtractor.selectTopColors(candidates),
                wheel = defaults.wheel,
                balance = defaults.balance,
            )
        }
    }

    /** Back to the viewfinder, discarding the unsaved result. */
    fun retakePhoto() {
        if (_uiState.value !is MatchObjectUiState.ShowingResult) return
        candidates = emptyList()
        _uiState.value = MatchObjectUiState.PickingPhoto
    }

    /** Re-picks the color from one of the chip alternatives (or the current color, a no-op). */
    fun pickCandidate(argb: Int) {
        val state = _uiState.value as? MatchObjectUiState.ShowingResult ?: return
        if (argb == state.inputColorArgb) return
        _uiState.value = state.copy(inputColorArgb = argb)
    }

    /** Saves the shown result to History under [name], then moves to [MatchObjectUiState.Saved]. */
    fun save(name: String) {
        val state = _uiState.value as? MatchObjectUiState.ShowingResult ?: return
        if (saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            historyRepository.saveObjectResult(
                thumbnailPath = thumbnailStore.save(state.photo),
                inputColorsArgb = listOf(state.inputColorArgb),
                wheel = state.wheel,
                balance = state.balance,
                chipColorsArgb = state.chipColorsArgb,
                name = name,
            )
            _uiState.value = MatchObjectUiState.Saved
        }
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
