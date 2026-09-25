package com.example.hueandyou.ui.history

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.IntArrayPixelSource
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.colorspace.PaletteScorer
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.profile.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryDetailViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val backgroundDispatcher = StandardTestDispatcher(mainDispatcher.scheduler, "background")
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val bitmap = allocateWithoutConstructor(Bitmap::class.java)

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun clothingEntry(
        id: Long = 1L,
        calibratedArgb: Int = red,
        bestColorsArgb: List<Int> = listOf(0xFF00FF00.toInt()),
        avoidColorsArgb: List<Int> = listOf(0xFF000000.toInt()),
        sampleX: Double? = null,
        sampleY: Double? = null,
    ) = HistoryEntry(
        id = id,
        type = HistoryEntryType.CLOTHING,
        name = "Clothing",
        createdAt = 0L,
        thumbnailPath = "thumb_$id.jpg",
        calibratedArgb = calibratedArgb,
        profileId = 9L,
        profileName = "Autumn",
        bestColorsArgb = bestColorsArgb,
        avoidColorsArgb = avoidColorsArgb,
        score = PaletteScorer.score(calibratedArgb, bestColorsArgb, avoidColorsArgb),
        sampleX = sampleX,
        sampleY = sampleY,
    )

    private fun objectEntry(
        id: Long = 1L,
        calibratedArgb: Int = red,
        sampleX: Double? = null,
        sampleY: Double? = null,
    ) = HistoryEntry(
        id = id,
        type = HistoryEntryType.OBJECT,
        name = "Object",
        createdAt = 0L,
        thumbnailPath = "thumb_$id.jpg",
        calibratedArgb = calibratedArgb,
        profileId = null,
        profileName = null,
        bestColorsArgb = emptyList(),
        avoidColorsArgb = emptyList(),
        score = PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false),
        inputColorsArgb = listOf(calibratedArgb),
        wheel = HarmonyWheel.TRADITIONAL,
        balance = HarmonyBalance.FAITHFUL,
        sampleX = sampleX,
        sampleY = sampleY,
    )

    /** A photo whose center-box quantizes to mostly [red] with a [blue] region, like [MatchObjectViewModelTest]'s. */
    private fun twoColorPixelSource(): IntArrayPixelSource {
        val size = 20
        return IntArrayPixelSource(
            size,
            size,
            IntArray(size * size) { index ->
                val x = index % size
                val y = index / size
                if (x in 6 until 9 && y in 6 until 14) blue else red
            },
        )
    }

    private fun viewModel(
        repository: FakeHistoryRepository,
        entryId: Long = 1L,
        decodeThreadNames: MutableList<String>? = null,
        extractThreadNames: MutableList<String>? = null,
    ) = HistoryDetailViewModel(
        repository = repository,
        entryId = entryId,
        backgroundDispatcher = backgroundDispatcher,
        bitmapDecoder = { path ->
            decodeThreadNames?.add(Thread.currentThread().name)
            assertEquals("thumb_$entryId.jpg", path)
            bitmap
        },
        pixelSourceOf = {
            extractThreadNames?.add(Thread.currentThread().name)
            twoColorPixelSource()
        },
    )

    @Test
    fun opensEntry_decodesThumbnailAndExtractsCandidatesOffMainThreadThenShowsLoaded() {
        val repository = FakeHistoryRepository(clothingEntry())
        val decodeThreads = mutableListOf<String>()
        val extractThreads = mutableListOf<String>()
        val model = viewModel(repository, decodeThreadNames = decodeThreads, extractThreadNames = extractThreads)

        // Nothing has run yet: only the collector was launched.
        assertEquals(HistoryDetailUiState.Loading, model.uiState.value)
        assertTrue(decodeThreads.isEmpty())

        mainDispatcher.scheduler.runCurrent()

        assertEquals(1, decodeThreads.size)
        assertEquals(1, extractThreads.size)
        val state = model.uiState.value as HistoryDetailUiState.Loaded
        assertEquals(bitmap, state.photo)
        assertEquals(red, state.entry.calibratedArgb)
        assertEquals(listOf(red, blue), state.chipColorsArgb)
    }

    @Test
    fun pickCandidate_sameColorAsCurrent_isANoop() {
        val repository = FakeHistoryRepository(clothingEntry(calibratedArgb = red))
        val model = viewModel(repository)
        mainDispatcher.scheduler.runCurrent()

        model.pickCandidate(red)
        mainDispatcher.scheduler.runCurrent()

        assertNull(repository.lastClothingPick)
    }

    @Test
    fun pickCandidate_objectEntry_persistsNewColorAndResetsSamplePoint() {
        val repository = FakeHistoryRepository(objectEntry(calibratedArgb = red, sampleX = 0.2, sampleY = 0.8))
        val model = viewModel(repository)
        mainDispatcher.scheduler.runCurrent()

        model.pickCandidate(blue)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(Triple(1L, blue, null to null), repository.lastObjectPick)
        val state = model.uiState.value as HistoryDetailUiState.Loaded
        assertEquals(blue, state.entry.calibratedArgb)
        assertNull(state.entry.sampleX)
        assertNull(state.entry.sampleY)
        assertEquals(listOf(red, blue), state.chipColorsArgb)
    }

    @Test
    fun pickCandidate_clothingEntry_rescoresAgainstStoredSnapshotAndResetsSamplePoint() {
        val bestColorsArgb = listOf(0xFF00FF00.toInt())
        val avoidColorsArgb = listOf(0xFF000000.toInt())
        val repository = FakeHistoryRepository(
            clothingEntry(calibratedArgb = red, bestColorsArgb = bestColorsArgb, avoidColorsArgb = avoidColorsArgb, sampleX = 0.3, sampleY = 0.4)
        )
        val model = viewModel(repository)
        mainDispatcher.scheduler.runCurrent()

        model.pickCandidate(blue)
        mainDispatcher.scheduler.runCurrent()

        val expectedScore = PaletteScorer.score(blue, bestColorsArgb, avoidColorsArgb)
        assertEquals(1L to blue, repository.lastClothingPick?.let { it.entryId to it.argb })
        assertEquals(expectedScore, repository.lastClothingPick?.score)
        assertNull(repository.lastClothingPick?.sampleX)
        assertNull(repository.lastClothingPick?.sampleY)
        val state = model.uiState.value as HistoryDetailUiState.Loaded
        assertEquals(expectedScore, state.entry.score)
    }

    @Test
    fun pickCandidate_preMigrationClothingEntryWithNoStoredProfile_stillScoresAndUpdates() {
        val repository = FakeHistoryRepository(
            clothingEntry(calibratedArgb = red, bestColorsArgb = emptyList(), avoidColorsArgb = emptyList())
        )
        val model = viewModel(repository)
        mainDispatcher.scheduler.runCurrent()

        model.pickCandidate(blue)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false), repository.lastClothingPick?.score)
        val state = model.uiState.value as HistoryDetailUiState.Loaded
        assertEquals(blue, state.entry.calibratedArgb)
    }

    @Test
    fun pickCandidate_doesNotReExtractCandidatesFromThePhoto() {
        val repository = FakeHistoryRepository(objectEntry(calibratedArgb = red))
        val decodeThreads = mutableListOf<String>()
        val extractThreads = mutableListOf<String>()
        val model = viewModel(repository, decodeThreadNames = decodeThreads, extractThreadNames = extractThreads)
        mainDispatcher.scheduler.runCurrent()
        assertEquals(1, extractThreads.size)

        model.pickCandidate(blue)
        mainDispatcher.scheduler.runCurrent()

        // Extraction is a one-time cost on open; a re-pick only recomputes alternatives, not the candidates.
        assertEquals(1, decodeThreads.size)
        assertEquals(1, extractThreads.size)
    }

    @Test
    fun rename_updatesTheEntryName() {
        val repository = FakeHistoryRepository(clothingEntry())
        val model = viewModel(repository)
        mainDispatcher.scheduler.runCurrent()

        model.rename("New name")
        mainDispatcher.scheduler.runCurrent()

        assertEquals(listOf(1L to "New name"), repository.renamedNames)
    }

    @Test
    fun entryDeletedElsewhere_showsNotFound() {
        val repository = FakeHistoryRepository(clothingEntry())
        val model = viewModel(repository)
        mainDispatcher.scheduler.runCurrent()

        repository.entryFlow.value = null
        mainDispatcher.scheduler.runCurrent()

        assertEquals(HistoryDetailUiState.NotFound, model.uiState.value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateWithoutConstructor(type: Class<T>): T {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }

    private data class ClothingPickCall(
        val entryId: Long,
        val argb: Int,
        val score: PaletteScore,
        val sampleX: Double?,
        val sampleY: Double?,
    )

    private class FakeHistoryRepository(initialEntry: HistoryEntry?) : HistoryRepository {
        val entryFlow = MutableStateFlow(initialEntry)
        val renamedNames = mutableListOf<Pair<Long, String>>()
        var lastObjectPick: Triple<Long, Int, Pair<Double?, Double?>>? = null
        var lastClothingPick: ClothingPickCall? = null

        override fun observeEntries() = throw UnsupportedOperationException("not used by this test")
        override fun observeEntry(entryId: Long): Flow<HistoryEntry?> = entryFlow

        override suspend fun saveClothingResult(
            thumbnailPath: String,
            calibratedArgb: Int,
            profile: Profile?,
            score: PaletteScore,
        ): HistoryEntry = throw UnsupportedOperationException("not used by this test")

        override suspend fun saveObjectResult(
            thumbnailPath: String,
            inputColorsArgb: List<Int>,
            wheel: HarmonyWheel,
            balance: HarmonyBalance,
        ): HistoryEntry = throw UnsupportedOperationException("not used by this test")

        override suspend fun renameEntry(entryId: Long, name: String) {
            renamedNames += entryId to name
            entryFlow.value = entryFlow.value?.copy(name = name)
        }

        override suspend fun updateObjectPick(entryId: Long, argb: Int, sampleX: Double?, sampleY: Double?) {
            lastObjectPick = Triple(entryId, argb, sampleX to sampleY)
            entryFlow.value = entryFlow.value?.copy(
                calibratedArgb = argb,
                inputColorsArgb = listOf(argb),
                sampleX = sampleX,
                sampleY = sampleY,
            )
        }

        override suspend fun updateClothingPick(
            entryId: Long,
            argb: Int,
            score: PaletteScore,
            sampleX: Double?,
            sampleY: Double?,
        ) {
            lastClothingPick = ClothingPickCall(entryId, argb, score, sampleX, sampleY)
            entryFlow.value = entryFlow.value?.copy(
                calibratedArgb = argb,
                score = score,
                sampleX = sampleX,
                sampleY = sampleY,
            )
        }

        override suspend fun updateClothingProfile(entryId: Long, profile: Profile, score: PaletteScore) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun deleteEntry(entryId: Long) {
            throw UnsupportedOperationException("not used by this test")
        }
    }
}
