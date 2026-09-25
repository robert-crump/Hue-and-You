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
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
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
            val mainColorArgb = withContext(backgroundDispatcher) {
                ColorExtractor.extractMainColor(pixelSourceOf(bitmap))
            }
            val defaults = settingsRepository.observeDefaults().first()
            val thumbnailPath = thumbnailStore.save(bitmap)
            val entry = historyRepository.saveObjectResult(
                thumbnailPath = thumbnailPath,
                inputColorsArgb = listOf(mainColorArgb),
                wheel = defaults.wheel,
                balance = defaults.balance,
            )
            _uiState.value = MatchObjectUiState.ShowingResult(
                inputColorArgb = entry.inputColorsArgb.first(),
                wheel = entry.wheel ?: defaults.wheel,
                balance = entry.balance ?: defaults.balance,
                historyEntryId = entry.id,
                historyEntryName = entry.name,
            )
        }
    }

    fun renameResult(name: String) {
        val state = _uiState.value as? MatchObjectUiState.ShowingResult ?: return
        viewModelScope.launch { historyRepository.renameEntry(state.historyEntryId, name) }
    }

    fun setWheel(wheel: HarmonyWheel) {
        val state = _uiState.value as? MatchObjectUiState.ShowingResult ?: return
        _uiState.value = state.copy(wheel = wheel)
        viewModelScope.launch { historyRepository.updateHarmonyOptions(state.historyEntryId, wheel, state.balance) }
    }

    fun setBalance(balance: HarmonyBalance) {
        val state = _uiState.value as? MatchObjectUiState.ShowingResult ?: return
        _uiState.value = state.copy(balance = balance)
        viewModelScope.launch { historyRepository.updateHarmonyOptions(state.historyEntryId, state.wheel, balance) }
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
