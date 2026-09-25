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
import com.example.hueandyou.colorspace.PixelSource
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
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

class RateClothingViewModel(
    private val profileRepository: ProfileRepository,
    private val historyRepository: HistoryRepository,
    private val thumbnailStore: ThumbnailStore,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val pixelSourceOf: (Bitmap) -> PixelSource = Bitmap::toPixelSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RateClothingUiState>(RateClothingUiState.LoadingProfiles)
    val uiState: StateFlow<RateClothingUiState> = _uiState.asStateFlow()

    private var selectedProfile: Profile? = null

    /** Every candidate color from the current photo's center-box extraction, ranked by share. */
    private var candidates: List<Int> = emptyList()

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
            val bitmap = withContext(backgroundDispatcher) { decodeBitmap(contentResolver, uri) }
            extractAndSaveResult(bitmap)
        }
    }

    private fun extractAndSaveResult(bitmap: Bitmap) {
        _uiState.value = RateClothingUiState.ExtractingColors
        viewModelScope.launch {
            val extractedCandidates = withContext(backgroundDispatcher) {
                ColorExtractor.extractCandidates(pixelSourceOf(bitmap))
            }
            candidates = extractedCandidates.map { it.argb }
            val argb = candidates.first()
            val alternatives = ColorExtractor.selectAlternatives(candidates, argb)
            val profile = selectedProfile
            val score = scoreFor(argb, profile)
            val thumbnailPath = thumbnailStore.save(bitmap)
            val entry = historyRepository.saveClothingResult(
                thumbnailPath = thumbnailPath,
                calibratedArgb = argb,
                profile = profile,
                score = score,
            )
            _uiState.value = RateClothingUiState.ShowingResult(
                photo = bitmap,
                argb = argb,
                alternativesArgb = alternatives,
                sampleX = null,
                sampleY = null,
                score = score,
                historyEntryId = entry.id,
            )
        }
    }

    /** Re-picks the color from one of the chip alternatives (or the current color, a no-op). */
    fun pickCandidate(argb: Int) {
        val state = _uiState.value as? RateClothingUiState.ShowingResult ?: return
        if (argb == state.argb) return
        applyPick(state, argb, sampleX = null, sampleY = null)
    }

    /** Re-picks the color by sampling around a tap on the photo, at normalized [x]/[y] in [0, 1]. */
    fun pickAtPoint(x: Double, y: Double) {
        val state = _uiState.value as? RateClothingUiState.ShowingResult ?: return
        viewModelScope.launch {
            val argb = withContext(backgroundDispatcher) {
                val pixels = pixelSourceOf(state.photo)
                ColorExtractor.extractColorAtPoint(
                    pixels,
                    x = (x * pixels.width).roundToInt(),
                    y = (y * pixels.height).roundToInt(),
                )
            }
            val latest = _uiState.value as? RateClothingUiState.ShowingResult ?: return@launch
            applyPick(latest, argb, sampleX = x, sampleY = y)
        }
    }

    private fun applyPick(state: RateClothingUiState.ShowingResult, argb: Int, sampleX: Double?, sampleY: Double?) {
        val alternatives = ColorExtractor.selectAlternatives(candidates, argb)
        val score = scoreFor(argb, selectedProfile)
        _uiState.value = state.copy(
            argb = argb,
            alternativesArgb = alternatives,
            sampleX = sampleX,
            sampleY = sampleY,
            score = score,
        )
        viewModelScope.launch {
            historyRepository.updateClothingPick(state.historyEntryId, argb, score, sampleX, sampleY)
        }
    }

    private fun scoreFor(argb: Int, profile: Profile?): PaletteScore = if (profile != null) {
        PaletteScorer.score(
            measuredArgb = argb,
            bestColors = profile.bestColors.map { it.argb },
            avoidColors = profile.avoidColors.map { it.argb },
        )
    } else {
        PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (context.applicationContext as HueAndYouApplication).container
                RateClothingViewModel(
                    profileRepository = container.profileRepository,
                    historyRepository = container.historyRepository,
                    thumbnailStore = container.thumbnailStore,
                )
            }
        }
    }
}
