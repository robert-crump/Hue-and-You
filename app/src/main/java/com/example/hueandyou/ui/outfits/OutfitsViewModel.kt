package com.example.hueandyou.ui.outfits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.outfit.OutfitRepository
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One row of the Outfits tab. [rating] is null when the outfit has too few items to rate. */
data class OutfitRow(
    val id: Long,
    val name: String,
    val colorsArgb: List<Int>,
    val rating: OutfitRating?,
)

data class OutfitsUiState(
    val isLoading: Boolean = true,
    val outfits: List<OutfitRow> = emptyList(),
)

/** The Clothes Outfits tab: every outfit, newest first, rated live against the last-used profile. */
class OutfitsViewModel(
    outfitRepository: OutfitRepository,
    historyRepository: HistoryRepository,
    profileRepository: ProfileRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<OutfitsUiState> = combine(
        outfitRepository.observeOutfits(),
        historyRepository.observeEntries(),
        profileRepository.observeProfiles(),
        settingsRepository.observeLastUsedClothingProfileId(),
        settingsRepository.observeDefaults(),
    ) { outfits, entries, profiles, lastUsedProfileId, defaults ->
        val colorById = entries.associate { it.id to it.calibratedArgb }
        val profile = profiles.firstOrNull { it.id == lastUsedProfileId } ?: profiles.firstOrNull()
        OutfitsUiState(
            isLoading = false,
            outfits = outfits.map { outfit ->
                val colors = outfit.entryIds.mapNotNull { colorById[it] }
                OutfitRow(outfit.id, outfit.name, colors, rateOutfit(colors, profile, defaults.wheel))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutfitsUiState())

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (context.applicationContext as HueAndYouApplication).container
                OutfitsViewModel(
                    outfitRepository = container.outfitRepository,
                    historyRepository = container.historyRepository,
                    profileRepository = container.profileRepository,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
