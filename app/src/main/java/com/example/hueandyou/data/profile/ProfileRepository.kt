package com.example.hueandyou.data.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>
    fun observeProfile(profileId: Long): Flow<Profile?>
    suspend fun createProfile(name: String): Long

    /**
     * The profile [createProfile] made most recently, until [consumeNewProfile] - so the Settings
     * list can point it out once, whichever flow created it. In memory only.
     */
    val newProfileId: StateFlow<Long?>

    /** Clears [newProfileId] if it is still [profileId]. */
    fun consumeNewProfile(profileId: Long)

    suspend fun renameProfile(profileId: Long, name: String)
    suspend fun deleteProfile(profileId: Long)

    /**
     * Appends a color to the end of [kind]'s list. Returns the new color's id, or null if that
     * list already holds [MAX_COLORS_PER_KIND] colors.
     */
    suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long?

    /** Removes all of [colorIds] in one statement. */
    suspend fun removeColors(colorIds: Collection<Long>)
}

class RoomProfileRepository(
    private val dao: ProfileDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : ProfileRepository {

    override fun observeProfiles(): Flow<List<Profile>> =
        dao.observeProfilesWithColors().map { list -> list.map { it.toDomain() } }

    override fun observeProfile(profileId: Long): Flow<Profile?> =
        dao.observeProfileWithColors(profileId).map { it?.toDomain() }

    private val _newProfileId = MutableStateFlow<Long?>(null)
    override val newProfileId: StateFlow<Long?> = _newProfileId.asStateFlow()

    override suspend fun createProfile(name: String): Long {
        val now = currentTimeMillis()
        return dao.insertProfile(ProfileEntity(name = name, createdAt = now, updatedAt = now))
            .also { _newProfileId.value = it }
    }

    override fun consumeNewProfile(profileId: Long) {
        _newProfileId.update { if (it == profileId) null else it }
    }

    override suspend fun renameProfile(profileId: Long, name: String) {
        val existing = dao.getProfile(profileId) ?: return
        dao.updateProfile(existing.copy(name = name, updatedAt = currentTimeMillis()))
    }

    override suspend fun deleteProfile(profileId: Long) {
        dao.deleteProfile(profileId)
    }

    override suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long? {
        if (dao.countColors(profileId, kind) >= MAX_COLORS_PER_KIND) return null
        val position = dao.nextPosition(profileId, kind)
        val id = dao.insertColor(
            PaletteColorEntity(profileId = profileId, kind = kind, argb = argb, position = position)
        )
        dao.getProfile(profileId)?.let { dao.updateProfile(it.copy(updatedAt = currentTimeMillis())) }
        return id
    }

    override suspend fun removeColors(colorIds: Collection<Long>) {
        if (colorIds.isEmpty()) return
        dao.deleteColors(colorIds.toList())
    }
}
