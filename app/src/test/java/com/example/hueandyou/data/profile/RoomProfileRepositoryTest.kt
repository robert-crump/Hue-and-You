package com.example.hueandyou.data.profile

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun addColor_whenListIsFull_returnsNullAndDoesNotInsert() = runBlocking {
        val id = repository.createProfile("Autumn")
        repeat(MAX_COLORS_PER_KIND) { repository.addColor(id, ColorKind.BEST, it) }

        val result = repository.addColor(id, ColorKind.BEST, 0xFF112233.toInt())

        assertNull(result)
        assertEquals(MAX_COLORS_PER_KIND, repository.observeProfile(id).first()!!.bestColors.size)
    }

    @Test
    fun addColor_capIsPerKind() = runBlocking {
        val id = repository.createProfile("Autumn")
        repeat(MAX_COLORS_PER_KIND) { repository.addColor(id, ColorKind.BEST, it) }

        val result = repository.addColor(id, ColorKind.AVOID, 0xFF112233.toInt())

        assertNotNull(result)
    }

    @Test
    fun removeColors_deletesOnlyThoseColors() = runBlocking {
        val id = repository.createProfile("Autumn")
        val keepId = repository.addColor(id, ColorKind.BEST, 0xFF112233.toInt())
        val removeBest = repository.addColor(id, ColorKind.BEST, 0xFF445566.toInt())!!
        val removeAvoid = repository.addColor(id, ColorKind.AVOID, 0xFF778899.toInt())!!

        repository.removeColors(listOf(removeBest, removeAvoid))

        val profile = repository.observeProfile(id).first()!!
        assertEquals(listOf(keepId), profile.bestColors.map { it.id })
        assertTrue(profile.avoidColors.isEmpty())
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

    @Test
    fun createProfile_marksItAsNew() = runBlocking {
        repository.createProfile("Autumn")
        val second = repository.createProfile("Winter")

        assertEquals(second, repository.newProfileId.value)
    }

    @Test
    fun consumeNewProfile_clearsOnlyTheCurrentNewProfile() = runBlocking {
        val first = repository.createProfile("Autumn")
        val second = repository.createProfile("Winter")

        repository.consumeNewProfile(first)
        assertEquals(second, repository.newProfileId.value)

        repository.consumeNewProfile(second)
        assertNull(repository.newProfileId.value)
    }
}
