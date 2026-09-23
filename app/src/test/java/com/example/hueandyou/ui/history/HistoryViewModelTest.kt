package com.example.hueandyou.ui.history

import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.profile.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeHistoryRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeHistoryRepository(entry(1L, "Clothing A"), entry(2L, "Clothing B"))
        viewModel = HistoryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deleteEntry_hidesFromListImmediatelyWithoutTellingTheRepository() = runTest(dispatcher) {
        val states = mutableListOf<HistoryUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        runCurrent()

        viewModel.deleteEntry(entry(1L, "Clothing A"))
        runCurrent()

        assertEquals(listOf(2L), states.last().entries.map { it.id })
        assertEquals(1L, states.last().pendingDeletion?.entryId)
        assertTrue(repository.deletedIds.isEmpty())

        job.cancel()
    }

    @Test
    fun undoDelete_beforeWindowElapses_fullyRestoresEntryAndNeverDeletes() = runTest(dispatcher) {
        val states = mutableListOf<HistoryUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        runCurrent()

        viewModel.deleteEntry(entry(1L, "Clothing A"))
        runCurrent()
        viewModel.undoDelete(1L)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(1L, 2L), states.last().entries.map { it.id }.sorted())
        assertNull(states.last().pendingDeletion)
        assertTrue(repository.deletedIds.isEmpty())

        job.cancel()
    }

    @Test
    fun deleteEntry_afterWindowElapses_finalizesDeletionInRepository() = runTest(dispatcher) {
        val states = mutableListOf<HistoryUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        runCurrent()

        viewModel.deleteEntry(entry(1L, "Clothing A"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(1L), repository.deletedIds)
        assertEquals(listOf(2L), states.last().entries.map { it.id })
        assertNull(states.last().pendingDeletion)

        job.cancel()
    }

    private fun entry(id: Long, name: String) = HistoryEntry(
        id = id,
        type = HistoryEntryType.CLOTHING,
        name = name,
        createdAt = 0L,
        thumbnailPath = "thumb_$id.jpg",
        calibratedArgb = 0xFF000000.toInt(),
        profileId = null,
        profileName = null,
        bestColorsArgb = emptyList(),
        avoidColorsArgb = emptyList(),
        score = PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false)
    )
}

private class FakeHistoryRepository(vararg initialEntries: HistoryEntry) : HistoryRepository {
    private val entriesFlow = MutableStateFlow(initialEntries.toList())
    val deletedIds = mutableListOf<Long>()

    override fun observeEntries(): Flow<List<HistoryEntry>> = entriesFlow

    override fun observeEntry(entryId: Long): Flow<HistoryEntry?> =
        entriesFlow.map { list -> list.find { it.id == entryId } }

    override suspend fun saveClothingResult(
        thumbnailPath: String,
        calibratedArgb: Int,
        profile: Profile?,
        score: PaletteScore
    ): HistoryEntry = throw UnsupportedOperationException("not used by this test")

    override suspend fun saveObjectResult(
        thumbnailPath: String,
        inputColorsArgb: List<Int>,
        wheel: HarmonyWheel,
        balance: HarmonyBalance,
    ): HistoryEntry = throw UnsupportedOperationException("not used by this test")

    override suspend fun renameEntry(entryId: Long, name: String) {
        throw UnsupportedOperationException("not used by this test")
    }

    override suspend fun updateHarmonyOptions(entryId: Long, wheel: HarmonyWheel, balance: HarmonyBalance) {
        throw UnsupportedOperationException("not used by this test")
    }

    override suspend fun deleteEntry(entryId: Long) {
        deletedIds += entryId
        entriesFlow.value = entriesFlow.value.filterNot { it.id == entryId }
    }
}
