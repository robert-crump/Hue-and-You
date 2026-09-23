package com.example.hueandyou.ui.rateclothing

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
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.colorspace.PaletteScorer
import com.example.hueandyou.colorspace.WhiteBalanceCalibrator
import com.example.hueandyou.colorspace.WhiteBalanceResult
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PHOTO_DIMENSION_PX = 1024

class RateClothingViewModel(private val profileRepository: ProfileRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<RateClothingUiState>(RateClothingUiState.LoadingProfiles)
    val uiState: StateFlow<RateClothingUiState> = _uiState.asStateFlow()

    private var selectedProfile: Profile? = null

    init {
        viewModelScope.launch {
            val profiles = profileRepository.observeProfiles().first()
            _uiState.value = when {
                profiles.isEmpty() -> RateClothingUiState.NoProfile
                profiles.size == 1 -> {
                    selectedProfile = profiles.first()
                    RateClothingUiState.PickingPhoto
                }
                else -> RateClothingUiState.SelectingProfile(profiles)
            }
        }
    }

    fun selectProfile(profile: Profile) {
        selectedProfile = profile
        _uiState.value = RateClothingUiState.PickingPhoto
    }

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
            resultFor(extraction.colors.first().argb)
        } else {
            RateClothingUiState.SelectingColor(extraction.colors)
        }
    }

    fun selectColor(argb: Int) {
        _uiState.value = resultFor(argb)
    }

    private fun resultFor(argb: Int): RateClothingUiState.ShowingResult {
        val profile = selectedProfile
        val score = if (profile != null) {
            PaletteScorer.score(
                measuredArgb = argb,
                bestColors = profile.bestColors.map { it.argb },
                avoidColors = profile.avoidColors.map { it.argb },
            )
        } else {
            PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false)
        }
        return RateClothingUiState.ShowingResult(argb, score)
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
                val repository = (context.applicationContext as HueAndYouApplication)
                    .container.profileRepository
                RateClothingViewModel(repository)
            }
        }
    }
}
