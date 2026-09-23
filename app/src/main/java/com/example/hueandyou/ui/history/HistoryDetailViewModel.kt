package com.example.hueandyou.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryDetailViewModel(
    private val repository: HistoryRepository,
    private val entryId: Long,
) : ViewModel() {

    val entry: StateFlow<HistoryEntry?> = repository.observeEntry(entryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        viewModelScope.launch { repository.renameEntry(entryId, name) }
    }

    fun setWheel(wheel: HarmonyWheel) {
        val balance = entry.value?.balance ?: return
        viewModelScope.launch { repository.updateHarmonyOptions(entryId, wheel, balance) }
    }

    fun setBalance(balance: HarmonyBalance) {
        val wheel = entry.value?.wheel ?: return
        viewModelScope.launch { repository.updateHarmonyOptions(entryId, wheel, balance) }
    }

    companion object {
        fun factory(context: Context, entryId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (context.applicationContext as HueAndYouApplication)
                    .container.historyRepository
                HistoryDetailViewModel(repository, entryId)
            }
        }
    }
}
