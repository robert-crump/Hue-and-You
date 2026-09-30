package com.example.hueandyou.ui.outfits

import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.OutfitHarmony
import com.example.hueandyou.data.history.ClothingCategory
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.outfit.FakeOutfitRepository
import com.example.hueandyou.data.outfit.Outfit
import com.example.hueandyou.data.profile.FakeProfileRepository
import com.example.hueandyou.data.profile.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutfitEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val autumn = profile(1L, "Autumn", best = RED, avoid = GREEN)
    private val winter = profile(2L, "Winter", best = BLUE, avoid = RED)

    private lateinit var history: FakeHistoryRepository
    private lateinit var outfits: FakeOutfitRepository
    private lateinit var settings: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        history = FakeHistoryRepository(
            clothing(1L, RED, "Red top", category = ClothingCategory.TOP),
            clothing(2L, GREEN, "Green skirt", category = ClothingCategory.BOTTOM),
            clothing(3L, BLUE, "Blue coat", category = ClothingCategory.OUTERWEAR),
            clothing(4L, RED, "Red shoes", inWardrobe = false, category = ClothingCategory.SHOES),
            clothing(5L, BLUE, "Vase", type = HistoryEntryType.OBJECT),
            *(6L..12L).map { clothing(it, RED) }.toTypedArray(),
        )
        outfits = FakeOutfitRepository(Outfit(20L, "Work", 0L, listOf(3L, 1L)))
        settings = FakeSettingsRepository(lastUsedClothingProfileId = 2L)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        outfitId: Long? = null,
        initialEntryIds: List<Long> = emptyList(),
        profiles: List<Profile> = listOf(autumn, winter),
    ) = OutfitEditorViewModel(
        outfitId = outfitId,
        initialEntryIds = initialEntryIds,
        defaultNamePrefix = "Outfit",
        outfitRepository = outfits,
        historyRepository = history,
        profileRepository = FakeProfileRepository().apply { this.profiles.value = profiles },
        settingsRepository = settings,
    ).also { dispatcher.scheduler.runCurrent() }

    private val OutfitEditorViewModel.state get() = uiState.value

    private fun OutfitEditorViewModel.act(action: OutfitEditorViewModel.() -> Unit) {
        action()
        dispatcher.scheduler.runCurrent()
    }

    private val OutfitEditorUiState.itemIds get() = items.map { it.id }

    @Test
    fun newOutfit_startsEmpty_withTheNextDefaultNameAndTheLastUsedProfile() {
        val model = viewModel()

        assertFalse(model.state.isLoading)
        assertTrue(model.state.isNew)
        assertNull(model.state.name)
        assertEquals("Outfit 2", model.state.defaultName)
        assertTrue(model.state.items.isEmpty())
        assertEquals(winter, model.state.selectedProfile)
        assertFalse(model.state.canSave)
        assertNull(model.state.rating)
        assertFalse(model.state.hasChanges)
    }

    @Test
    fun makeOutfit_prefillsTheSelectionInOrder_upToEightItems_andCountsAsAChange() {
        assertEquals(listOf(2L, 1L), viewModel(initialEntryIds = listOf(2L, 1L)).state.itemIds)

        val model = viewModel(initialEntryIds = (1L..4L) + (6L..12L))

        assertEquals(OUTFIT_MAX_ITEMS, model.state.items.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 6L, 7L, 8L, 9L), model.state.itemIds)
        assertTrue(model.state.hasChanges)
    }

    @Test
    fun addAndRemove_keepOrder_ignoreDuplicates_andEnforceTwoToEightItems() {
        val model = viewModel()

        model.act { addItem(1L) }
        assertFalse(model.state.canSave)
        assertNull(model.state.rating)

        model.act { addItem(3L); addItem(1L) }
        assertEquals(listOf(1L, 3L), model.state.itemIds)
        assertTrue(model.state.canSave)

        model.act { (6L..12L).forEach { addItem(it) } }
        assertEquals(listOf(1L, 3L, 6L, 7L, 8L, 9L, 10L, 11L), model.state.itemIds)
        assertFalse(model.state.canAddMore)

        model.act { removeItem(6L) }
        assertEquals(listOf(1L, 3L, 7L, 8L, 9L, 10L, 11L), model.state.itemIds)
        assertTrue(model.state.canAddMore)
    }

    @Test
    fun toggleItem_addsOrRemoves() {
        val model = viewModel()

        model.act { toggleItem(1L); toggleItem(2L); toggleItem(1L) }

        assertEquals(listOf(2L), model.state.itemIds)
    }

    @Test
    fun moveItem_shiftsItAlongTheStrip_clampedToTheEnds() {
        val model = viewModel(initialEntryIds = listOf(1L, 2L, 3L))

        model.act { moveItem(3L, -1) }
        assertEquals(listOf(1L, 3L, 2L), model.state.itemIds)

        model.act { moveItem(1L, 1) }
        assertEquals(listOf(3L, 1L, 2L), model.state.itemIds)

        model.act { moveItem(3L, -1); moveItem(2L, 1) }
        assertEquals(listOf(3L, 1L, 2L), model.state.itemIds)
    }

    @Test
    fun rating_isLive_withAvoidItemsAndTheWheelFromSettings() {
        settings.defaults.value = settings.defaults.value.copy(wheel = HarmonyWheel.TRADITIONAL)
        val model = viewModel(initialEntryIds = listOf(1L, 2L, 3L))

        // Winter: blue is Best, red is Avoid.
        assertEquals(SuitsYouSummary(itemCount = 3, bestCount = 1, avoidIndices = listOf(0)), model.state.rating?.suitsYou)
        assertEquals(
            OutfitHarmony.classify(listOf(RED, GREEN, BLUE), HarmonyWheel.TRADITIONAL),
            model.state.rating?.harmony,
        )

        model.act { removeItem(2L) }
        assertEquals(OutfitHarmony.classify(listOf(RED, BLUE), HarmonyWheel.TRADITIONAL), model.state.rating?.harmony)
    }

    @Test
    fun switchProfile_reScoresAndRemembersIt() {
        val model = viewModel(initialEntryIds = listOf(1L, 2L, 3L))

        model.act { switchProfile(autumn) }

        assertEquals(autumn, model.state.selectedProfile)
        // Autumn: red is Best, green is Avoid.
        assertEquals(SuitsYouSummary(itemCount = 3, bestCount = 1, avoidIndices = listOf(1)), model.state.rating?.suitsYou)
        assertEquals(1L, settings.lastUsedClothingProfileId.value)
    }

    @Test
    fun withNoProfiles_onlyHarmonyIsRated() {
        val model = viewModel(initialEntryIds = listOf(1L, 3L), profiles = emptyList())

        assertNull(model.state.selectedProfile)
        assertNull(model.state.rating?.suitsYou)
        assertEquals(OutfitHarmony.classify(listOf(RED, BLUE), HarmonyWheel.PERCEPTUAL), model.state.rating?.harmony)
    }

    @Test
    fun picker_startsOnWardrobeItems_andFiltersByCategory_neverShowingObjects() {
        val model = viewModel()

        assertTrue(model.state.picker.wardrobeOnly)
        assertFalse(4L in model.state.picker.items.map { it.id })
        assertFalse(5L in model.state.picker.items.map { it.id })

        model.act { setPickerWardrobeOnly(false) }
        assertTrue(4L in model.state.picker.items.map { it.id })
        assertFalse(5L in model.state.picker.items.map { it.id })

        model.act { setPickerCategory(ClothingCategory.SHOES) }
        assertEquals(listOf(4L), model.state.picker.items.map { it.id })

        model.act { setPickerWardrobeOnly(true) }
        assertTrue(model.state.picker.items.isEmpty())
    }

    @Test
    fun save_newOutfit_createsItWithTheItemsInOrder_thenFinishes() {
        val model = viewModel(initialEntryIds = listOf(3L, 1L, 2L))

        model.act { save("  Date night ") }

        assertTrue(model.state.isFinished)
        val saved = outfits.outfits.value.first()
        assertEquals("Date night", saved.name)
        assertEquals(listOf(3L, 1L, 2L), saved.entryIds)
    }

    @Test
    fun save_withABlankName_usesTheDefault() {
        val model = viewModel(initialEntryIds = listOf(1L, 2L))

        model.act { save(" ") }

        assertEquals("Outfit 2", outfits.outfits.value.first().name)
    }

    @Test
    fun save_withFewerThanTwoItems_doesNothing() {
        val model = viewModel(initialEntryIds = listOf(1L))

        model.act { save("Solo") }

        assertFalse(model.state.isFinished)
        assertEquals(1, outfits.outfits.value.size)
    }

    @Test
    fun existingOutfit_loadsItsNameAndItems_unchangedUntilEdited() {
        val model = viewModel(outfitId = 20L)

        assertFalse(model.state.isNew)
        assertEquals("Work", model.state.name)
        assertEquals("Work", model.state.defaultName)
        assertEquals(listOf(3L, 1L), model.state.itemIds)
        assertFalse(model.state.hasChanges)

        model.act { addItem(2L) }
        assertTrue(model.state.hasChanges)
    }

    @Test
    fun save_existingOutfit_updatesItsNameAndItems() {
        val model = viewModel(outfitId = 20L)

        model.act { moveItem(1L, -1); addItem(2L); save("") }

        assertTrue(model.state.isFinished)
        assertEquals(listOf(Outfit(20L, "Work", 0L, listOf(1L, 3L, 2L))), outfits.outfits.value)

        val renamed = viewModel(outfitId = 20L)
        renamed.act { save("Office") }
        assertEquals("Office", outfits.outfits.value.single().name)
    }

    @Test
    fun delete_removesTheOutfitButNotItsItems() {
        val model = viewModel(outfitId = 20L)

        model.act { delete() }

        assertTrue(model.state.isFinished)
        assertTrue(outfits.outfits.value.isEmpty())
        assertEquals(12, history.entries.value.size)
    }

    @Test
    fun anItemDeletedFromClothes_dropsOutOfTheEditor() {
        val model = viewModel(initialEntryIds = listOf(1L, 2L, 3L))

        history.entries.value = history.entries.value.filterNot { it.id == 2L }
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(1L, 3L), model.state.itemIds)
    }
}
