package com.example.hueandyou.data.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ProfileRepository] for ViewModel tests; only what those tests need is implemented. */
class FakeProfileRepository : ProfileRepository {
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

    override suspend fun createProfile(name: String): Long =
        createProfileBlocking(name).also { newProfileId.value = it }

    override val newProfileId = MutableStateFlow<Long?>(null)

    override fun consumeNewProfile(profileId: Long) {
        if (newProfileId.value == profileId) newProfileId.value = null
    }

    override suspend fun renameProfile(profileId: Long, name: String) {
        throw UnsupportedOperationException("not used by these tests")
    }

    override suspend fun deleteProfile(profileId: Long) {
        throw UnsupportedOperationException("not used by these tests")
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
        throw UnsupportedOperationException("not used by these tests")
    }
}
