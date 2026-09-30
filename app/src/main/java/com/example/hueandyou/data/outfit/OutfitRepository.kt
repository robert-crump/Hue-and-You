package com.example.hueandyou.data.outfit

import com.example.hueandyou.data.backup.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A saved outfit: Clothes entries in order. Its verdicts are computed live, never stored. */
data class Outfit(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val entryIds: List<Long>,
)

interface OutfitRepository {
    /** Every outfit, newest first. */
    fun observeOutfits(): Flow<List<Outfit>>

    fun observeOutfit(outfitId: Long): Flow<Outfit?>

    /** Saves a new outfit with [entryIds] in that order and returns its id. */
    suspend fun createOutfit(name: String, entryIds: List<Long>): Long

    /** Replaces an outfit's name and items, all or nothing. */
    suspend fun updateOutfit(outfitId: Long, name: String, entryIds: List<Long>)

    suspend fun deleteOutfit(outfitId: Long)
}

/** How many of [outfits] contain at least one of [entryIds]: the History delete confirmation's "Used in N outfits". */
fun countOutfitsUsing(outfits: List<Outfit>, entryIds: Collection<Long>): Int =
    outfits.count { outfit -> outfit.entryIds.any { it in entryIds } }

class DefaultOutfitRepository(
    private val dao: OutfitDao,
    private val transactionRunner: TransactionRunner,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : OutfitRepository {

    override fun observeOutfits(): Flow<List<Outfit>> =
        combine(dao.observeOutfits(), dao.observeItems()) { outfits, items ->
            val itemsByOutfit = items.groupBy { it.outfitId }
            outfits.map { outfit ->
                Outfit(
                    id = outfit.id,
                    name = outfit.name,
                    createdAt = outfit.createdAt,
                    entryIds = itemsByOutfit[outfit.id].orEmpty().sortedBy { it.position }.map { it.entryId },
                )
            }
        }

    override fun observeOutfit(outfitId: Long): Flow<Outfit?> =
        observeOutfits().map { outfits -> outfits.find { it.id == outfitId } }

    override suspend fun createOutfit(name: String, entryIds: List<Long>): Long =
        transactionRunner.runInTransaction {
            val id = dao.insertOutfit(OutfitEntity(name = name, createdAt = currentTimeMillis()))
            insertItems(id, entryIds)
            id
        }

    override suspend fun updateOutfit(outfitId: Long, name: String, entryIds: List<Long>) {
        transactionRunner.runInTransaction {
            dao.updateName(outfitId, name)
            dao.deleteItems(outfitId)
            insertItems(outfitId, entryIds)
        }
    }

    override suspend fun deleteOutfit(outfitId: Long) {
        dao.deleteOutfit(outfitId)
    }

    private suspend fun insertItems(outfitId: Long, entryIds: List<Long>) {
        val items = entryIds.distinct().mapIndexed { index, entryId -> OutfitItemEntity(outfitId, entryId, index) }
        if (items.isNotEmpty()) dao.insertItems(items)
    }
}
