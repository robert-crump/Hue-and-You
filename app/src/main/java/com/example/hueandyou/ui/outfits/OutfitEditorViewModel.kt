package com.example.hueandyou.ui.outfits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.data.history.ClothingCategory
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.outfit.OutfitRepository
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "Add items" sheet's filters and the Clothes items they let through, newest first. */
data class OutfitPickerState(
    val wardrobeOnly: Boolean = true,
    val category: ClothingCategory? = null,
    val items: List<HistoryEntry> = emptyList(),
)

data class OutfitEditorUiState(
    val isLoading: Boolean = true,
    /** False when editing a saved outfit. */
    val isNew: Boolean = true,
    /** The saved name, or null for a new outfit. */
    val name: String? = null,
    /** What the naming dialog keeps when left blank: the saved name, else "Outfit N". */
    val defaultName: String = "",
    /** In outfit order; an entry deleted meanwhile drops out. */
    val items: List<HistoryEntry> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfile: Profile? = null,
    /** Null while there are too few (or too many) items to rate. */
    val rating: OutfitRating? = null,
    val picker: OutfitPickerState = OutfitPickerState(),
    /** Whether the items differ from what was loaded, so leaving would lose something. */
    val hasChanges: Boolean = false,
    /** Set once the outfit is saved or deleted: the screen closes. */
    val isFinished: Boolean = false,
) {
    val canSave: Boolean get() = items.size in OUTFIT_MIN_ITEMS..OUTFIT_MAX_ITEMS
    val canAddMore: Boolean get() = items.size < OUTFIT_MAX_ITEMS
}

/**
 * Builds or edits one outfit of 2-8 Clothes items. Nothing is written until [save]. Both verdicts
 * are recomputed live from the items, the selected profile and the user's wheel.
 *
 * @param outfitId the outfit to edit, or null for a new one.
 * @param initialEntryIds a new outfit's items to start with (Make outfit from the Items list).
 * @param defaultNamePrefix "Outfit", localized; a new outfit's default name is "<prefix> N".
 */
