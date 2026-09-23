package com.example.hueandyou.ui.profiles

import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.MoveDirection
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val PROFILE_ID = 1L
private const val INVALID_HEX_MESSAGE = "Enter a valid hex code"

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorViewModelTest {

    private lateinit var repository: FakeProfileRepository
    private lateinit var viewModel: ProfileEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeProfileRepository()
        viewModel = ProfileEditorViewModel(repository, PROFILE_ID)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addColor_withInvalidHex_returnsErrorAndDoesNotPersist() {
        val error = viewModel.addColor(ColorKind.BEST, "not-a-hex", INVALID_HEX_MESSAGE)

        assertEquals(INVALID_HEX_MESSAGE, error)
        assertEquals(0, repository.addedColors.size)
    }

    @Test
    fun addColor_withValidHex_persistsAndReturnsNull() {
        val error = viewModel.addColor(ColorKind.BEST, "#3A7DFF", INVALID_HEX_MESSAGE)

        assertNull(error)
        assertEquals(1, repository.addedColors.size)
        assertEquals(ColorKind.BEST, repository.addedColors[0].second)
    }

    @Test
    fun moveColor_delegatesToRepository() {
        viewModel.moveColor(colorId = 7L, direction = MoveDirection.DOWN)

        assertEquals(listOf(7L to MoveDirection.DOWN), repository.movedColors)
    }

    @Test
    fun deleteProfile_removesFromRepositoryAndInvokesCallback() {
        var deletedCalled = false

        viewModel.deleteProfile { deletedCalled = true }

        assertEquals(PROFILE_ID, repository.deletedProfileId)
        assertEquals(true, deletedCalled)
    }
}

private class FakeProfileRepository : ProfileRepository {
    val addedColors = mutableListOf<Pair<Long, ColorKind>>()
    val movedColors = mutableListOf<Pair<Long, MoveDirection>>()
    var deletedProfileId: Long? = null
    private val profiles = MutableStateFlow<Profile?>(
        Profile(
            id = PROFILE_ID,
            name = "Autumn",
            createdAt = 0L,
            updatedAt = 0L,
            bestColors = emptyList(),
            avoidColors = emptyList()
        )
    )

    override fun observeProfiles(): Flow<List<Profile>> =
        throw UnsupportedOperationException("not used by this test")

    override fun observeProfile(profileId: Long): Flow<Profile?> = profiles

    override suspend fun createProfile(name: String): Long =
        throw UnsupportedOperationException("not used by this test")

    override suspend fun renameProfile(profileId: Long, name: String) {
        profiles.value = profiles.value?.copy(name = name)
    }

    override suspend fun deleteProfile(profileId: Long) {
        deletedProfileId = profileId
    }

    override suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long {
        addedColors += profileId to kind
        val color = PaletteColor(id = addedColors.size.toLong(), kind = kind, argb = argb)
        val current = profiles.value ?: return color.id
        profiles.value = if (kind == ColorKind.BEST) {
            current.copy(bestColors = current.bestColors + color)
        } else {
            current.copy(avoidColors = current.avoidColors + color)
        }
        return color.id
    }

    override suspend fun removeColor(colorId: Long) {
        val current = profiles.value ?: return
        profiles.value = current.copy(
            bestColors = current.bestColors.filterNot { it.id == colorId },
            avoidColors = current.avoidColors.filterNot { it.id == colorId }
        )
    }

    override suspend fun moveColor(colorId: Long, direction: MoveDirection) {
        movedColors += colorId to direction
    }
}
