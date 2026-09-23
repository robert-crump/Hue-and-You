package com.example.hueandyou.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.hueandyou.data.profile.HueAndYouDatabase
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.data.profile.RoomProfileRepository

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hue_and_you_settings"
)

/**
 * Manual dependency injection container. No DI framework is used; screens obtain
 * their dependencies from [HueAndYouApplication.container].
 */
interface AppContainer {
    val settingsDataStore: DataStore<Preferences>
    val profileRepository: ProfileRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val settingsDataStore: DataStore<Preferences> = context.settingsDataStore

    private val database: HueAndYouDatabase by lazy {
        Room.databaseBuilder(context, HueAndYouDatabase::class.java, "hue_and_you.db").build()
    }

    override val profileRepository: ProfileRepository by lazy {
        RoomProfileRepository(database.profileDao())
    }
}
