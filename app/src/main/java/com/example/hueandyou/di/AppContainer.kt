package com.example.hueandyou.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hue_and_you_settings"
)

/**
 * Manual dependency injection container. No DI framework is used; screens obtain
 * their dependencies from [HueAndYouApplication.container].
 */
interface AppContainer {
    val settingsDataStore: DataStore<Preferences>
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val settingsDataStore: DataStore<Preferences> = context.settingsDataStore
}
