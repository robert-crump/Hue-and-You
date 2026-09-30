package com.example.hueandyou.data.outfit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [OutfitRepository] for ViewModel tests. Outfits are listed newest (highest id) first. */
class FakeOutfitRepository(vararg initialOutfits: Outfit) : OutfitRepository {
    val outfits = MutableStateFlow(initialOutfits.sortedByDescending { it.id })
    private var nextId = (initialOutfits.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeOutfits(): Flow<List<Outfit>> = outfits

    override fun observeOutfit(outfitId: Long): Flow<Outfit?> =
        outfits.map { list -> list.find { it.id == outfitId } }

    override suspend fun createOutfit(name: String, entryIds: List<Long>): Long {
        val id = nextId++
        outfits.value = listOf(Outfit(id, name, createdAt = id, entryIds = entryIds)) + outfits.value
        return id
    }

    override suspend fun updateOutfit(outfitId: Long, name: String, entryIds: List<Long>) {
        outfits.value = outfits.value.map { if (it.id == outfitId) it.copy(name = name, entryIds = entryIds) else it }
    }

    override suspend fun deleteOutfit(outfitId: Long) {
        outfits.value = outfits.value.filterNot { it.id == outfitId }
    }
}
