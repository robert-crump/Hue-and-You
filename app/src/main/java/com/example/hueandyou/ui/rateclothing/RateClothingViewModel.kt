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

class RateClothingViewModel(
    private val profileRepository: ProfileRepository,
    private val historyRepository: HistoryRepository,
    private val thumbnailStore: ThumbnailStore,
    private val settingsRepository: SettingsRepository,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val pixelSourceOf: (Bitmap) -> PixelSource = Bitmap::toPixelSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RateClothingUiState>(RateClothingUiState.LoadingProfiles)
    val uiState: StateFlow<RateClothingUiState> = _uiState.asStateFlow()

    private var profiles: List<Profile> = emptyList()
    private var selectedProfile: Profile? = null

    /** Every candidate color from the current photo's center-box extraction, ranked by share. */
    private var candidates: List<Int> = emptyList()

    init {
        viewModelScope.launch {
            profiles = profileRepository.observeProfiles().first()
            if (profiles.isEmpty()) {
                _uiState.value = RateClothingUiState.NoProfile
            } else {
                val lastUsedId = settingsRepository.observeLastUsedClothingProfileId().first()
                selectedProfile = profiles.firstOrNull { it.id == lastUsedId } ?: profiles.first()
                _uiState.value = RateClothingUiState.PickingPhoto
            }
        }
    }

    /** Switches the profile the current result is scored against; re-scores and remembers the choice. */
    fun switchProfile(profile: Profile) {
        val state = _uiState.value as? RateClothingUiState.ShowingResult ?: return
        if (profile.id == selectedProfile?.id) return
        selectedProfile = profile
        val score = scoreFor(state.argb, profile)
        _uiState.value = state.copy(score = score, selectedProfile = profile)
        viewModelScope.launch {
            settingsRepository.setLastUsedClothingProfileId(profile.id)
            historyRepository.updateClothingProfile(state.historyEntryId, profile, score)
        }
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
                chipColorsArgb = ColorExtractor.selectTopColors(candidates),
                score = score,
                historyEntryId = entry.id,
                profiles = profiles,
                selectedProfile = profile,
            )
        }
    }

    /** Back to the viewfinder for the next item; the saved entry stays and the profile is kept. */
    fun startNewPhoto() {
        if (_uiState.value !is RateClothingUiState.ShowingResult) return
        candidates = emptyList()
        _uiState.value = RateClothingUiState.PickingPhoto
    }

    /** Re-picks the color from one of the chip alternatives (or the current color, a no-op). */
    fun pickCandidate(argb: Int) {
        val state = _uiState.value as? RateClothingUiState.ShowingResult ?: return
        if (argb == state.argb) return
        applyPick(state, argb)
    }

    private fun applyPick(state: RateClothingUiState.ShowingResult, argb: Int) {
        val score = scoreFor(argb, selectedProfile)
        _uiState.value = state.copy(
            argb = argb,
            score = score,
        )
        viewModelScope.launch {
            historyRepository.updateClothingPick(state.historyEntryId, argb, score, sampleX = null, sampleY = null)
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
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
