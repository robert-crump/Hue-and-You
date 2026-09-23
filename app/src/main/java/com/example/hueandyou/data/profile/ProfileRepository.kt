package com.example.hueandyou.data.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>
    fun observeProfile(profileId: Long): Flow<Profile?>
    suspend fun createProfile(name: String): Long
    suspend fun renameProfile(profileId: Long, name: String)
    suspend fun deleteProfile(profileId: Long)
    suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long
    suspend fun removeColor(colorId: Long)
    suspend fun moveColor(colorId: Long, direction: MoveDirection)
}

class RoomProfileRepository(
    private val dao: ProfileDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : ProfileRepository {

    override fun observeProfiles(): Flow<List<Profile>> =
        dao.observeProfilesWithColors().map { list -> list.map { it.toDomain() } }

    override fun observeProfile(profileId: Long): Flow<Profile?> =
        dao.observeProfileWithColors(profileId).map { it?.toDomain() }

    override suspend fun createProfile(name: String): Long {
        val now = currentTimeMillis()
        return dao.insertProfile(ProfileEntity(name = name, createdAt = now, updatedAt = now))
    }

    override suspend fun renameProfile(profileId: Long, name: String) {
        val existing = dao.getProfile(profileId) ?: return
        dao.updateProfile(existing.copy(name = name, updatedAt = currentTimeMillis()))
    }

    override suspend fun deleteProfile(profileId: Long) {
        dao.deleteProfile(profileId)
    }

    override suspend fun addColor(profileId: Long, kind: ColorKind, argb: Int): Long {
        val position = dao.nextPosition(profileId, kind)
        val id = dao.insertColor(
            PaletteColorEntity(profileId = profileId, kind = kind, argb = argb, position = position)
        )
        dao.getProfile(profileId)?.let { dao.updateProfile(it.copy(updatedAt = currentTimeMillis())) }
        return id
    }

    override suspend fun removeColor(colorId: Long) {
        dao.deleteColor(colorId)
    }

    override suspend fun moveColor(colorId: Long, direction: MoveDirection) {
        val color = dao.getColor(colorId) ?: return
        val siblings = dao.getColorsForKind(color.profileId, color.kind)
        val index = siblings.indexOfFirst { it.id == colorId }
        if (index < 0) return
        val targetIndex = if (direction == MoveDirection.UP) index - 1 else index + 1
        if (targetIndex < 0 || targetIndex >= siblings.size) return

        val target = siblings[targetIndex]
        dao.updateColor(color.copy(position = target.position))
        dao.updateColor(target.copy(position = color.position))
        dao.getProfile(color.profileId)?.let { dao.updateProfile(it.copy(updatedAt = currentTimeMillis())) }
    }
}
