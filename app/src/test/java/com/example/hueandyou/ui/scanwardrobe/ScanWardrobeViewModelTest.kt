package com.example.hueandyou.ui.scanwardrobe

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.IntArrayPixelSource
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.colorspace.PaletteScorer
import com.example.hueandyou.data.history.ClothingCategory
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.NewClothingResult
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.data.settings.HarmonyDefaults
import com.example.hueandyou.data.settings.SettingsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanWardrobeViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val backgroundDispatcher = StandardTestDispatcher(mainDispatcher.scheduler, "background")

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private val autumn = profile(1L, "Autumn", bestArgb = red, avoidArgb = 0xFF00FF00.toInt())
    private val winter = profile(2L, "Winter", bestArgb = blue, avoidArgb = 0xFFFFFF00.toInt())

    private lateinit var historyRepository: FakeHistoryRepository
    private lateinit var thumbnailStore: FakeThumbnailStore

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        historyRepository = FakeHistoryRepository()
        thumbnailStore = FakeThumbnailStore()
    }

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
        profiles: List<Profile> = listOf(autumn, winter),
        lastUsedClothingProfileId: Long? = 2L,
    ) = ScanWardrobeViewModel(
        category = ClothingCategory.TOP,
        categoryLabel = "Top",
        profileRepository = FakeProfileRepository(profiles),
        historyRepository = historyRepository,
        thumbnailStore = thumbnailStore,
        settingsRepository = FakeSettingsRepository(lastUsedClothingProfileId),
        backgroundDispatcher = backgroundDispatcher,
        pixelSourceOf = { mostlyRedWithBlue() },
    ).also { mainDispatcher.scheduler.runCurrent() }

    /** Every third pixel blue, the rest red: red is the most probable color, blue an alternative. */
    private fun mostlyRedWithBlue(): IntArrayPixelSource {
        val size = 12
        return IntArrayPixelSource(size, size, IntArray(size * size) { if (it % 3 == 0) blue else red })
    }

    private fun ScanWardrobeViewModel.capture() {
        onPhotoDecoded(allocateWithoutConstructor(Bitmap::class.java))
        mainDispatcher.scheduler.runCurrent()
    }

    private val ScanWardrobeViewModel.state get() = uiState.value

    @Test
    fun startsOnTheViewfinderWithAnEmptyBatch() {
        val model = viewModel()

        assertEquals(ScanWardrobeStep.Viewfinder, model.state.step)
        assertEquals(ClothingCategory.TOP, model.state.category)
        assertTrue(model.state.items.isEmpty())
    }

    @Test
    fun capture_addsTheItemAndShowsItsPhotoScreen_withTheMostProbableColorPreselected() {
        val model = viewModel()

        model.capture()

        val item = model.state.items.single()
        assertEquals(ScanWardrobeStep.Photo(item.key, fromReview = false), model.state.step)
        assertEquals(item, model.state.currentItem)
        assertEquals(item.chipColorsArgb.first(), item.argb)
        assertEquals("Top 1", item.name)
        assertTrue(historyRepository.saved.isEmpty())
    }

    @Test
    fun pickColor_switchesToAnotherChip_andIgnoresColorsThatAreNotChips() {
        val model = viewModel()
        model.capture()
        val chips = model.state.items.single().chipColorsArgb
        assertTrue("expected an alternative chip", chips.size >= 2)

        model.pickColor(chips[1])
        assertEquals(chips[1], model.state.items.single().argb)

        model.pickColor(0xFF123456.toInt())
        assertEquals(chips[1], model.state.items.single().argb)
    }

    @Test
    fun nextItem_keepsTheItemAndReturnsToTheViewfinder_counterGrows() {
        val model = viewModel()
        model.capture()

        model.nextItem()
        assertEquals(ScanWardrobeStep.Viewfinder, model.state.step)
        model.capture()

        assertEquals(listOf("Top 1", "Top 2"), model.state.items.map { it.name })
    }

    @Test
    fun retake_discardsTheItemAndGivesItsNumberBack() {
        val model = viewModel()
        model.capture()
        model.nextItem()
        model.capture()

        model.retake()
        assertEquals(ScanWardrobeStep.Viewfinder, model.state.step)
        assertEquals(listOf("Top 1"), model.state.items.map { it.name })

        model.capture()
        assertEquals(listOf("Top 1", "Top 2"), model.state.items.map { it.name })
    }

    @Test
    fun done_keepsTheItemAndShowsTheReview() {
        val model = viewModel()
        model.capture()

        model.showReview()

        assertEquals(ScanWardrobeStep.Review, model.state.step)
        assertEquals(1, model.state.items.size)
    }

    @Test
    fun reopen_showsThatItemsPhotoScreenFromTheReview() {
        val model = viewModel()
        model.capture()
        model.nextItem()
        model.capture()
        model.showReview()
        val first = model.state.items.first()

        model.reopen(first.key)

        assertEquals(ScanWardrobeStep.Photo(first.key, fromReview = true), model.state.step)
        assertEquals(first, model.state.currentItem)
    }

    @Test
    fun continueScanning_returnsToTheViewfinderWithTheBatchIntact() {
        val model = viewModel()
        model.capture()
        model.showReview()

        model.continueScanning()
        model.capture()

        assertEquals(listOf("Top 1", "Top 2"), model.state.items.map { it.name })
    }

    @Test
    fun renameAndRemove_editTheBatch_duplicatesAllowed() {
        val model = viewModel()
        model.capture()
        model.nextItem()
        model.capture()
        model.nextItem()
        model.capture()
        model.showReview()
        val (first, second, third) = model.state.items

        model.rename(first.key, "Tee")
        model.rename(third.key, "Tee")
        model.remove(second.key)

        assertEquals(listOf("Tee", "Tee"), model.state.items.map { it.name })
        assertEquals(listOf(first.key, third.key), model.state.items.map { it.key })
    }

    @Test
    fun save_writesTheWholeBatchOnce_ownedInTheCategory_scoredAgainstTheLastUsedProfile() {
        val model = viewModel(lastUsedClothingProfileId = 2L)
        model.capture()
        model.nextItem()
        model.capture()
        model.showReview()
        val items = model.state.items
        model.rename(items[0].key, "  Striped tee ")
        model.rename(items[1].key, " ")

        model.save()
        mainDispatcher.scheduler.runCurrent()

        val batch = historyRepository.saved.single()
        assertEquals(listOf("Striped tee", "Top 2"), batch.map { it.name })
        batch.forEachIndexed { index, result ->
            assertEquals(true, result.inWardrobe)
            assertEquals(ClothingCategory.TOP, result.category)
            assertEquals(winter, result.profile)
            assertEquals(items[index].argb, result.calibratedArgb)
            assertEquals(items[index].chipColorsArgb, result.chipColorsArgb)
            assertEquals(
                PaletteScorer.score(items[index].argb, winter.bestColors.map { it.argb }, winter.avoidColors.map { it.argb }),
                result.score,
            )
        }
        assertEquals(2, thumbnailStore.saveCount)
        assertEquals(ScanWardrobeStep.Saved, model.state.step)
    }

    @Test
    fun save_withNoProfiles_savesWithoutAVerdict() {
        val model = viewModel(profiles = emptyList(), lastUsedClothingProfileId = null)
        model.capture()
        model.showReview()

        model.save()
        mainDispatcher.scheduler.runCurrent()

        val result = historyRepository.saved.single().single()
        assertNull(result.profile)
        assertEquals(PaletteScore(nearestBest = null, nearestAvoid = null, closerToAvoid = false), result.score)
    }

    @Test
    fun save_outsideTheReviewOrWithAnEmptyBatch_isANoop() {
        val model = viewModel()
        model.save()
        model.capture()
        model.save()
        model.showReview()
        model.remove(model.state.items.single().key)
        model.save()
        mainDispatcher.scheduler.runCurrent()

        assertTrue(historyRepository.saved.isEmpty())
        assertEquals(ScanWardrobeStep.Review, model.state.step)
    }

    @Test
    fun discard_leavingWithoutSaving_persistsNothing() {
        val model = viewModel()
        model.capture()
        model.nextItem()
        model.capture()
        model.showReview()

        // The screen's discard prompt just leaves; the view model is then cleared unsaved.
        mainDispatcher.scheduler.advanceUntilIdle()

        assertTrue(historyRepository.saved.isEmpty())
        assertEquals(0, thumbnailStore.saveCount)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateWithoutConstructor(type: Class<T>): T {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }

    private class FakeProfileRepository(private val profiles: List<Profile>) : ProfileRepository {
        override val newProfileId = MutableStateFlow<Long?>(null)
        override fun consumeNewProfile(profileId: Long) = Unit
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

        override suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long? =
            throw UnsupportedOperationException("not used by this test")

        override suspend fun removeColors(colorIds: Collection<Long>) {
            throw UnsupportedOperationException("not used by this test")
        }
    }

    private class FakeSettingsRepository(private val lastUsedClothingProfileId: Long?) : SettingsRepository {
        override fun observeDefaults(): Flow<HarmonyDefaults> = flowOf(HarmonyDefaults())

        override suspend fun setDefaultWheel(wheel: HarmonyWheel) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun setDefaultBalance(balance: HarmonyBalance) {
            throw UnsupportedOperationException("not used by this test")
        }

        override fun observeLastUsedClothingProfileId(): Flow<Long?> = flowOf(lastUsedClothingProfileId)

        override suspend fun setLastUsedClothingProfileId(profileId: Long) {
            throw UnsupportedOperationException("not used by this test")
        }
    }

    private class FakeHistoryRepository : HistoryRepository {
        /** One list per [saveClothingResults] call. */
        val saved = mutableListOf<List<NewClothingResult>>()

        override suspend fun saveClothingResults(results: List<NewClothingResult>) {
            saved += results
        }

        override fun observeEntries() = throw UnsupportedOperationException("not used by this test")
        override fun observeEntry(entryId: Long) = throw UnsupportedOperationException("not used by this test")

        override suspend fun saveClothingResult(
            thumbnailPath: String,
            calibratedArgb: Int,
            profile: Profile?,
            score: PaletteScore,
            chipColorsArgb: List<Int>,
            name: String,
            inWardrobe: Boolean,
            category: ClothingCategory?,
        ): HistoryEntry = throw UnsupportedOperationException("not used by this test")

        override suspend fun saveObjectResult(
            thumbnailPath: String,
            inputColorsArgb: List<Int>,
            wheel: HarmonyWheel,
            balance: HarmonyBalance,
            chipColorsArgb: List<Int>,
            name: String,
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
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun updateChipColors(entryId: Long, chipColorsArgb: List<Int>) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun updateWardrobeDetails(entryId: Long, inWardrobe: Boolean, category: ClothingCategory?) {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun deleteEntry(entryId: Long) {
            throw UnsupportedOperationException("not used by this test")
        }
    }

    private class FakeThumbnailStore : ThumbnailStore {
        var saveCount = 0

        override suspend fun save(bitmap: Bitmap): String = "thumb${++saveCount}.jpg"
        override suspend fun delete(path: String) = throw UnsupportedOperationException("not used by this test")
        override suspend fun readBytes(path: String): ByteArray = throw UnsupportedOperationException("not used by this test")
        override suspend fun writeBytes(bytes: ByteArray): String = throw UnsupportedOperationException("not used by this test")
    }
}
