package com.example.hueandyou.data.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ProfileDao] double so repository logic can be unit tested without a real database. */
class FakeProfileDao : ProfileDao {
    private val profiles = mutableMapOf<Long, ProfileEntity>()
    private val colors = mutableMapOf<Long, PaletteColorEntity>()
    private var nextProfileId = 1L
    private var nextColorId = 1L
    private val profilesFlow = MutableStateFlow<List<ProfileWithColors>>(emptyList())

    private fun emit() {
        profilesFlow.value = profiles.values
            .sortedBy { it.createdAt }
            .map { profile -> ProfileWithColors(profile, colors.values.filter { it.profileId == profile.id }) }
    }

    override fun observeProfilesWithColors(): Flow<List<ProfileWithColors>> = profilesFlow

    override fun observeProfileWithColors(profileId: Long): Flow<ProfileWithColors?> =
        profilesFlow.map { list -> list.find { it.profile.id == profileId } }

    override suspend fun insertProfile(profile: ProfileEntity): Long {
        val id = nextProfileId++
        profiles[id] = profile.copy(id = id)
        emit()
        return id
    }

    override suspend fun updateProfile(profile: ProfileEntity) {
        profiles[profile.id] = profile
        emit()
    }

    override suspend fun getProfile(profileId: Long): ProfileEntity? = profiles[profileId]

    override suspend fun deleteProfile(profileId: Long) {
        profiles.remove(profileId)
        colors.values.filter { it.profileId == profileId }.map { it.id }.forEach { colors.remove(it) }
        emit()
    }

    override suspend fun deleteAllProfiles() {
        profiles.clear()
        colors.clear()
        emit()
    }

    override suspend fun insertColor(color: PaletteColorEntity): Long {
        val id = nextColorId++
        colors[id] = color.copy(id = id)
        emit()
        return id
    }

    override suspend fun insertColors(colors: List<PaletteColorEntity>) {
        colors.forEach { color ->
            val id = nextColorId++
            this.colors[id] = color.copy(id = id)
        }
        emit()
    }

    override suspend fun updateColor(color: PaletteColorEntity) {
        colors[color.id] = color
        emit()
    }

    override suspend fun deleteColor(colorId: Long) {
        colors.remove(colorId)
        emit()
    }

    override suspend fun getColor(colorId: Long): PaletteColorEntity? = colors[colorId]

    override suspend fun getColorsForKind(profileId: Long, kind: ColorKind): List<PaletteColorEntity> =
        colors.values
            .filter { it.profileId == profileId && it.kind == kind }
            .sortedBy { it.position }

    override suspend fun nextPosition(profileId: Long, kind: ColorKind): Int =
        (
            colors.values
                .filter { it.profileId == profileId && it.kind == kind }
                .maxOfOrNull { it.position } ?: -1
            ) + 1

    fun colorCount(): Int = colors.size
}
