package com.example.hueandyou.data.history

import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [HistoryDao] double so repository logic can be unit tested without a real database. */
class FakeHistoryDao : HistoryDao {
    private val entries = mutableMapOf<Long, HistoryEntryEntity>()
    private var nextId = 1L
    private val entriesFlow = MutableStateFlow<List<HistoryEntryEntity>>(emptyList())

    private fun emit() {
        entriesFlow.value = entries.values.sortedByDescending { it.createdAt }
    }

    override fun observeEntries(): Flow<List<HistoryEntryEntity>> = entriesFlow

    override fun observeEntry(entryId: Long): Flow<HistoryEntryEntity?> =
        entriesFlow.map { list -> list.find { it.id == entryId } }

    override suspend fun getEntry(entryId: Long): HistoryEntryEntity? = entries[entryId]

    override suspend fun insert(entry: HistoryEntryEntity): Long {
        val id = nextId++
        entries[id] = entry.copy(id = id)
        emit()
        return id
    }

    override suspend fun updateName(entryId: Long, name: String) {
        entries[entryId]?.let { entries[entryId] = it.copy(name = name) }
        emit()
    }

    override suspend fun updateHarmonyOptions(entryId: Long, wheel: HarmonyWheel, balance: HarmonyBalance) {
        entries[entryId]?.let { entries[entryId] = it.copy(wheel = wheel, balance = balance) }
        emit()
    }

    override suspend fun delete(entryId: Long) {
        entries.remove(entryId)
        emit()
    }
}
