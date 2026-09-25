package com.example.hueandyou.ui.matchcolors

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.IntArrayPixelSource
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.settings.HarmonyDefaults
import com.example.hueandyou.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

@OptIn(ExperimentalCoroutinesApi::class)
class MatchObjectViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val backgroundDispatcher = StandardTestDispatcher(mainDispatcher.scheduler, "background")

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun extractAndSaveResult_showsSpinnerImmediately_extractsOffMainThread_andSavesOneColor() {
        val extractionThreadNames = mutableListOf<String>()
        val bitmap = allocateWithoutConstructor(Bitmap::class.java)
        val mainColor = 0xFF336699.toInt()
        val historyRepository = FakeHistoryRepository()
        val viewModel = MatchObjectViewModel(
            historyRepository = historyRepository,
            thumbnailStore = FakeThumbnailStore(),
            settingsRepository = FakeSettingsRepository(),
            backgroundDispatcher = backgroundDispatcher,
            pixelSourceOf = {
                extractionThreadNames += Thread.currentThread().name
                IntArrayPixelSource(2, 2, IntArray(4) { mainColor })
            },
        )

        invokeExtractAndSaveResult(viewModel, bitmap)

        // Nothing has run yet: the caller only flipped state and queued the work.
        assertEquals(MatchObjectUiState.ExtractingColors, viewModel.uiState.value)
        assertTrue(extractionThreadNames.isEmpty())

        mainDispatcher.scheduler.runCurrent()

        assertEquals(1, extractionThreadNames.size)
        val state = viewModel.uiState.value
        assertTrue(state is MatchObjectUiState.ShowingResult)
        state as MatchObjectUiState.ShowingResult
        assertEquals(mainColor, state.inputColorArgb)
        assertEquals(listOf(mainColor), historyRepository.savedInputColors)
    }

    @Test
    fun pickCandidate_updatesStateInstantlyAndPersistsToTheSameEntry() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val historyRepository = FakeHistoryRepository()
        val viewModel = MatchObjectViewModel(
            historyRepository = historyRepository,
            thumbnailStore = FakeThumbnailStore(),
            settingsRepository = FakeSettingsRepository(),
            backgroundDispatcher = backgroundDispatcher,
            pixelSourceOf = { twoColorPixelSource(red, blue) },
        )
        invokeExtractAndSaveResult(viewModel, allocateWithoutConstructor(Bitmap::class.java))
        mainDispatcher.scheduler.runCurrent()
        val initial = viewModel.uiState.value as MatchObjectUiState.ShowingResult
        assertEquals(red, initial.inputColorArgb)
        assertEquals(listOf(blue), initial.alternativesArgb)

        viewModel.pickCandidate(blue)

        // The state updates synchronously; only persistence needs the coroutine to run.
        val afterPick = viewModel.uiState.value as MatchObjectUiState.ShowingResult
        assertEquals(blue, afterPick.inputColorArgb)
        assertEquals(listOf(red), afterPick.alternativesArgb)
        assertEquals(null, afterPick.sampleX)
        assertEquals(null, afterPick.sampleY)
        assertEquals(initial.historyEntryId, afterPick.historyEntryId)

        mainDispatcher.scheduler.runCurrent()
        assertEquals(Triple(initial.historyEntryId, blue, null to null), historyRepository.lastObjectPick)
    }

    @Test
    fun pickAtPoint_samplesOffMainThreadThenUpdatesTheSameEntry() {
        val extractionThreadNames = mutableListOf<String>()
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val historyRepository = FakeHistoryRepository()
        val viewModel = MatchObjectViewModel(
            historyRepository = historyRepository,
            thumbnailStore = FakeThumbnailStore(),
            settingsRepository = FakeSettingsRepository(),
            backgroundDispatcher = backgroundDispatcher,
            pixelSourceOf = {
                extractionThreadNames += Thread.currentThread().name
                twoColorPixelSource(red, blue)
            },
        )
        invokeExtractAndSaveResult(viewModel, allocateWithoutConstructor(Bitmap::class.java))
        mainDispatcher.scheduler.runCurrent()
        val initial = viewModel.uiState.value as MatchObjectUiState.ShowingResult
        extractionThreadNames.clear()

        viewModel.pickAtPoint(0.35, 0.5)

        // Nothing has changed yet: the sampling work is only queued on the background dispatcher.
        assertEquals(initial, viewModel.uiState.value)
        assertTrue(extractionThreadNames.isEmpty())

        mainDispatcher.scheduler.runCurrent()

        assertEquals(1, extractionThreadNames.size)
        val afterTap = viewModel.uiState.value as MatchObjectUiState.ShowingResult
        assertEquals(blue, afterTap.inputColorArgb)
        assertEquals(0.35, afterTap.sampleX)
        assertEquals(0.5, afterTap.sampleY)
        assertEquals(initial.historyEntryId, afterTap.historyEntryId)
        assertEquals(Triple(initial.historyEntryId, blue, 0.35 to 0.5), historyRepository.lastObjectPick)
    }

    /**
     * A 20x20 image whose center box (x, y in [6, 14)) is mostly [red], with a [blue] strip at
     * x in [6, 9) - big enough to survive quantizing as its own cluster, and to tap into cleanly.
     */
    private fun twoColorPixelSource(red: Int, blue: Int): IntArrayPixelSource {
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

    private fun invokeExtractAndSaveResult(viewModel: MatchObjectViewModel, bitmap: Bitmap) {
        val method: Method = MatchObjectViewModel::class.java.getDeclaredMethod(
            "extractAndSaveResult",
            Bitmap::class.java,
        )
        method.isAccessible = true
        method.invoke(viewModel, bitmap)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateWithoutConstructor(type: Class<T>): T {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }

    private class FakeHistoryRepository : HistoryRepository {
        var savedInputColors: List<Int>? = null
        var lastObjectPick: Triple<Long, Int, Pair<Double?, Double?>>? = null

        override fun observeEntries() = throw UnsupportedOperationException("not used by this test")
        override fun observeEntry(entryId: Long) = throw UnsupportedOperationException("not used by this test")

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
        ): HistoryEntry {
            savedInputColors = inputColorsArgb
            return HistoryEntry(
                id = 1L,
                type = HistoryEntryType.OBJECT,
                name = "Object",
                createdAt = 0L,
                thumbnailPath = thumbnailPath,
                calibratedArgb = inputColorsArgb.first(),
                profileId = null,
                profileName = null,
                bestColorsArgb = emptyList(),
                avoidColorsArgb = emptyList(),
                score = PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false),
                inputColorsArgb = inputColorsArgb,
                wheel = wheel,
                balance = balance,
            )
        }

        override suspend fun renameEntry(entryId: Long, name: String) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun updateObjectPick(entryId: Long, argb: Int, sampleX: Double?, sampleY: Double?) {
            lastObjectPick = Triple(entryId, argb, sampleX to sampleY)
        }

        override suspend fun updateClothingPick(
            entryId: Long,
            argb: Int,
            score: PaletteScore,
            sampleX: Double?,
            sampleY: Double?,
        ) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun updateClothingProfile(entryId: Long, profile: Profile, score: PaletteScore) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun deleteEntry(entryId: Long) {
            throw UnsupportedOperationException("not used by this test")
        }
    }

    private class FakeThumbnailStore : ThumbnailStore {
        override suspend fun save(bitmap: Bitmap): String = "thumb.jpg"
        override suspend fun delete(path: String) = throw UnsupportedOperationException("not used by this test")
        override suspend fun readBytes(path: String): ByteArray = throw UnsupportedOperationException("not used by this test")
        override suspend fun writeBytes(bytes: ByteArray): String = throw UnsupportedOperationException("not used by this test")
    }

    private class FakeSettingsRepository : SettingsRepository {
        override fun observeDefaults(): Flow<HarmonyDefaults> = flowOf(HarmonyDefaults())
        override suspend fun setDefaultWheel(wheel: HarmonyWheel) = throw UnsupportedOperationException("not used by this test")
        override suspend fun setDefaultBalance(balance: HarmonyBalance) = throw UnsupportedOperationException("not used by this test")
        override fun observeLastUsedClothingProfileId(): Flow<Long?> = throw UnsupportedOperationException("not used by this test")
        override suspend fun setLastUsedClothingProfileId(profileId: Long) = throw UnsupportedOperationException("not used by this test")
    }
}
