package com.example.hueandyou.ui.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val _isEditing = MutableStateFlow(false)

    /** Whether the pencil's edit mode (staged color removal) is active. */
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _pendingRemovals = MutableStateFlow<Set<Long>>(emptySet())

    /** Ids of colors removed in edit mode but not yet committed with the tick. */
    val pendingRemovals: StateFlow<Set<Long>> = _pendingRemovals.asStateFlow()

    fun addColor(kind: ColorKind, argb: Int) {
        viewModelScope.launch { repository.addColor(profileId, kind, argb) }
    }

    fun startEditing() {
        _pendingRemovals.value = emptySet()
        _isEditing.value = true
    }

    fun stageRemoval(colorId: Long) {
        if (_isEditing.value) _pendingRemovals.update { it + colorId }
    }

    /** Leaves edit mode, discarding every staged removal (X and system back). */
    fun cancelEditing() {
        _pendingRemovals.value = emptySet()
        _isEditing.value = false
    }

    /** Leaves edit mode, removing every staged color in one batch (the tick). */
    fun commitEditing() {
        val removals = _pendingRemovals.value
        _pendingRemovals.value = emptySet()
        _isEditing.value = false
        if (removals.isNotEmpty()) {
            viewModelScope.launch { repository.removeColors(removals) }
        }
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
