package com.example.hueandyou.data.outfit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [OutfitDao] double. Deleting an outfit cascades to its items as in the database; the
 * cascade from a deleted Clothes entry is [removeEntry]'s job, since the fake history DAO can't reach here.
 */
class FakeOutfitDao : OutfitDao {
    private var nextId = 1L
    val outfits = MutableStateFlow<List<OutfitEntity>>(emptyList())
    val items = MutableStateFlow<List<OutfitItemEntity>>(emptyList())

    override fun observeOutfits(): Flow<List<OutfitEntity>> = outfits

    override fun observeItems(): Flow<List<OutfitItemEntity>> = items

    override suspend fun insertOutfit(outfit: OutfitEntity): Long {
        val id = nextId++
        outfits.value = (outfits.value + outfit.copy(id = id)).sortedByDescending { it.createdAt }
        return id
    }

    override suspend fun insertItems(items: List<OutfitItemEntity>) {
        this.items.value = (this.items.value + items).sortedWith(compareBy({ it.outfitId }, { it.position }))
    }

    override suspend fun updateName(outfitId: Long, name: String) {
        outfits.value = outfits.value.map { if (it.id == outfitId) it.copy(name = name) else it }
    }

    override suspend fun deleteItems(outfitId: Long) {
        items.value = items.value.filterNot { it.outfitId == outfitId }
    }

    override suspend fun deleteOutfit(outfitId: Long) {
        outfits.value = outfits.value.filterNot { it.id == outfitId }
        deleteItems(outfitId)
    }

    override suspend fun deleteAllOutfits() {
        outfits.value = emptyList()
        items.value = emptyList()
    }

    /** What the history_entries foreign key's cascade does when a Clothes entry is deleted. */
    fun removeEntry(entryId: Long) {
        items.value = items.value.filterNot { it.entryId == entryId }
    }
}
