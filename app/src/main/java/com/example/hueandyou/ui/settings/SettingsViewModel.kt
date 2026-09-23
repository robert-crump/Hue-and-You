package com.example.hueandyou.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.data.backup.BackupRepository
import com.example.hueandyou.data.settings.HarmonyDefaults
import com.example.hueandyou.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val defaults: StateFlow<HarmonyDefaults> = repository.observeDefaults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HarmonyDefaults())

    fun setDefaultWheel(wheel: HarmonyWheel) {
        viewModelScope.launch { repository.setDefaultWheel(wheel) }
    }

    fun setDefaultBalance(balance: HarmonyBalance) {
        viewModelScope.launch { repository.setDefaultBalance(balance) }
    }

    fun exportBackup(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { backupRepository.exportBackup() }) }
    }

    fun importBackup(json: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { backupRepository.importBackup(json) }) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (context.applicationContext as HueAndYouApplication).container
                SettingsViewModel(container.settingsRepository, container.backupRepository)
            }
        }
    }
}
