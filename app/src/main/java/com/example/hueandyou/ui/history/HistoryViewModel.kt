package com.example.hueandyou.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.outfit.OutfitRepository
import com.example.hueandyou.data.outfit.countOutfitsUsing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a deleted batch stays recoverable via the Undo snackbar before it's finalized. */
private const val UNDO_WINDOW_MILLIS = 4_000L

/** The most recent deleted batch, still undoable. [entryName] is set only for a single-entry batch. */
data class PendingDeletion(val entryIds: List<Long>, val entryName: String?) {
    val count: Int get() = entryIds.size
}

/** Deleting [itemCount] selected items that [outfitCount] outfits use: asked before deleting. */
data class DeleteConfirmation(val itemCount: Int, val outfitCount: Int)

/** The Clothes Items tab's filter on the "In my wardrobe" flag. */
enum class WardrobeFilter {
    ALL,
    WARDROBE,
    NOT_OWNED;

    fun matches(entry: HistoryEntry): Boolean = when (this) {
        ALL -> true
        WARDROBE -> entry.inWardrobe
        NOT_OWNED -> !entry.inWardrobe
    }
}

data class HistoryUiState(
    /** The visible entries, after [wardrobeFilter]. */
    val entries: List<HistoryEntry> = emptyList(),
    /** Whether there is anything to list before [wardrobeFilter]; false shows the empty state. */
    val hasEntries: Boolean = false,
    val wardrobeFilter: WardrobeFilter = WardrobeFilter.ALL,
    val pendingDeletion: PendingDeletion? = null,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val deleteConfirmation: DeleteConfirmation? = null,
) {
    val isEmpty: Boolean get() = !hasEntries
}

class HistoryViewModel(
    private val repository: HistoryRepository,
    private val type: HistoryEntryType? = null,
    /** Clothes only: to say which outfits a delete touches. Null skips that check. */
    private val outfitRepository: OutfitRepository? = null,
) : ViewModel() {

    /**
     * Batches currently within their undo window, oldest first: hidden from the list, not yet
     * deleted for real. Only the newest batch is offered for undo; older ones just run out their timer.
     */
    private val pendingBatches = MutableStateFlow<List<List<HistoryEntry>>>(emptyList())
    private val pendingBatchJobs = mutableMapOf<List<Long>, Job>()

    /** IDs already deleted from the repository; kept hidden in case the list flow is briefly stale. */
    private val deletedIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Null outside selection mode; may be empty inside it (delete is then disabled). */
    private val selectedIds = MutableStateFlow<Set<Long>?>(null)

    private val wardrobeFilter = MutableStateFlow(WardrobeFilter.ALL)

    private val deleteConfirmation = MutableStateFlow<DeleteConfirmation?>(null)

    private val selection = combine(selectedIds, deleteConfirmation, ::Pair)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeEntries(), pendingBatches, deletedIds, selection, wardrobeFilter
    ) { entries, batches, deleted, (selected, confirmation), filter ->
        val pendingIds = batches.flatten().mapTo(HashSet()) { it.id }
        val listed = entries.filter { (type == null || it.type == type) && it.id !in pendingIds && it.id !in deleted }
        HistoryUiState(
            entries = listed.filter(filter::matches),
            hasEntries = listed.isNotEmpty(),
            wardrobeFilter = filter,
            pendingDeletion = batches.lastOrNull()?.let { batch ->
                PendingDeletion(batch.map { it.id }, batch.singleOrNull()?.name)
            },
            isSelectionMode = selected != null,
            selectedIds = selected.orEmpty(),
            deleteConfirmation = confirmation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Clothes Items tab: shows all items, only owned ones, or only ones not owned. Leaves selection mode. */
    fun setWardrobeFilter(filter: WardrobeFilter) {
        exitSelectionMode()
        wardrobeFilter.value = filter
    }

    /** Long-press: enters selection mode with just [entryId] selected. */
    fun enterSelectionMode(entryId: Long) {
        selectedIds.value = setOf(entryId)
    }

    /** Tap while in selection mode: multiselect toggle. Deselecting the last one stays in selection mode. */
    fun toggleSelected(entryId: Long) {
        selectedIds.update { current -> current?.let { if (entryId in it) it - entryId else it + entryId } }
    }

    /** Close (X), back, or leaving the tab: exits selection mode and clears all selections. */
    fun exitSelectionMode() {
        selectedIds.value = null
    }

    /**
     * The selection bar's delete: when any selected item is in an outfit, asks first via
     * [HistoryUiState.deleteConfirmation] ("Used in N outfits"); otherwise deletes right away.
     */
    fun requestDeleteSelected() {
        val selected = selectedIds.value ?: return
        val outfitRepository = outfitRepository ?: return deleteSelected()
        viewModelScope.launch {
            val outfitCount = countOutfitsUsing(outfitRepository.observeOutfits().first(), selected)
            if (outfitCount > 0) {
                deleteConfirmation.value = DeleteConfirmation(selected.size, outfitCount)
            } else {
                deleteSelected()
            }
        }
    }

    /** The confirmation's Delete. */
    fun confirmDelete() {
        deleteConfirmation.value = null
        deleteSelected()
    }

    /** The confirmation's Cancel: the selection stays. */
    fun dismissDeleteConfirmation() {
        deleteConfirmation.value = null
    }

    /** Hides every selected entry immediately as one undoable batch and exits selection mode. */
    fun deleteSelected() {
        val selected = selectedIds.value.orEmpty()
        val batch = uiState.value.entries.filter { it.id in selected }
        exitSelectionMode()
        if (batch.isEmpty()) return
        val ids = batch.map { it.id }
        pendingBatches.update { it + listOf(batch) }
        pendingBatchJobs[ids] = viewModelScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            ids.forEach { repository.deleteEntry(it) }
            // Stay hidden after the delete: the list flow may still emit a stale list containing
            // the entries, which would flash them back into view when the pending state is cleared.
            deletedIds.update { it + ids }
            pendingBatches.update { batches -> batches.filterNot { batch -> batch.map { it.id } == ids } }
            pendingBatchJobs.remove(ids)
        }
    }

    /** Cancels a pending batch deletion, fully restoring its entries (they were never actually removed). */
    fun undoDelete(pending: PendingDeletion) {
        pendingBatchJobs.remove(pending.entryIds)?.cancel()
        pendingBatches.update { batches -> batches.filterNot { batch -> batch.map { it.id } == pending.entryIds } }
    }

    companion object {
        fun factory(context: Context, type: HistoryEntryType? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (context.applicationContext as HueAndYouApplication).container
                HistoryViewModel(
                    repository = container.historyRepository,
                    type = type,
                    outfitRepository = container.outfitRepository.takeIf { type == HistoryEntryType.CLOTHING },
                )
            }
        }
    }
}
