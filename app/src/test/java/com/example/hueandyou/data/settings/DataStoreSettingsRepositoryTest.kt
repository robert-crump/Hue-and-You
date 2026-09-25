package com.example.hueandyou.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DataStoreSettingsRepositoryTest {

    private lateinit var file: File
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        file = File.createTempFile("settings_test", ".preferences_pb").apply { deleteOnExit() }
        repository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(produceFile = { file })
        )
    }

    @Test
    fun observeDefaults_withNothingStored_fallsBackToPerceptualAndFaithful() = runBlocking {
        val defaults = repository.observeDefaults().first()

        assertEquals(HarmonyWheel.PERCEPTUAL, defaults.wheel)
        assertEquals(HarmonyBalance.FAITHFUL, defaults.balance)
    }

    @Test
    fun setDefaultWheel_persistsAndLeavesBalanceUntouched() = runBlocking {
        repository.setDefaultWheel(HarmonyWheel.TRADITIONAL)

        val defaults = repository.observeDefaults().first()

        assertEquals(HarmonyWheel.TRADITIONAL, defaults.wheel)
        assertEquals(HarmonyBalance.FAITHFUL, defaults.balance)
    }

    @Test
    fun setDefaultBalance_persistsAndLeavesWheelUntouched() = runBlocking {
        repository.setDefaultBalance(HarmonyBalance.SOFTENED)

        val defaults = repository.observeDefaults().first()

        assertEquals(HarmonyWheel.PERCEPTUAL, defaults.wheel)
        assertEquals(HarmonyBalance.SOFTENED, defaults.balance)
    }

    @Test
    fun bothDefaults_roundTripTogether() = runBlocking {
        repository.setDefaultWheel(HarmonyWheel.SCREEN)
        repository.setDefaultBalance(HarmonyBalance.SOFTENED)

        val defaults = repository.observeDefaults().first()

        assertEquals(HarmonyWheel.SCREEN, defaults.wheel)
        assertEquals(HarmonyBalance.SOFTENED, defaults.balance)
    }

    @Test
    fun observeLastUsedClothingProfileId_withNothingStored_isNull() = runBlocking {
        assertEquals(null, repository.observeLastUsedClothingProfileId().first())
    }

    @Test
    fun setLastUsedClothingProfileId_persists() = runBlocking {
        repository.setLastUsedClothingProfileId(7L)

        assertEquals(7L, repository.observeLastUsedClothingProfileId().first())
    }
}
