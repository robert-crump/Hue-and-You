package com.example.hueandyou.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a deleted entry stays recoverable via the Undo snackbar before it's finalized. */
private const val UNDO_WINDOW_MILLIS = 4_000L

data class PendingDeletion(val entryId: Long, val entryName: String)

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val pendingDeletion: PendingDeletion? = null
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {

    /** Entries currently within their undo window: hidden from the list, not yet deleted for real. */
    private val pendingDeletions = MutableStateFlow<Map<Long, HistoryEntry>>(emptyMap())
    private val pendingDeletionJobs = mutableMapOf<Long, Job>()

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeEntries(), pendingDeletions
    ) { entries, pending ->
        HistoryUiState(
            entries = entries.filterNot { it.id in pending },
            pendingDeletion = pending.values.lastOrNull()?.let { PendingDeletion(it.id, it.name) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Hides [entry] immediately; it's only actually deleted once the undo window elapses. */
    fun deleteEntry(entry: HistoryEntry) {
        pendingDeletions.update { it + (entry.id to entry) }
        pendingDeletionJobs[entry.id] = viewModelScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            repository.deleteEntry(entry.id)
            pendingDeletions.update { it - entry.id }
            pendingDeletionJobs.remove(entry.id)
        }
    }

    /** Cancels a pending deletion, fully restoring the entry (it was never actually removed). */
    fun undoDelete(entryId: Long) {
        pendingDeletionJobs.remove(entryId)?.cancel()
        pendingDeletions.update { it - entryId }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (context.applicationContext as HueAndYouApplication)
                    .container.historyRepository
                HistoryViewModel(repository)
            }
        }
    }
}
