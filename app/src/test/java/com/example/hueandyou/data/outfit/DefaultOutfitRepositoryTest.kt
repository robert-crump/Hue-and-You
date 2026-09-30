package com.example.hueandyou.data.outfit

import com.example.hueandyou.data.backup.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DefaultOutfitRepositoryTest {

    private lateinit var dao: FakeOutfitDao
    private lateinit var repository: DefaultOutfitRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        dao = FakeOutfitDao()
        val passthrough = object : TransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        repository = DefaultOutfitRepository(dao, passthrough, currentTimeMillis = { now })
    }

    @Test
    fun createOutfit_keepsItemOrder_andListsNewestFirst() = runBlocking {
        val first = repository.createOutfit("Work", listOf(3L, 1L, 2L))
        now = 2_000L
        val second = repository.createOutfit("Weekend", listOf(5L, 4L))

        assertEquals(
            listOf(
                Outfit(second, "Weekend", 2_000L, listOf(5L, 4L)),
                Outfit(first, "Work", 1_000L, listOf(3L, 1L, 2L)),
            ),
            repository.observeOutfits().first(),
        )
    }

    @Test
    fun updateOutfit_replacesNameAndItems() = runBlocking {
        val id = repository.createOutfit("Work", listOf(1L, 2L, 3L))

        repository.updateOutfit(id, "Office", listOf(3L, 4L))

        assertEquals(Outfit(id, "Office", 1_000L, listOf(3L, 4L)), repository.observeOutfit(id).first())
    }

    @Test
    fun deleteOutfit_removesItAndItsItems() = runBlocking {
        val id = repository.createOutfit("Work", listOf(1L, 2L))

        repository.deleteOutfit(id)

        assertNull(repository.observeOutfit(id).first())
        assertEquals(emptyList<OutfitItemEntity>(), dao.items.value)
    }

    @Test
    fun aDeletedEntry_dropsOutOfItsOutfits_whichStay() = runBlocking {
        val id = repository.createOutfit("Work", listOf(1L, 2L))

        dao.removeEntry(1L)

        assertEquals(listOf(2L), repository.observeOutfit(id).first()?.entryIds)
    }

    @Test
    fun countOutfitsUsing_countsOutfitsContainingAnyOfTheEntries() {
        val outfits = listOf(
            Outfit(1L, "A", 0L, listOf(1L, 2L)),
            Outfit(2L, "B", 0L, listOf(2L, 3L)),
            Outfit(3L, "C", 0L, listOf(4L, 5L)),
        )

        assertEquals(2, countOutfitsUsing(outfits, setOf(2L)))
        assertEquals(3, countOutfitsUsing(outfits, setOf(1L, 3L, 5L)))
        assertEquals(0, countOutfitsUsing(outfits, setOf(9L)))
    }
}
