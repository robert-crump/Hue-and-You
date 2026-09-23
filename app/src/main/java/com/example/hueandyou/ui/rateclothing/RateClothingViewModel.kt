package com.example.hueandyou.ui.rateclothing

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hueandyou.colorspace.ColorExtractor
import com.example.hueandyou.colorspace.WhiteBalanceCalibrator
import com.example.hueandyou.colorspace.WhiteBalanceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PHOTO_DIMENSION_PX = 1024

class RateClothingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<RateClothingUiState>(RateClothingUiState.PickingPhoto)
    val uiState: StateFlow<RateClothingUiState> = _uiState.asStateFlow()

    fun onPhotoPicked(contentResolver: ContentResolver, uri: Uri) {
        _uiState.value = RateClothingUiState.LoadingPhoto
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { decodeBitmap(contentResolver, uri) }
            _uiState.value = RateClothingUiState.Calibrating(bitmap)
        }
    }

    fun onTap(x: Int, y: Int) {
        val state = _uiState.value as? RateClothingUiState.Calibrating ?: return
        val result = WhiteBalanceCalibrator.calibrate(state.bitmap.toPixelSource(), x, y)
        _uiState.value = state.copy(calibration = result)
    }

    fun chooseNewPhoto() {
        _uiState.value = RateClothingUiState.PickingPhoto
    }

    fun confirmCalibration() {
        val state = _uiState.value as? RateClothingUiState.Calibrating ?: return
        val success = state.calibration as? WhiteBalanceResult.Success ?: return
        val extraction = ColorExtractor.extract(
            pixels = state.bitmap.toPixelSource(),
            correction = success.correction,
            exclusion = success.sampledRegion,
        )
        _uiState.value = if (extraction.isClearlyDominant && extraction.colors.isNotEmpty()) {
            RateClothingUiState.ShowingResult(extraction.colors.first().argb)
        } else {
            RateClothingUiState.SelectingColor(extraction.colors)
        }
    }

    fun selectColor(argb: Int) {
        _uiState.value = RateClothingUiState.ShowingResult(argb)
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
}