class OutfitEditorViewModel(
    private val outfitId: Long?,
    initialEntryIds: List<Long>,
    private val defaultNamePrefix: String,
    private val outfitRepository: OutfitRepository,
    historyRepository: HistoryRepository,
    profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Loaded once in init; null until then. */
    private data class Loaded(val name: String?, val defaultName: String, val initialEntryIds: List<Long>)

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val entryIds = MutableStateFlow(initialEntryIds.distinct().take(OUTFIT_MAX_ITEMS))
    private val selectedProfileId = MutableStateFlow<Long?>(null)
    private val pickerWardrobeOnly = MutableStateFlow(true)
    private val pickerCategory = MutableStateFlow<ClothingCategory?>(null)
    private val isFinished = MutableStateFlow(false)

    /** Set while a save or delete is being written, so a second tap does nothing. */
    private var isWriting = false

    private val clothes = historyRepository.observeEntries()
        .map { entries -> entries.filter { it.type == HistoryEntryType.CLOTHING } }

    private val wheel = settingsRepository.observeDefaults().map { it.wheel }

    private val picker = combine(clothes, pickerWardrobeOnly, pickerCategory) { entries, wardrobeOnly, category ->
        OutfitPickerState(
            wardrobeOnly = wardrobeOnly,
            category = category,
            items = entries.filter { (!wardrobeOnly || it.inWardrobe) && (category == null || it.category == category) },
        )
    }

    private data class Scoring(val profiles: List<Profile>, val selectedProfileId: Long?, val wheel: HarmonyWheel)

    private val scoring = combine(profileRepository.observeProfiles(), selectedProfileId, wheel, ::Scoring)

    val uiState: StateFlow<OutfitEditorUiState> = combine(
        loaded, clothes, entryIds, scoring, combine(picker, isFinished, ::Pair)
    ) { loaded, clothes, ids, scoring, (picker, finished) ->
        if (loaded == null) return@combine OutfitEditorUiState()
        val byId = clothes.associateBy { it.id }
        val items = ids.mapNotNull { byId[it] }
        // A deleted profile falls back to the first one, as in Rate Clothing.
        val profile = scoring.profiles.firstOrNull { it.id == scoring.selectedProfileId } ?: scoring.profiles.firstOrNull()
        OutfitEditorUiState(
            isLoading = false,
            isNew = outfitId == null,
            name = loaded.name,
            defaultName = loaded.defaultName,
            items = items,
            profiles = scoring.profiles,
            selectedProfile = profile,
            rating = rateOutfit(items.map { it.calibratedArgb }, profile, scoring.wheel),
            picker = picker,
            hasChanges = ids != loaded.initialEntryIds,
            isFinished = finished,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OutfitEditorUiState())

    init {
        viewModelScope.launch {
            val outfits = outfitRepository.observeOutfits().first()
            val outfit = outfitId?.let { id -> outfits.find { it.id == id } }
            if (outfit != null) entryIds.value = outfit.entryIds
            selectedProfileId.value = settingsRepository.observeLastUsedClothingProfileId().first()
            loaded.value = Loaded(
                name = outfit?.name,
                defaultName = outfit?.name ?: "$defaultNamePrefix ${outfits.size + 1}",
                // A new outfit pre-filled from the Items list is already something to lose.
                initialEntryIds = outfit?.entryIds.orEmpty(),
            )
        }
    }

    /** Adds [entryId] at the end, unless it's already in or the outfit is full. */
    fun addItem(entryId: Long) {
        entryIds.update { ids -> if (entryId in ids || ids.size >= OUTFIT_MAX_ITEMS) ids else ids + entryId }
    }

    fun removeItem(entryId: Long) {
        entryIds.update { it - entryId }
    }

    /** The picker's tap: adds the item, or removes it if it's already in. */
    fun toggleItem(entryId: Long) {
        if (entryId in entryIds.value) removeItem(entryId) else addItem(entryId)
    }

    /** Moves an item [offset] places along the strip (negative = earlier), clamped to the ends. */
    fun moveItem(entryId: Long, offset: Int) {
        entryIds.update { ids ->
            val from = ids.indexOf(entryId)
            if (from < 0) return@update ids
            val to = (from + offset).coerceIn(0, ids.lastIndex)
            ids.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    /** Re-scores against [profile] and remembers it as the last-used Clothes profile. */
    fun switchProfile(profile: Profile) {
        if (profile.id == uiState.value.selectedProfile?.id) return
        selectedProfileId.value = profile.id
        viewModelScope.launch { settingsRepository.setLastUsedClothingProfileId(profile.id) }
    }

    fun setPickerWardrobeOnly(wardrobeOnly: Boolean) {
        pickerWardrobeOnly.value = wardrobeOnly
    }

    /** Null shows every category. */
    fun setPickerCategory(category: ClothingCategory?) {
        pickerCategory.value = category
    }

    /** The naming dialog's Save: writes the outfit (new or edited), then finishes. Needs 2-8 items. */
    fun save(name: String) {
        val loaded = loaded.value ?: return
        if (isWriting) return
        isWriting = true
        val finalName = name.trim().ifEmpty { loaded.defaultName }
        // Finished only once written: closing the screen clears this ViewModel and its scope.
        viewModelScope.launch {
            // From the sources rather than uiState, which may not have caught up with the latest edit.
            val existing = clothes.first().mapTo(HashSet()) { it.id }
            val ids = entryIds.value.filter { it in existing }
            if (ids.size !in OUTFIT_MIN_ITEMS..OUTFIT_MAX_ITEMS) {
                isWriting = false
                return@launch
            }
            if (outfitId == null) {
                outfitRepository.createOutfit(finalName, ids)
            } else {
                outfitRepository.updateOutfit(outfitId, finalName, ids)
            }
            isFinished.value = true
        }
    }

    /** Deletes the outfit being edited (not its items), then finishes. */
    fun delete() {
        if (outfitId == null || isWriting) return
        isWriting = true
        viewModelScope.launch {
            outfitRepository.deleteOutfit(outfitId)
            isFinished.value = true
        }
    }

    companion object {
        fun factory(context: Context, outfitId: Long?, initialEntryIds: List<Long>): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (context.applicationContext as HueAndYouApplication).container
                    OutfitEditorViewModel(
                        outfitId = outfitId,
                        initialEntryIds = initialEntryIds,
                        defaultNamePrefix = context.getString(R.string.outfit_default_name_prefix),
                        outfitRepository = container.outfitRepository,
                        historyRepository = container.historyRepository,
                        profileRepository = container.profileRepository,
                        settingsRepository = container.settingsRepository,
                    )
                }
            }
    }
}
