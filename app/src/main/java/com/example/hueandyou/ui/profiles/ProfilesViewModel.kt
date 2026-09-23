package com.example.hueandyou.ui.profiles

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.data.share.ShareCardRenderer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(
    private val repository: ProfileRepository,
    private val shareCardRenderer: ShareCardRenderer,
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createProfile(defaultName: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createProfile(defaultName)
            onCreated(id)
        }
    }

    fun shareCard(profile: Profile, onRendered: (Uri) -> Unit) {
        viewModelScope.launch {
            onRendered(shareCardRenderer.render(profile))
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (context.applicationContext as HueAndYouApplication).container
                ProfilesViewModel(container.profileRepository, container.shareCardRenderer)
            }
        }
    }
}
