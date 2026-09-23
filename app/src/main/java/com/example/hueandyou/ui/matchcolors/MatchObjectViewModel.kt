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
import com.example.hueandyou.colorspace.WhiteBalanceCalibrator
import com.example.hueandyou.colorspace.WhiteBalanceResult
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.settings.SettingsRepository
import com.example.hueandyou.ui.common.toPixelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PHOTO_DIMENSION_PX = 1024

class MatchObjectViewModel(
    private val historyRepository: HistoryRepository,
    private val thumbnailStore: ThumbnailStore,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MatchObjectUiState>(MatchObjectUiState.PickingPhoto)
    val uiState: StateFlow<MatchObjectUiState> = _uiState.asStateFlow()

    private var currentBitmap: Bitmap? = null

    fun onPhotoPicked(contentResolver: ContentResolver, uri: Uri) {
        _uiState.value = MatchObjectUiState.LoadingPhoto
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { decodeBitmap(contentResolver, uri) }
            currentBitmap = bitmap
            _uiState.value = MatchObjectUiState.Calibrating(bitmap)
        }
    }

    fun onTap(x: Int, y: Int) {
        val state = _uiState.value as? MatchObjectUiState.Calibrating ?: return
        val result = WhiteBalanceCalibrator.calibrate(state.bitmap.toPixelSource(), x, y)
        _uiState.value = state.copy(calibration = result)
    }

    fun chooseNewPhoto() {
        _uiState.value = MatchObjectUiState.PickingPhoto
    }

    fun confirmCalibration() {
        val state = _uiState.value as? MatchObjectUiState.Calibrating ?: return
        val success = state.calibration as? WhiteBalanceResult.Success ?: return
        val extraction = ColorExtractor.extract(
            pixels = state.bitmap.toPixelSource(),
            correction = success.correction,
            exclusion = success.sampledRegion,
        )
        _uiState.value = MatchObjectUiState.SelectingColors(extraction.colors)
    }

    fun toggleColorSelection(argb: Int) {
        val state = _uiState.value as? MatchObjectUiState.SelectingColors ?: return
        val selected = if (argb in state.selectedArgb) {
            state.selectedArgb - argb
        } else {
            state.selectedArgb + argb
        }
        _uiState.value = state.copy(selectedArgb = selected)
    }

    fun confirmColorSelection() {
        val state = _uiState.value as? MatchObjectUiState.SelectingColors ?: return
        if (state.selectedArgb.isEmpty()) return
        val bitmap = requireNotNull(currentBitmap)
        val inputColors = state.selectedArgb.toList()
        viewModelScope.launch {
            val defaults = settingsRepository.observeDefaults().first()
            val thumbnailPath = thumbnailStore.save(bitmap)
            val entry = historyRepository.saveObjectResult(
                thumbnailPath = thumbnailPath,
                inputColorsArgb = inputColors,
                wheel = defaults.wheel,
                balance = defaults.balance,
            )
            _uiState.value = MatchObjectUiState.ShowingResult(
                inputColorsArgb = entry.inputColorsArgb,
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
