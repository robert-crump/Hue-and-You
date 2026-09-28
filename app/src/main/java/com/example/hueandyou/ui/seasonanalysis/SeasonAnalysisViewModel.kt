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
 * Find my season: for skin, hair and eyes in turn, a photo and a marker placed on it -> ranked
 * seasons -> a new profile with the chosen season's palette, named by [seasonName]. Nothing is
 * saved until [createProfile].
 */
class SeasonAnalysisViewModel(
    private val profileRepository: ProfileRepository,
    private val seasonName: (Season) -> String,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SeasonAnalysisUiState>(SeasonAnalysisUiState.Capturing())
    val uiState: StateFlow<SeasonAnalysisUiState> = _uiState.asStateFlow()

    private val _photo = MutableStateFlow<Bitmap?>(null)

    /** The current step's photo; kept out of [uiState] so the picking logic is testable without a Bitmap. */
    val photo: StateFlow<Bitmap?> = _photo.asStateFlow()

    /** Each finished or current step's photo, kept so Back can return to an earlier marker. */
    private val photos = mutableMapOf<SeasonFeature, Bitmap>()
    private val pixelSources = mutableMapOf<SeasonFeature, PixelSource>()

    fun onPhotoPicked(contentResolver: ContentResolver, uri: Uri) {
        val state = _uiState.value as? SeasonAnalysisUiState.Capturing ?: return
        _uiState.value = SeasonAnalysisUiState.LoadingPhoto(state.feature, state.picks)
        viewModelScope.launch {
            val (bitmap, source) = withContext(backgroundDispatcher) {
                val bitmap = decodeBitmap(contentResolver, uri)
                bitmap to bitmap.toPixelSource()
            }
            photos[state.feature] = bitmap
            _photo.value = bitmap
            startPlacing(state.feature, state.picks, source)
        }
    }

    /** Opens [feature]'s marker step on [source] with the marker in the middle. */
    @VisibleForTesting
    internal fun startPlacing(
        feature: SeasonFeature,
        picks: Map<SeasonFeature, FeaturePick>,
        source: PixelSource,
    ) {
        pixelSources[feature] = source
        val x = source.width / 2
        val y = source.height / 2
        _uiState.value = SeasonAnalysisUiState.Placing(feature, picks, sample(feature, source, x, y))
    }

    /** Moves the marker to ([x], [y]) in photo pixels, clamped to the photo, and samples its color there. */
    fun moveMarker(x: Int, y: Int) {
        val state = _uiState.value as? SeasonAnalysisUiState.Placing ?: return
        val source = pixelSources[state.feature] ?: return
        val clampedX = x.coerceIn(0, source.width - 1)
        val clampedY = y.coerceIn(0, source.height - 1)
        if (clampedX == state.marker.x && clampedY == state.marker.y) return
        _uiState.value = state.copy(marker = sample(state.feature, source, clampedX, clampedY))
    }

    /** Keeps the marker's color and moves on to the next photo, or to the result after the eyes. */
    fun next() {
        val state = _uiState.value as? SeasonAnalysisUiState.Placing ?: return
        val picks = state.picks + (state.feature to state.marker)
        val nextFeature = state.feature.next()
        if (nextFeature != null) {
            _photo.value = null
            _uiState.value = SeasonAnalysisUiState.Capturing(nextFeature, picks)
        } else {
            showResult(picks)
        }
    }

    private fun sample(feature: SeasonFeature, source: PixelSource, x: Int, y: Int): FeaturePick {
        val argb = ColorExtractor.extractColorAtPoint(source, x, y, radiusFraction = feature.sampleRadiusFraction())
        return FeaturePick(argb, x, y)
    }

    private fun showResult(picks: Map<SeasonFeature, FeaturePick>) {
        val topMatches = SeasonClassifier.classify(
            skin = picks.getValue(SeasonFeature.SKIN).argb,
            hair = picks.getValue(SeasonFeature.HAIR).argb,
            eyes = picks.getValue(SeasonFeature.EYES).argb,
        ).take(SEASON_RESULT_COUNT)
        _uiState.value = SeasonAnalysisUiState.ShowingResult(picks, topMatches, topMatches.first().season)
    }

    /** Reopens [feature]'s marker step on its kept photo with the marker where it was left. */
    private fun reopenPlacing(feature: SeasonFeature, picks: Map<SeasonFeature, FeaturePick>) {
        _photo.value = photos[feature]
        _uiState.value = SeasonAnalysisUiState.Placing(feature, picks - feature, picks.getValue(feature))
    }

    fun selectSeason(season: Season) {
        val state = _uiState.value as? SeasonAnalysisUiState.ShowingResult ?: return
        if (state.topMatches.none { it.season == season }) return
        _uiState.value = state.copy(selected = season)
    }

    /** Steps back one screen. Returns false on the skin camera, where back leaves the flow. */
    fun back(): Boolean {
        when (val state = _uiState.value) {
            is SeasonAnalysisUiState.ShowingResult -> reopenPlacing(SeasonFeature.EYES, state.picks)
            is SeasonAnalysisUiState.Placing -> {
                photos.remove(state.feature)
                pixelSources.remove(state.feature)
                _photo.value = null
                _uiState.value = SeasonAnalysisUiState.Capturing(state.feature, state.picks)
            }
            is SeasonAnalysisUiState.Capturing -> {
                val previous = state.feature.previous() ?: return false
                reopenPlacing(previous, state.picks)
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
