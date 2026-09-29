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
import com.example.hueandyou.data.profile.isProfileNameTaken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileEditorViewModel(
    private val repository: ProfileRepository,
    private val profileId: Long
) : ViewModel() {

    val profile: StateFlow<Profile?> = repository.observeProfile(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isEditing = MutableStateFlow(false)

    /** Whether the pencil's edit mode (rename and staged color removal) is active. */
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _pendingRemovals = MutableStateFlow<Set<Long>>(emptySet())

    /** Ids of colors removed in edit mode but not yet committed with the tick. */
    val pendingRemovals: StateFlow<Set<Long>> = _pendingRemovals.asStateFlow()

    private val _draftName = MutableStateFlow("")

    /** The name being typed in edit mode; saved only with the tick. */
    val draftName: StateFlow<String> = _draftName.asStateFlow()

    /** Whether another profile already has [draftName] (trimmed, ignoring case); blocks the tick. */
    val isDraftNameTaken: StateFlow<Boolean> = combine(
        _draftName,
        repository.observeProfiles().map { profiles -> profiles.filter { it.id != profileId }.map { it.name } }
    ) { name, otherNames -> isProfileNameTaken(name, otherNames) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun addColor(kind: ColorKind, argb: Int) {
        viewModelScope.launch { repository.addColor(profileId, kind, argb) }
    }

    fun startEditing() {
        _pendingRemovals.value = emptySet()
        _draftName.value = profile.value?.name.orEmpty()
        _isEditing.value = true
    }

    fun updateDraftName(name: String) {
        if (_isEditing.value) _draftName.value = name
    }

    fun stageRemoval(colorId: Long) {
        if (_isEditing.value) _pendingRemovals.update { it + colorId }
    }

    /** Leaves edit mode, discarding the draft name and every staged removal (X and system back). */
    fun cancelEditing() {
        _pendingRemovals.value = emptySet()
        _isEditing.value = false
    }

    /**
     * Leaves edit mode, saving the trimmed draft name and removing every staged color in one
     * batch (the tick). A blank name is ignored so a profile never ends up nameless; a name another
     * profile already has is ignored too, as a backstop to the disabled tick.
     */
    fun commitEditing() {
        val removals = _pendingRemovals.value
        val newName = _draftName.value.trim().takeUnless { isDraftNameTaken.value }.orEmpty()
        _pendingRemovals.value = emptySet()
        _isEditing.value = false
        viewModelScope.launch {
            if (newName.isNotEmpty() && newName != profile.value?.name) {
                repository.renameProfile(profileId, newName)
            }
            if (removals.isNotEmpty()) repository.removeColors(removals)
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
