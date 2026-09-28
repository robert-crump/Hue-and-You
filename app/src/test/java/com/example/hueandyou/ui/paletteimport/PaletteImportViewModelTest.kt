package com.example.hueandyou.ui.paletteimport

import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.MAX_COLORS_PER_KIND
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val NEW_PROFILE_NAME = "My palette"
private const val RED = 0xFFFF0000.toInt()
private const val GREEN = 0xFF00FF00.toInt()
private const val BLUE = 0xFF0000FF.toInt()

@OptIn(ExperimentalCoroutinesApi::class)
class PaletteImportViewModelTest {

    private lateinit var repository: FakeProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeProfileRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun swatch(id: Int, argb: Int) = ImportSwatch(id = id, argb = argb, share = 0.5)

    @Test
    fun newProfileMode_reviewUsesFullSlots() {
        val viewModel = PaletteImportViewModel(repository, profileId = null, newProfileName = NEW_PROFILE_NAME)

        viewModel.startReview(listOf(swatch(0, RED)), emptyList())

        val state = viewModel.uiState.value as PaletteImportUiState.Reviewing
        assertEquals(MAX_COLORS_PER_KIND, state.bestSlots)
        assertEquals(MAX_COLORS_PER_KIND, state.avoidSlots)
    }

    @Test
    fun newProfileMode_withoutConfirm_createsNothing() {
        val viewModel = PaletteImportViewModel(repository, profileId = null, newProfileName = NEW_PROFILE_NAME)

        viewModel.startReview(listOf(swatch(0, RED)), listOf(swatch(1, BLUE)))

        assertTrue(repository.profiles.value.isEmpty())
    }

    @Test
    fun newProfileMode_confirm_createsOneProfileWithSelectedColors() {
        val viewModel = PaletteImportViewModel(repository, profileId = null, newProfileName = NEW_PROFILE_NAME)
        viewModel.startReview(listOf(swatch(0, RED), swatch(1, GREEN)), listOf(swatch(2, BLUE)))
        viewModel.toggleSwatch(ColorKind.BEST, swatchId = 1)

        viewModel.confirmImport()

        val profile = repository.profiles.value.single()
        assertEquals(NEW_PROFILE_NAME, profile.name)
        assertEquals(listOf(RED), profile.bestColors.map { it.argb })
        assertEquals(listOf(BLUE), profile.avoidColors.map { it.argb })
        assertEquals(PaletteImportUiState.Done(profile.id), viewModel.uiState.value)
    }

    @Test
    fun newProfileMode_confirmTwice_createsOneProfile() {
        val viewModel = PaletteImportViewModel(repository, profileId = null, newProfileName = NEW_PROFILE_NAME)
        viewModel.startReview(listOf(swatch(0, RED)), emptyList())

        viewModel.confirmImport()
        viewModel.confirmImport()

        assertEquals(1, repository.profiles.value.size)
    }

    @Test
    fun existingProfileMode_confirm_addsToThatProfile() {
        val existingId = repository.createProfileBlocking("Autumn")
        val viewModel = PaletteImportViewModel(repository, profileId = existingId)
        viewModel.startReview(listOf(swatch(0, RED)), emptyList())

        viewModel.confirmImport()

        val profile = repository.profiles.value.single()
        assertEquals("Autumn", profile.name)
        assertEquals(listOf(RED), profile.bestColors.map { it.argb })
        assertEquals(PaletteImportUiState.Done(existingId), viewModel.uiState.value)
    }
}

private class FakeProfileRepository : ProfileRepository {
    val profiles = MutableStateFlow<List<Profile>>(emptyList())
    private var nextId = 1L

    fun createProfileBlocking(name: String): Long {
        val id = nextId++
        profiles.value += Profile(
            id = id,
            name = name,
            createdAt = 0L,
            updatedAt = 0L,
            bestColors = emptyList(),
            avoidColors = emptyList()
        )
        return id
    }

    override fun observeProfiles(): Flow<List<Profile>> = profiles

    override fun observeProfile(profileId: Long): Flow<Profile?> =
        profiles.map { list -> list.find { it.id == profileId } }

    override suspend fun createProfile(name: String): Long = createProfileBlocking(name)

    override suspend fun renameProfile(profileId: Long, name: String) {
        throw UnsupportedOperationException("not used by this test")
    }

    override suspend fun deleteProfile(profileId: Long) {
        throw UnsupportedOperationException("not used by this test")
    }

    override suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long? {
        val color = PaletteColor(id = nextId++, kind = kind, argb = argb)
        profiles.value = profiles.value.map { profile ->
            when {
                profile.id != profileId -> profile
                kind == ColorKind.BEST -> profile.copy(bestColors = profile.bestColors + color)
                else -> profile.copy(avoidColors = profile.avoidColors + color)
            }
        }
        return color.id
    }

    override suspend fun removeColors(colorIds: Collection<Long>) {
        throw UnsupportedOperationException("not used by this test")
    }
}
