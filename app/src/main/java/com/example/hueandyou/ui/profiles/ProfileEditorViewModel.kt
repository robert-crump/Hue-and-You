package com.example.hueandyou.ui.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.colorspace.parseHexColor
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.MoveDirection
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileEditorViewModel(
    private val repository: ProfileRepository,
    private val profileId: Long
) : ViewModel() {

    val profile: StateFlow<Profile?> = repository.observeProfile(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun renameProfile(name: String) {
        viewModelScope.launch { repository.renameProfile(profileId, name) }
    }

    /** Returns null on success, or an error message if [hex] isn't a valid hex color. */
    fun addColor(kind: ColorKind, hex: String, invalidHexMessage: String): String? {
        val argb = parseHexColor(hex) ?: return invalidHexMessage
        viewModelScope.launch { repository.addColor(profileId, kind, argb) }
        return null
    }

    fun removeColor(colorId: Long) {
        viewModelScope.launch { repository.removeColor(colorId) }
    }

    fun moveColor(colorId: Long, direction: MoveDirection) {
        viewModelScope.launch { repository.moveColor(colorId, direction) }
    }

    fun deleteProfile(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            onDeleted()
        }
    }

    companion object {
        fun factory(context: Context, profileId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (context.applicationContext as HueAndYouApplication)
                    .container.profileRepository
                ProfileEditorViewModel(repository, profileId)
            }
        }
    }
}
