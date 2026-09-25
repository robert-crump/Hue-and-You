package com.example.hueandyou.data.backup

import com.example.hueandyou.data.history.HistoryDao
import com.example.hueandyou.data.history.HistoryEntryEntity
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.history.toDomain
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.PaletteColorEntity
import com.example.hueandyou.data.profile.ProfileDao
import com.example.hueandyou.data.profile.ProfileEntity
import com.example.hueandyou.data.profile.toDomain
import com.example.hueandyou.data.settings.SettingsRepository
import java.time.Instant
import kotlinx.coroutines.flow.first

interface BackupRepository {
    /** Serializes the complete dataset (settings, profiles, history, thumbnails) as backup JSON. */
    suspend fun exportBackup(): String

    /**
     * Replaces all data (settings, profiles, colors, history, thumbnails) with the contents of
     * [json], in one atomic operation.
     *
     * @throws BackupImportException if [json] is invalid; when that happens, nothing changes.
     */
    suspend fun importBackup(json: String)
}

class DefaultBackupRepository(
    private val profileDao: ProfileDao,
    private val historyDao: HistoryDao,
    private val settingsRepository: SettingsRepository,
    private val thumbnailStore: ThumbnailStore,
    private val serializer: BackupSerializer,
    private val transactionRunner: TransactionRunner,
    private val currentInstant: () -> Instant = Instant::now,
) : BackupRepository {

    override suspend fun exportBackup(): String {
        val defaults = settingsRepository.observeDefaults().first()
        val profiles = profileDao.observeProfilesWithColors().first().map { profileWithColors ->
            val profile = profileWithColors.toDomain()
            BackupProfile(
                id = profile.id,
                name = profile.name,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt,
                bestColorsArgb = profile.bestColors.map { it.argb },
                avoidColorsArgb = profile.avoidColors.map { it.argb },
            )
        }
        val history = historyDao.observeEntries().first().map { entity ->
            val entry = entity.toDomain()
            BackupHistoryEntry(
                id = entry.id,
                type = entry.type,
                name = entry.name,
                createdAt = entry.createdAt,
                thumbnailBytes = thumbnailStore.readBytes(entry.thumbnailPath),
                calibratedArgb = entry.calibratedArgb,
                profileId = entry.profileId,
                profileName = entry.profileName,
                bestColorsArgb = entry.bestColorsArgb,
                avoidColorsArgb = entry.avoidColorsArgb,
                score = entry.score,
                inputColorsArgb = entry.inputColorsArgb,
                wheel = entry.wheel,
                balance = entry.balance,
                sampleX = entry.sampleX,
                sampleY = entry.sampleY,
            )
        }
        return serializer.serialize(
            BackupData(defaults.wheel, defaults.balance, profiles, history),
            exportedAt = currentInstant(),
        )
    }

    override suspend fun importBackup(json: String) {
        // Validation happens here, before anything below touches storage.
        val data = serializer.deserialize(json)

        val oldThumbnailPaths = historyDao.observeEntries().first().map { it.thumbnailPath }
        val newThumbnailPaths = data.history.map { thumbnailStore.writeBytes(it.thumbnailBytes) }

        transactionRunner.runInTransaction {
            profileDao.deleteAllProfiles()
            historyDao.deleteAllEntries()

            val profileIdMap = mutableMapOf<Long, Long>()
            data.profiles.forEach { profile ->
                val newProfileId = profileDao.insertProfile(
                    ProfileEntity(
                        id = 0,
                        name = profile.name,
                        createdAt = profile.createdAt,
                        updatedAt = profile.updatedAt,
                    )
                )
                profileIdMap[profile.id] = newProfileId
                val colors = profile.bestColorsArgb.mapIndexed { index, argb ->
                    PaletteColorEntity(
                        id = 0,
                        profileId = newProfileId,
                        kind = ColorKind.BEST,
                        argb = argb,
                        position = index,
                    )
                } + profile.avoidColorsArgb.mapIndexed { index, argb ->
                    PaletteColorEntity(
                        id = 0,
                        profileId = newProfileId,
                        kind = ColorKind.AVOID,
                        argb = argb,
                        position = index,
                    )
                }
                if (colors.isNotEmpty()) profileDao.insertColors(colors)
            }

            val historyEntities = data.history.mapIndexed { index, entry ->
                HistoryEntryEntity(
                    id = 0,
                    type = entry.type,
                    name = entry.name,
                    createdAt = entry.createdAt,
                    thumbnailPath = newThumbnailPaths[index],
                    calibratedArgb = entry.calibratedArgb,
                    profileId = entry.profileId?.let { profileIdMap[it] },
                    profileName = entry.profileName,
                    bestColorsArgb = entry.bestColorsArgb,
                    avoidColorsArgb = entry.avoidColorsArgb,
                    nearestBestArgb = entry.score.nearestBest?.argb,
                    nearestBestDeltaE = entry.score.nearestBest?.deltaE,
                    nearestAvoidArgb = entry.score.nearestAvoid?.argb,
                    nearestAvoidDeltaE = entry.score.nearestAvoid?.deltaE,
                    closerToAvoid = entry.score.closerToAvoid,
                    inputColorsArgb = entry.inputColorsArgb,
                    wheel = entry.wheel,
                    balance = entry.balance,
                    sampleX = entry.sampleX,
                    sampleY = entry.sampleY,
                )
            }
            if (historyEntities.isNotEmpty()) historyDao.insertAll(historyEntities)

            settingsRepository.setDefaultWheel(data.defaultWheel)
            settingsRepository.setDefaultBalance(data.defaultBalance)
        }

        // Only clean up the previous thumbnails once the replacement has committed.
        oldThumbnailPaths.forEach { thumbnailStore.delete(it) }
    }
}
