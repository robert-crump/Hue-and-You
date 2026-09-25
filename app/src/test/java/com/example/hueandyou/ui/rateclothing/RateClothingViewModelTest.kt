package com.example.hueandyou.ui.rateclothing

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.IntArrayPixelSource
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.colorspace.PaletteScorer
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.MoveDirection
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.data.settings.HarmonyDefaults
import com.example.hueandyou.data.settings.SettingsRepository
import java.lang.reflect.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateClothingViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val backgroundDispatcher = StandardTestDispatcher(mainDispatcher.scheduler, "background")

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun profile(id: Long, name: String, bestArgb: Int, avoidArgb: Int) = Profile(
        id = id,
        name = name,
        createdAt = 0L,
        updatedAt = 0L,
        bestColors = listOf(PaletteColor(id * 10 + 1, ColorKind.BEST, bestArgb)),
        avoidColors = listOf(PaletteColor(id * 10 + 2, ColorKind.AVOID, avoidArgb)),
    )

    private fun viewModel(
        profiles: List<Profile>,
        lastUsedClothingProfileId: Long?,
        historyRepository: FakeHistoryRepository = FakeHistoryRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(lastUsedClothingProfileId),
    ) = RateClothingViewModel(
        profileRepository = FakeProfileRepository(profiles),
        historyRepository = historyRepository,
        thumbnailStore = FakeThumbnailStore(),
        settingsRepository = settingsRepository,
        backgroundDispatcher = backgroundDispatcher,
        pixelSourceOf = { redPixelSource() },
    )

    @Test
    fun init_rememberedProfileExists_isUsedWithNoSelectionStep() {
        val profileA = profile(1L, "Autumn", 0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val profileB = profile(2L, "Winter", 0xFF0000FF.toInt(), 0xFFFFFF00.toInt())
        val historyRepository = FakeHistoryRepository()
        val model = viewModel(listOf(profileA, profileB), lastUsedClothingProfileId = 2L, historyRepository = historyRepository)
        mainDispatcher.scheduler.runCurrent()

        // No selection step: init lands straight on PickingPhoto.
        assertEquals(RateClothingUiState.PickingPhoto, model.uiState.value)

        invokeExtractAndSaveResult(model, allocateWithoutConstructor(Bitmap::class.java))
        mainDispatcher.scheduler.runCurrent()

        val state = model.uiState.value as RateClothingUiState.ShowingResult
        assertEquals(profileB, state.selectedProfile)
        assertEquals(profileB, historyRepository.savedProfile)
    }

    @Test
    fun init_rememberedProfileWasDeleted_fallsBackToFirstProfile() {
        val profileA = profile(1L, "Autumn", 0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val profileB = profile(2L, "Winter", 0xFF0000FF.toInt(), 0xFFFFFF00.toInt())
        val model = viewModel(listOf(profileA, profileB), lastUsedClothingProfileId = 999L)
        mainDispatcher.scheduler.runCurrent()

        invokeExtractAndSaveResult(model, allocateWithoutConstructor(Bitmap::class.java))
        mainDispatcher.scheduler.runCurrent()

        val state = model.uiState.value as RateClothingUiState.ShowingResult
        assertEquals(profileA, state.selectedProfile)
    }

    @Test
    fun init_nothingRememberedYet_usesFirstProfile() {
        val profileA = profile(1L, "Autumn", 0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val model = viewModel(listOf(profileA), lastUsedClothingProfileId = null)
        mainDispatcher.scheduler.runCurrent()

        invokeExtractAndSaveResult(model, allocateWithoutConstructor(Bitmap::class.java))
        mainDispatcher.scheduler.runCurrent()

        val state = model.uiState.value as RateClothingUiState.ShowingResult
        assertEquals(profileA, state.selectedProfile)
        assertEquals(listOf(profileA), state.profiles)
    }

    @Test
    fun switchProfile_rescoresUpdatesTheHistoryEntryAndRemembersTheChoice() {
        val profileA = profile(1L, "Autumn", 0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val profileB = profile(2L, "Winter", 0xFF0000FF.toInt(), 0xFFFFFF00.toInt())
        val historyRepository = FakeHistoryRepository()
        val settingsRepository = FakeSettingsRepository(lastUsedClothingProfileId = 1L)
        val model = viewModel(
            listOf(profileA, profileB),
            lastUsedClothingProfileId = 1L,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
        )
        mainDispatcher.scheduler.runCurrent()
        invokeExtractAndSaveResult(model, allocateWithoutConstructor(Bitmap::class.java))
        mainDispatcher.scheduler.runCurrent()
        val initial = model.uiState.value as RateClothingUiState.ShowingResult
        assertEquals(profileA, initial.selectedProfile)

        model.switchProfile(profileB)

        // The state updates synchronously; only persistence needs the coroutine to run.
        val afterSwitch = model.uiState.value as RateClothingUiState.ShowingResult
        assertEquals(profileB, afterSwitch.selectedProfile)
        val expectedScore = PaletteScorer.score(
            measuredArgb = initial.argb,
            bestColors = profileB.bestColors.map { it.argb },
            avoidColors = profileB.avoidColors.map { it.argb },
        )
        assertEquals(expectedScore, afterSwitch.score)
        assertEquals(initial.historyEntryId, afterSwitch.historyEntryId)

        mainDispatcher.scheduler.runCurrent()
        assertEquals(listOf(2L), settingsRepository.setCalls)
        assertEquals(Triple(initial.historyEntryId, profileB, expectedScore), historyRepository.lastProfileUpdate)
    }

    @Test
    fun switchProfile_toTheAlreadySelectedProfile_isANoop() {
        val profileA = profile(1L, "Autumn", 0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val profileB = profile(2L, "Winter", 0xFF0000FF.toInt(), 0xFFFFFF00.toInt())
        val historyRepository = FakeHistoryRepository()
        val settingsRepository = FakeSettingsRepository(lastUsedClothingProfileId = 1L)
        val model = viewModel(
            listOf(profileA, profileB),
            lastUsedClothingProfileId = 1L,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
        )
        mainDispatcher.scheduler.runCurrent()
        invokeExtractAndSaveResult(model, allocateWithoutConstructor(Bitmap::class.java))
        mainDispatcher.scheduler.runCurrent()

        model.switchProfile(profileA)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(emptyList<Long>(), settingsRepository.setCalls)
        assertNull(historyRepository.lastProfileUpdate)
    }

    private fun redPixelSource(): IntArrayPixelSource {
        val size = 4
        return IntArrayPixelSource(size, size, IntArray(size * size) { 0xFFFF0000.toInt() })
    }

    private fun invokeExtractAndSaveResult(viewModel: RateClothingViewModel, bitmap: Bitmap) {
        val method: Method = RateClothingViewModel::class.java.getDeclaredMethod(
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

    private class FakeProfileRepository(private val profiles: List<Profile>) : ProfileRepository {
        override fun observeProfiles(): Flow<List<Profile>> = flowOf(profiles)
        override fun observeProfile(profileId: Long): Flow<Profile?> =
            throw UnsupportedOperationException("not used by this test")

        override suspend fun createProfile(name: String): Long =
            throw UnsupportedOperationException("not used by this test")

        override suspend fun renameProfile(profileId: Long, name: String) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun deleteProfile(profileId: Long) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long =
            throw UnsupportedOperationException("not used by this test")

        override suspend fun removeColor(colorId: Long) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun moveColor(colorId: Long, direction: MoveDirection) {
            throw UnsupportedOperationException("not used by this test")
        }
    }

    private class FakeSettingsRepository(lastUsedClothingProfileId: Long?) : SettingsRepository {
        private val lastUsedFlow = MutableStateFlow(lastUsedClothingProfileId)
        val setCalls = mutableListOf<Long>()

        override fun observeDefaults(): Flow<HarmonyDefaults> = flowOf(HarmonyDefaults())

        override suspend fun setDefaultWheel(wheel: HarmonyWheel) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun setDefaultBalance(balance: HarmonyBalance) {
            throw UnsupportedOperationException("not used by this test")
        }

        override fun observeLastUsedClothingProfileId(): Flow<Long?> = lastUsedFlow

        override suspend fun setLastUsedClothingProfileId(profileId: Long) {
            setCalls += profileId
            lastUsedFlow.value = profileId
        }
    }

    private class FakeHistoryRepository : HistoryRepository {
        var savedProfile: Profile? = null
        var lastProfileUpdate: Triple<Long, Profile, PaletteScore>? = null

        override fun observeEntries() = throw UnsupportedOperationException("not used by this test")
        override fun observeEntry(entryId: Long) = throw UnsupportedOperationException("not used by this test")

        override suspend fun saveClothingResult(
            thumbnailPath: String,
            calibratedArgb: Int,
            profile: Profile?,
            score: PaletteScore,
        ): HistoryEntry {
            savedProfile = profile
            return HistoryEntry(
                id = 1L,
                type = HistoryEntryType.CLOTHING,
                name = "Clothing",
                createdAt = 0L,
                thumbnailPath = thumbnailPath,
                calibratedArgb = calibratedArgb,
                profileId = profile?.id,
                profileName = profile?.name,
                bestColorsArgb = profile?.bestColors.orEmpty().map { it.argb },
                avoidColorsArgb = profile?.avoidColors.orEmpty().map { it.argb },
                score = score,
            )
        }

        override suspend fun saveObjectResult(
            thumbnailPath: String,
            inputColorsArgb: List<Int>,
            wheel: HarmonyWheel,
            balance: HarmonyBalance,
        ): HistoryEntry = throw UnsupportedOperationException("not used by this test")

        override suspend fun renameEntry(entryId: Long, name: String) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun updateObjectPick(entryId: Long, argb: Int, sampleX: Double?, sampleY: Double?) {
            throw UnsupportedOperationException("not used by this test")
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
            lastProfileUpdate = Triple(entryId, profile, score)
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
}
