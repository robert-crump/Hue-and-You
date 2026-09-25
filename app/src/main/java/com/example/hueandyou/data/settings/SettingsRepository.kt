package com.example.hueandyou.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val DEFAULT_WHEEL_KEY = stringPreferencesKey("default_harmony_wheel")
private val DEFAULT_BALANCE_KEY = stringPreferencesKey("default_harmony_balance")
private val LAST_USED_CLOTHING_PROFILE_ID_KEY = longPreferencesKey("last_used_clothing_profile_id")

private val FALLBACK_WHEEL = HarmonyWheel.PERCEPTUAL
private val FALLBACK_BALANCE = HarmonyBalance.FAITHFUL

interface SettingsRepository {
    /** The wheel/balance new object results should start with; reopened results keep their own. */
    fun observeDefaults(): Flow<HarmonyDefaults>
    suspend fun setDefaultWheel(wheel: HarmonyWheel)
    suspend fun setDefaultBalance(balance: HarmonyBalance)

    /** The profile Rate Clothing should start with; null if none has been used yet. */
    fun observeLastUsedClothingProfileId(): Flow<Long?>
    suspend fun setLastUsedClothingProfileId(profileId: Long)
}

data class HarmonyDefaults(
    val wheel: HarmonyWheel = FALLBACK_WHEEL,
    val balance: HarmonyBalance = FALLBACK_BALANCE,
)

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeDefaults(): Flow<HarmonyDefaults> = dataStore.data.map { prefs ->
        HarmonyDefaults(
            wheel = prefs[DEFAULT_WHEEL_KEY]?.let(::parseWheel) ?: FALLBACK_WHEEL,
            balance = prefs[DEFAULT_BALANCE_KEY]?.let(::parseBalance) ?: FALLBACK_BALANCE,
        )
    }

    override suspend fun setDefaultWheel(wheel: HarmonyWheel) {
        dataStore.edit { it[DEFAULT_WHEEL_KEY] = wheel.name }
    }

    override suspend fun setDefaultBalance(balance: HarmonyBalance) {
        dataStore.edit { it[DEFAULT_BALANCE_KEY] = balance.name }
    }

    override fun observeLastUsedClothingProfileId(): Flow<Long?> =
        dataStore.data.map { prefs -> prefs[LAST_USED_CLOTHING_PROFILE_ID_KEY] }

    override suspend fun setLastUsedClothingProfileId(profileId: Long) {
        dataStore.edit { it[LAST_USED_CLOTHING_PROFILE_ID_KEY] = profileId }
    }

    private fun parseWheel(name: String): HarmonyWheel =
        HarmonyWheel.entries.firstOrNull { it.name == name } ?: FALLBACK_WHEEL

    private fun parseBalance(name: String): HarmonyBalance =
        HarmonyBalance.entries.firstOrNull { it.name == name } ?: FALLBACK_BALANCE
}
