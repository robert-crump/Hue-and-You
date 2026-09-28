package com.example.hueandyou.ui.seasonanalysis

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
import com.example.hueandyou.colorspace.CalibrationConfig
import com.example.hueandyou.colorspace.ColorExtractor
import com.example.hueandyou.colorspace.PixelSource
import com.example.hueandyou.colorspace.Season
import com.example.hueandyou.colorspace.SeasonClassifier
import com.example.hueandyou.data.profile.ColorKind
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

/** Larger than the other photo flows' 1024 px so the small iris sample still covers enough pixels. */
private const val MAX_PHOTO_DIMENSION_PX = 2048

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

/** [base] if no profile has that name yet, otherwise "[base] 2", "[base] 3", ... */
internal fun uniqueProfileName(base: String, existingNames: Collection<String>): String {
    if (base !in existingNames) return base
    return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it !in existingNames }
}

private fun SeasonFeature.sampleRadiusFraction(): Double = when (this) {
    SeasonFeature.EYES -> CalibrationConfig.EYE_TAP_SAMPLE_RADIUS_FRACTION
    SeasonFeature.SKIN, SeasonFeature.HAIR -> CalibrationConfig.TAP_SAMPLE_RADIUS_FRACTION
}

/**
 * Find my season: photo -> skin/hair/eye taps -> ranked seasons -> a new profile with the chosen
 * season's palette, named by [seasonName]. Nothing is saved until [createProfile].
 */
class SeasonAnalysisViewModel(
    private val profileRepository: ProfileRepository,
    private val seasonName: (Season) -> String,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SeasonAnalysisUiState>(SeasonAnalysisUiState.PickingPhoto)
    val uiState: StateFlow<SeasonAnalysisUiState> = _uiState.asStateFlow()

    private val _photo = MutableStateFlow<Bitmap?>(null)

    /** The photo being picked from; kept out of [uiState] so the picking logic is testable without a Bitmap. */
    val photo: StateFlow<Bitmap?> = _photo.asStateFlow()

    private var pixels: PixelSource? = null

    fun onPhotoPicked(contentResolver: ContentResolver, uri: Uri) {
        _uiState.value = SeasonAnalysisUiState.LoadingPhoto
        viewModelScope.launch {
            val (bitmap, source) = withContext(backgroundDispatcher) {
                val bitmap = decodeBitmap(contentResolver, uri)
                bitmap to bitmap.toPixelSource()
            }
            _photo.value = bitmap
            startPicking(source)
        }
    }

    @VisibleForTesting
    internal fun startPicking(source: PixelSource) {
        pixels = source
        _uiState.value = SeasonAnalysisUiState.PickingColors()
    }

    /** Makes [feature] the one the next tap picks, whether or not it's set already. */
    fun selectFeature(feature: SeasonFeature) {
        val state = _uiState.value as? SeasonAnalysisUiState.PickingColors ?: return
        _uiState.value = state.copy(active = feature)
    }

    /** Samples the active feature's color at ([x], [y]) in bitmap pixels, then moves on to the next missing one. */
    fun onPhotoTap(x: Int, y: Int) {
        val state = _uiState.value as? SeasonAnalysisUiState.PickingColors ?: return
        val feature = state.active ?: return
        val source = pixels ?: return
        if (x !in 0 until source.width || y !in 0 until source.height) return
        val argb = ColorExtractor.extractColorAtPoint(source, x, y, radiusFraction = feature.sampleRadiusFraction())
        val picks = state.picks + (feature to FeaturePick(argb, x, y))
        _uiState.value = state.copy(picks = picks, active = nextMissingFeature(picks, after = feature))
    }

    fun showResult() {
        val state = _uiState.value as? SeasonAnalysisUiState.PickingColors ?: return
        if (!state.canContinue) return
        val picks = state.picks
        val topMatches = SeasonClassifier.classify(
            skin = picks.getValue(SeasonFeature.SKIN).argb,
            hair = picks.getValue(SeasonFeature.HAIR).argb,
            eyes = picks.getValue(SeasonFeature.EYES).argb,
        ).take(SEASON_RESULT_COUNT)
        _uiState.value = SeasonAnalysisUiState.ShowingResult(picks, topMatches, topMatches.first().season)
    }

    fun selectSeason(season: Season) {
        val state = _uiState.value as? SeasonAnalysisUiState.ShowingResult ?: return
        if (state.topMatches.none { it.season == season }) return
        _uiState.value = state.copy(selected = season)
    }

    /** Steps back within the flow. Returns false on the photo step, where back leaves the flow. */
    fun back(): Boolean {
        when (val state = _uiState.value) {
            is SeasonAnalysisUiState.ShowingResult -> _uiState.value = SeasonAnalysisUiState.PickingColors(
                picks = state.picks,
                active = null,
            )
            is SeasonAnalysisUiState.PickingColors -> {
                pixels = null
                _photo.value = null
                _uiState.value = SeasonAnalysisUiState.PickingPhoto
            }
            else -> return false
        }
        return true
    }

    fun createProfile() {
        val state = _uiState.value as? SeasonAnalysisUiState.ShowingResult ?: return
        // Leave ShowingResult right away so a double tap can't create a second profile.
        _uiState.value = SeasonAnalysisUiState.Saving
        viewModelScope.launch {
            val existingNames = profileRepository.observeProfiles().first().map { it.name }
            val profileId = profileRepository.createProfile(uniqueProfileName(seasonName(state.selected), existingNames))
            state.bestColors.forEach { profileRepository.addColor(profileId, ColorKind.BEST, it) }
            state.avoidColors.forEach { profileRepository.addColor(profileId, ColorKind.AVOID, it) }
            _uiState.value = SeasonAnalysisUiState.Done(profileId)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val repository = (appContext as HueAndYouApplication).container.profileRepository
                SeasonAnalysisViewModel(repository, seasonName = { appContext.getString(seasonNameRes(it)) })
            }
        }
    }
}
