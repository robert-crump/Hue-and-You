package com.example.hueandyou.data.profile

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomProfileRepositoryTest {

    private lateinit var dao: FakeProfileDao
    private lateinit var repository: ProfileRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        dao = FakeProfileDao()
        repository = RoomProfileRepository(dao) { clock }
    }

    @Test
    fun createProfile_appearsInObserveProfiles() = runBlocking {
        val id = repository.createProfile("Autumn")

        val profiles = repository.observeProfiles().first()

        assertEquals(1, profiles.size)
        assertEquals(id, profiles[0].id)
        assertEquals("Autumn", profiles[0].name)
        assertEquals(clock, profiles[0].createdAt)
        assertEquals(clock, profiles[0].updatedAt)
        assertTrue(profiles[0].bestColors.isEmpty())
        assertTrue(profiles[0].avoidColors.isEmpty())
    }

    @Test
    fun renameProfile_updatesNameAndTimestamp() = runBlocking {
        val id = repository.createProfile("Autumn")
        clock = 2_000L

        repository.renameProfile(id, "Deep Autumn")

        val profile = repository.observeProfile(id).first()
        assertEquals("Deep Autumn", profile?.name)
        assertEquals(2_000L, profile?.updatedAt)
    }

    @Test
    fun addColor_splitsByKindAndPreservesInsertionOrder() = runBlocking {
        val id = repository.createProfile("Autumn")

        repository.addColor(id, ColorKind.BEST, 0xFF112233.toInt())
        repository.addColor(id, ColorKind.BEST, 0xFF445566.toInt())
        repository.addColor(id, ColorKind.AVOID, 0xFF778899.toInt())

        val profile = repository.observeProfile(id).first()!!
        assertEquals(listOf(0xFF112233.toInt(), 0xFF445566.toInt()), profile.bestColors.map { it.argb })
        assertEquals(listOf(0xFF778899.toInt()), profile.avoidColors.map { it.argb })
    }

    @Test
    fun removeColor_deletesOnlyThatColor() = runBlocking {
        val id = repository.createProfile("Autumn")
        val keepId = repository.addColor(id, ColorKind.BEST, 0xFF112233.toInt())
        val removeId = repository.addColor(id, ColorKind.BEST, 0xFF445566.toInt())

        repository.removeColor(removeId)

        val profile = repository.observeProfile(id).first()!!
        assertEquals(listOf(keepId), profile.bestColors.map { it.id })
    }

    @Test
    fun moveColor_down_swapsWithNextInSameKind() = runBlocking {
        val id = repository.createProfile("Autumn")
        val first = repository.addColor(id, ColorKind.BEST, 0xFF111111.toInt())
        val second = repository.addColor(id, ColorKind.BEST, 0xFF222222.toInt())

        repository.moveColor(first, MoveDirection.DOWN)

        val profile = repository.observeProfile(id).first()!!
        assertEquals(listOf(second, first), profile.bestColors.map { it.id })
    }

    @Test
    fun moveColor_up_swapsWithPreviousInSameKind() = runBlocking {
        val id = repository.createProfile("Autumn")
        val first = repository.addColor(id, ColorKind.BEST, 0xFF111111.toInt())
        val second = repository.addColor(id, ColorKind.BEST, 0xFF222222.toInt())

        repository.moveColor(second, MoveDirection.UP)

        val profile = repository.observeProfile(id).first()!!
        assertEquals(listOf(second, first), profile.bestColors.map { it.id })
    }

    @Test
    fun moveColor_atTopBoundary_isNoOp() = runBlocking {
        val id = repository.createProfile("Autumn")
        val first = repository.addColor(id, ColorKind.BEST, 0xFF111111.toInt())
        val second = repository.addColor(id, ColorKind.BEST, 0xFF222222.toInt())

        repository.moveColor(first, MoveDirection.UP)

        val profile = repository.observeProfile(id).first()!!
        assertEquals(listOf(first, second), profile.bestColors.map { it.id })
    }

    @Test
    fun moveColor_atBottomBoundary_isNoOp() = runBlocking {
        val id = repository.createProfile("Autumn")
        val first = repository.addColor(id, ColorKind.BEST, 0xFF111111.toInt())
        val second = repository.addColor(id, ColorKind.BEST, 0xFF222222.toInt())

        repository.moveColor(second, MoveDirection.DOWN)

        val profile = repository.observeProfile(id).first()!!
        assertEquals(listOf(first, second), profile.bestColors.map { it.id })
    }

    @Test
    fun moveColor_doesNotAffectOtherKind() = runBlocking {
        val id = repository.createProfile("Autumn")
        val best = repository.addColor(id, ColorKind.BEST, 0xFF111111.toInt())
        val avoid = repository.addColor(id, ColorKind.AVOID, 0xFF222222.toInt())

        repository.moveColor(avoid, MoveDirection.UP)

        val profile = repository.observeProfile(id).first()!!
        assertEquals(listOf(best), profile.bestColors.map { it.id })
        assertEquals(listOf(avoid), profile.avoidColors.map { it.id })
    }

    @Test
    fun deleteProfile_cascadesToItsColors() = runBlocking {
        val id = repository.createProfile("Autumn")
        repository.addColor(id, ColorKind.BEST, 0xFF112233.toInt())

        repository.deleteProfile(id)

        assertNull(repository.observeProfile(id).first())
        assertTrue(repository.observeProfiles().first().isEmpty())
        assertEquals(0, dao.colorCount())
    }
}
