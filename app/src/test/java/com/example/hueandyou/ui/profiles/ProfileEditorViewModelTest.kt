package com.example.hueandyou.ui.profiles

import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.data.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val PROFILE_ID = 1L

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
    fun addColor_persists() {
        viewModel.addColor(ColorKind.BEST, 0xFF3A7DFF.toInt())

        assertEquals(listOf(PROFILE_ID to ColorKind.BEST), repository.addedColors)
    }

    @Test
    fun stageRemoval_outsideEditMode_isIgnored() {
        viewModel.stageRemoval(7L)

        assertEquals(emptySet<Long>(), viewModel.pendingRemovals.value)
    }

    @Test
    fun stageRemoval_inEditMode_stagesWithoutPersisting() {
        viewModel.startEditing()

        viewModel.stageRemoval(7L)
        viewModel.stageRemoval(8L)

        assertTrue(viewModel.isEditing.value)
        assertEquals(setOf(7L, 8L), viewModel.pendingRemovals.value)
        assertTrue(repository.removedBatches.isEmpty())
    }

    @Test
    fun cancelEditing_discardsStagedRemovals() {
        viewModel.startEditing()
        viewModel.stageRemoval(7L)

        viewModel.cancelEditing()

        assertFalse(viewModel.isEditing.value)
        assertEquals(emptySet<Long>(), viewModel.pendingRemovals.value)
        assertTrue(repository.removedBatches.isEmpty())
    }

    @Test
    fun commitEditing_removesStagedColorsInOneBatch() {
        viewModel.startEditing()
        viewModel.stageRemoval(7L)
        viewModel.stageRemoval(8L)

        viewModel.commitEditing()

        assertFalse(viewModel.isEditing.value)
        assertEquals(emptySet<Long>(), viewModel.pendingRemovals.value)
        assertEquals(listOf(setOf(7L, 8L)), repository.removedBatches)
    }

    @Test
    fun commitEditing_withNothingStaged_justExits() {
        viewModel.startEditing()

        viewModel.commitEditing()

        assertFalse(viewModel.isEditing.value)
        assertTrue(repository.removedBatches.isEmpty())
    }

    @Test
    fun startEditing_seedsDraftWithCurrentName() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.profile.collect {} }

        viewModel.startEditing()

        assertEquals("Autumn", viewModel.draftName.value)
    }

    @Test
    fun updateDraftName_doesNotPersistUntilCommit() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.profile.collect {} }
        viewModel.startEditing()

        viewModel.updateDraftName("Winter")

        assertEquals("Autumn", viewModel.profile.value?.name)
        assertTrue(repository.renames.isEmpty())
    }

    @Test
    fun commitEditing_savesTrimmedName() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.profile.collect {} }
        viewModel.startEditing()
        viewModel.updateDraftName("  Winter ")

        viewModel.commitEditing()

        assertEquals(listOf("Winter"), repository.renames)
    }

    @Test
    fun commitEditing_withBlankOrUnchangedName_doesNotRename() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.profile.collect {} }
        viewModel.startEditing()
        viewModel.commitEditing()
        viewModel.startEditing()
        viewModel.updateDraftName("   ")
        viewModel.commitEditing()

        assertTrue(repository.renames.isEmpty())
    }

    @Test
    fun cancelEditing_discardsDraftName() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.profile.collect {} }
        viewModel.startEditing()
        viewModel.updateDraftName("Winter")

        viewModel.cancelEditing()

        assertTrue(repository.renames.isEmpty())
        assertEquals("Autumn", viewModel.profile.value?.name)
    }

    @Test
    fun draftName_matchingAnotherProfileIgnoringCase_isTakenAndNotSaved() = runTest(UnconfinedTestDispatcher()) {
        repository.otherProfiles.value = listOf(profileNamed(2L, "Winter"))
        backgroundScope.launch { viewModel.profile.collect {} }
        backgroundScope.launch { viewModel.isDraftNameTaken.collect {} }
        viewModel.startEditing()

        viewModel.updateDraftName(" winter ")
        assertTrue(viewModel.isDraftNameTaken.value)
        viewModel.commitEditing()

        assertTrue(repository.renames.isEmpty())
    }

    @Test
    fun draftName_ownNameInAnotherCase_isNotTaken() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.profile.collect {} }
        backgroundScope.launch { viewModel.isDraftNameTaken.collect {} }
        viewModel.startEditing()

        viewModel.updateDraftName("AUTUMN")
        assertFalse(viewModel.isDraftNameTaken.value)
        viewModel.commitEditing()

        assertEquals(listOf("AUTUMN"), repository.renames)
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
    override val newProfileId = MutableStateFlow<Long?>(null)
    override fun consumeNewProfile(profileId: Long) = Unit
    val addedColors = mutableListOf<Pair<Long, ColorKind>>()
    val removedBatches = mutableListOf<Set<Long>>()
    val renames = mutableListOf<String>()
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

    /** Profiles besides the edited one, for the duplicate-name check. */
    val otherProfiles = MutableStateFlow<List<Profile>>(emptyList())

    override fun observeProfiles(): Flow<List<Profile>> =
        combine(profiles, otherProfiles) { profile, others -> listOfNotNull(profile) + others }

    override fun observeProfile(profileId: Long): Flow<Profile?> = profiles

    override suspend fun createProfile(name: String): Long =
        throw UnsupportedOperationException("not used by this test")

    override suspend fun renameProfile(profileId: Long, name: String) {
        renames += name
        profiles.value = profiles.value?.copy(name = name)
    }

    override suspend fun deleteProfile(profileId: Long) {
        deletedProfileId = profileId
    }

    override suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long? {
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

    override suspend fun removeColors(colorIds: Collection<Long>) {
        removedBatches += colorIds.toSet()
    }
}

private fun profileNamed(id: Long, name: String) = Profile(
    id = id,
    name = name,
    createdAt = 0L,
    updatedAt = 0L,
    bestColors = emptyList(),
    avoidColors = emptyList()
)
