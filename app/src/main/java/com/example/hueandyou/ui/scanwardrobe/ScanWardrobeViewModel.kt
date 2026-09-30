package com.example.hueandyou.ui.scanwardrobe

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
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.colorspace.PaletteScorer
import com.example.hueandyou.colorspace.PixelSource
import com.example.hueandyou.data.history.ClothingCategory
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.NewClothingResult
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.data.settings.SettingsRepository
import com.example.hueandyou.ui.common.clothingCategoryLabel
import com.example.hueandyou.ui.common.decodePhoto
import com.example.hueandyou.ui.common.toPixelSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scan wardrobe: photographs many items of one [category] in a row, then names and saves them all
 * at once. Every item is saved as owned, in [category], scored against the last-used Clothes
 * profile. Default names are "<[categoryLabel]> N", numbered in capture order.
 */
class ScanWardrobeViewModel(
    category: ClothingCategory,
    private val categoryLabel: String,
    private val profileRepository: ProfileRepository,
    private val historyRepository: HistoryRepository,
    private val thumbnailStore: ThumbnailStore,
    private val settingsRepository: SettingsRepository,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val pixelSourceOf: (Bitmap) -> PixelSource = Bitmap::toPixelSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanWardrobeUiState(category = category))
    val uiState: StateFlow<ScanWardrobeUiState> = _uiState.asStateFlow()

    /** Null when there are no profiles: items are then saved without a verdict, as in Rate Clothing. */
    private var profile: Profile? = null

    private var nextKey = 1L

    /** The number in the next default name; a retake of the newest capture gives its number back. */
    private var nextNumber = 1

    init {
        viewModelScope.launch {
            val profiles = profileRepository.observeProfiles().first()
            val lastUsedId = settingsRepository.observeLastUsedClothingProfileId().first()
            profile = profiles.firstOrNull { it.id == lastUsedId } ?: profiles.firstOrNull()
        }
    }

    fun onPhotoPicked(contentResolver: ContentResolver, uri: Uri) {
        if (_uiState.value.step != ScanWardrobeStep.Viewfinder) return
        _uiState.update { it.copy(step = ScanWardrobeStep.Processing) }
        viewModelScope.launch {
            val bitmap = withContext(backgroundDispatcher) { decodePhoto(contentResolver, uri) }
            onPhotoDecoded(bitmap)
        }
    }

    /** Adds the photo to the batch with its most probable color and shows its per-photo screen. */
    internal fun onPhotoDecoded(bitmap: Bitmap) {
        _uiState.update { it.copy(step = ScanWardrobeStep.Processing) }
        viewModelScope.launch {
            val candidates = withContext(backgroundDispatcher) {
                ColorExtractor.extractCandidates(pixelSourceOf(bitmap))
            }.map { it.argb }
            val chips = ColorExtractor.selectTopColors(candidates)
            val item = ScannedItem(
                key = nextKey++,
                photo = bitmap,
                chipColorsArgb = chips,
                argb = chips.first(),
                defaultName = "$categoryLabel ${nextNumber++}",
            )
            _uiState.update {
                it.copy(items = it.items + item, step = ScanWardrobeStep.Photo(item.key, fromReview = false))
            }
        }
    }

    /** Per-photo screen: switches the item's color to another of its chips. */
    fun pickColor(argb: Int) {
        val item = _uiState.value.currentItem ?: return
        if (argb !in item.chipColorsArgb) return
        updateItem(item.key) { it.copy(argb = argb) }
    }

    /** Per-photo screen: keeps the item and goes back to the viewfinder. */
    fun nextItem() {
        if (_uiState.value.currentItem == null) return
        _uiState.update { it.copy(step = ScanWardrobeStep.Viewfinder) }
    }

    /** Per-photo screen: discards the item and goes back to the viewfinder. */
    fun retake() {
        val state = _uiState.value
        val item = state.currentItem ?: return
        if (item.key == nextKey - 1) nextNumber--
        _uiState.value = state.copy(items = state.items - item, step = ScanWardrobeStep.Viewfinder)
    }

    /** Per-photo screen "Done", the viewfinder's back with items in the batch, or a reopened item's back. */
    fun showReview() {
        _uiState.update { it.copy(step = ScanWardrobeStep.Review) }
    }

    /** Review list: reopens an item's per-photo screen. */
    fun reopen(itemKey: Long) {
        val state = _uiState.value
        if (state.step != ScanWardrobeStep.Review || state.items.none { it.key == itemKey }) return
        _uiState.value = state.copy(step = ScanWardrobeStep.Photo(itemKey, fromReview = true))
    }

    /** Review list "+": back to the viewfinder with the batch intact. */
    fun continueScanning() {
        if (_uiState.value.step != ScanWardrobeStep.Review) return
        _uiState.update { it.copy(step = ScanWardrobeStep.Viewfinder) }
    }

    /** Review list: any name is allowed, including duplicates; a blank one is saved as the default. */
    fun rename(itemKey: Long, name: String) {
        updateItem(itemKey) { it.copy(name = name) }
    }

    /** Review list: takes the item out of the batch. */
    fun remove(itemKey: Long) {
        _uiState.update { state -> state.copy(items = state.items.filterNot { it.key == itemKey }) }
    }

    /** Writes the whole batch to History in one transaction, then moves to [ScanWardrobeStep.Saved]. */
    fun save() {
        val state = _uiState.value
        if (state.step != ScanWardrobeStep.Review || state.items.isEmpty()) return
        _uiState.value = state.copy(step = ScanWardrobeStep.Saving)
        viewModelScope.launch {
            val profile = profile
            val results = state.items.map { item ->
                NewClothingResult(
                    thumbnailPath = thumbnailStore.save(item.photo),
                    calibratedArgb = item.argb,
                    profile = profile,
                    score = scoreFor(item.argb, profile),
                    chipColorsArgb = item.chipColorsArgb,
                    name = item.name.trim().ifEmpty { item.defaultName },
                    inWardrobe = true,
                    category = state.category,
                )
            }
            historyRepository.saveClothingResults(results)
            _uiState.update { it.copy(step = ScanWardrobeStep.Saved) }
        }
    }

    private fun updateItem(itemKey: Long, transform: (ScannedItem) -> ScannedItem) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.key == itemKey) transform(it) else it })
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
        fun factory(context: Context, category: ClothingCategory): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (context.applicationContext as HueAndYouApplication).container
                ScanWardrobeViewModel(
                    category = category,
                    categoryLabel = context.getString(clothingCategoryLabel(category)),
                    profileRepository = container.profileRepository,
                    historyRepository = container.historyRepository,
                    thumbnailStore = container.thumbnailStore,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
