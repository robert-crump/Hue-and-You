package com.example.hueandyou.data.backup

import android.graphics.Bitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.data.history.FakeHistoryDao
import com.example.hueandyou.data.history.HistoryEntryEntity
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.FakeProfileDao
import com.example.hueandyou.data.profile.PaletteColorEntity
import com.example.hueandyou.data.profile.ProfileEntity
import com.example.hueandyou.data.settings.DataStoreSettingsRepository
import com.example.hueandyou.data.settings.SettingsRepository
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class DefaultBackupRepositoryTest {

    private lateinit var sourceProfileDao: FakeProfileDao
    private lateinit var sourceHistoryDao: FakeHistoryDao
    private lateinit var sourceSettings: SettingsRepository
    private lateinit var sourceThumbnails: FakeThumbnailStore
    private lateinit var sourceRepository: DefaultBackupRepository

    private lateinit var targetProfileDao: FakeProfileDao
    private lateinit var targetHistoryDao: FakeHistoryDao
    private lateinit var targetSettings: SettingsRepository
    private lateinit var targetThumbnails: FakeThumbnailStore
    private lateinit var targetRepository: DefaultBackupRepository

    private val serializer: BackupSerializer = JsonBackupSerializer()
    private val passthroughTransactionRunner = object : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    @Before
    fun setUp() {
        sourceProfileDao = FakeProfileDao()
        sourceHistoryDao = FakeHistoryDao()
        sourceSettings = DataStoreSettingsRepository(tempDataStore("backup_source"))
        sourceThumbnails = FakeThumbnailStore()
        sourceRepository = DefaultBackupRepository(
            sourceProfileDao, sourceHistoryDao, sourceSettings, sourceThumbnails,
            serializer, passthroughTransactionRunner,
        )

        targetProfileDao = FakeProfileDao()
        targetHistoryDao = FakeHistoryDao()
        targetSettings = DataStoreSettingsRepository(tempDataStore("backup_target"))
        targetThumbnails = FakeThumbnailStore()
        targetRepository = DefaultBackupRepository(
            targetProfileDao, targetHistoryDao, targetSettings, targetThumbnails,
            serializer, passthroughTransactionRunner,
        )
    }

    private fun tempDataStore(prefix: String) = PreferenceDataStoreFactory.create(
        produceFile = { File.createTempFile(prefix, ".preferences_pb").apply { deleteOnExit() } }
    )

    @Test
    fun exportThenImport_restoresIdenticalDataset() = runBlocking {
        sourceSettings.setDefaultWheel(HarmonyWheel.SCREEN)
        sourceSettings.setDefaultBalance(HarmonyBalance.SOFTENED)

        val profileId = sourceProfileDao.insertProfile(
            ProfileEntity(name = "Autumn", createdAt = 1_000L, updatedAt = 2_000L)
        )
        sourceProfileDao.insertColor(
            PaletteColorEntity(profileId = profileId, kind = ColorKind.BEST, argb = 0xFF112233.toInt(), position = 0)
        )
        sourceProfileDao.insertColor(
            PaletteColorEntity(profileId = profileId, kind = ColorKind.AVOID, argb = 0xFF778899.toInt(), position = 0)
        )

        sourceThumbnails.seed("clothing.jpg", byteArrayOf(1, 2, 3))
        sourceHistoryDao.insert(
            HistoryEntryEntity(
                type = HistoryEntryType.CLOTHING,
                name = "Clothing",
                createdAt = 3_000L,
                thumbnailPath = "clothing.jpg",
                calibratedArgb = 0xFF112233.toInt(),
                profileId = profileId,
                profileName = "Autumn",
                bestColorsArgb = listOf(0xFF112233.toInt()),
                avoidColorsArgb = listOf(0xFF778899.toInt()),
                nearestBestArgb = 0xFF112233.toInt(),
                nearestBestDeltaE = 1.5,
                nearestAvoidArgb = 0xFF778899.toInt(),
                nearestAvoidDeltaE = 25.0,
                closerToAvoid = false,
            )
        )

        sourceThumbnails.seed("object.jpg", byteArrayOf(4, 5, 6))
        sourceHistoryDao.insert(
            HistoryEntryEntity(
                type = HistoryEntryType.OBJECT,
                name = "Object",
                createdAt = 4_000L,
                thumbnailPath = "object.jpg",
                calibratedArgb = 0xFF001122.toInt(),
                profileId = null,
                profileName = null,
                bestColorsArgb = emptyList(),
                avoidColorsArgb = emptyList(),
                nearestBestArgb = null,
                nearestBestDeltaE = null,
                nearestAvoidArgb = null,
                nearestAvoidDeltaE = null,
                closerToAvoid = false,
                inputColorsArgb = listOf(0xFF001122.toInt(), 0xFF334455.toInt()),
                wheel = HarmonyWheel.TRADITIONAL,
                balance = HarmonyBalance.FAITHFUL,
            )
        )

        val json = sourceRepository.exportBackup()
        targetRepository.importBackup(json)

        val targetDefaults = targetSettings.observeDefaults().first()
        assertEquals(HarmonyWheel.SCREEN, targetDefaults.wheel)
        assertEquals(HarmonyBalance.SOFTENED, targetDefaults.balance)

        // Both DAOs started empty and were filled in the same order, so profile/color ids line up.
        assertEquals(
            sourceProfileDao.observeProfilesWithColors().first(),
            targetProfileDao.observeProfilesWithColors().first(),
        )

        // History ids don't line up the same way (export orders by createdAt desc, re-insertion
        // order then differs from the original insertion order) - id and path are storage details,
        // not part of the dataset, so they're normalized away before comparing.
        fun HistoryEntryEntity.normalized() = copy(id = 0, thumbnailPath = "")
        assertEquals(
            sourceHistoryDao.observeEntries().first().map { it.normalized() },
            targetHistoryDao.observeEntries().first().map { it.normalized() },
        )

        targetHistoryDao.observeEntries().first().forEach { entity ->
            val expectedBytes = if (entity.name == "Clothing") byteArrayOf(1, 2, 3) else byteArrayOf(4, 5, 6)
            assertArrayEquals(expectedBytes, targetThumbnails.readBytes(entity.thumbnailPath))
        }
    }

    @Test
    fun importBackup_withInvalidJson_throwsAndLeavesDataUnchanged() = runBlocking {
        sourceProfileDao.insertProfile(ProfileEntity(name = "Autumn", createdAt = 1L, updatedAt = 1L))

        assertThrows(BackupImportException::class.java) {
            runBlocking { sourceRepository.importBackup("{ not valid json") }
        }

        assertEquals(1, sourceProfileDao.observeProfilesWithColors().first().size)
    }

    @Test
    fun importBackup_withUnknownSchemaVersion_throwsAndLeavesDataUnchanged() = runBlocking {
        sourceProfileDao.insertProfile(ProfileEntity(name = "Autumn", createdAt = 1L, updatedAt = 1L))
        val futureJson = serializer.serialize(
            BackupData(HarmonyWheel.PERCEPTUAL, HarmonyBalance.FAITHFUL, emptyList(), emptyList())
        ).replace("\"schemaVersion\": 1,", "\"schemaVersion\": 999,")

        assertThrows(BackupImportException::class.java) {
            runBlocking { sourceRepository.importBackup(futureJson) }
        }

        assertEquals(1, sourceProfileDao.observeProfilesWithColors().first().size)
    }
}

private class FakeThumbnailStore : ThumbnailStore {
    private val files = mutableMapOf<String, ByteArray>()
    private var counter = 0

    override suspend fun save(bitmap: Bitmap): String =
        throw UnsupportedOperationException("not used by this test")

    override suspend fun delete(path: String) {
        files.remove(path)
    }

    override suspend fun readBytes(path: String): ByteArray = files[path] ?: throw FileNotFoundException(path)

    override suspend fun writeBytes(bytes: ByteArray): String {
        val path = "thumb_${counter++}.jpg"
        files[path] = bytes
        return path
    }

    fun seed(path: String, bytes: ByteArray) {
        files[path] = bytes
    }
}
