package com.example.hueandyou.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.hueandyou.data.backup.BackupRepository
import com.example.hueandyou.data.backup.DefaultBackupRepository
import com.example.hueandyou.data.backup.JsonBackupSerializer
import com.example.hueandyou.data.backup.RoomTransactionRunner
import com.example.hueandyou.data.history.DefaultHistoryRepository
import com.example.hueandyou.data.history.FileThumbnailStore
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.profile.HueAndYouDatabase
import com.example.hueandyou.data.profile.MIGRATION_3_4
import com.example.hueandyou.data.profile.ProfileRepository
import com.example.hueandyou.data.profile.RoomProfileRepository
import com.example.hueandyou.data.settings.DataStoreSettingsRepository
import com.example.hueandyou.data.settings.SettingsRepository
import com.example.hueandyou.data.share.FileShareCardRenderer
import com.example.hueandyou.data.share.ShareCardRenderer

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
    val historyRepository: HistoryRepository
    val thumbnailStore: ThumbnailStore
    val settingsRepository: SettingsRepository
    val shareCardRenderer: ShareCardRenderer
    val backupRepository: BackupRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val settingsDataStore: DataStore<Preferences> = context.settingsDataStore

    private val database: HueAndYouDatabase by lazy {
        Room.databaseBuilder(context, HueAndYouDatabase::class.java, "hue_and_you.db")
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    override val profileRepository: ProfileRepository by lazy {
        RoomProfileRepository(database.profileDao())
    }

    override val thumbnailStore: ThumbnailStore by lazy {
        FileThumbnailStore(context)
    }

    override val historyRepository: HistoryRepository by lazy {
        DefaultHistoryRepository(database.historyDao(), thumbnailStore)
    }

    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(settingsDataStore)
    }

    override val shareCardRenderer: ShareCardRenderer by lazy {
        FileShareCardRenderer(context)
    }

    override val backupRepository: BackupRepository by lazy {
        DefaultBackupRepository(
            profileDao = database.profileDao(),
            historyDao = database.historyDao(),
            settingsRepository = settingsRepository,
            thumbnailStore = thumbnailStore,
            serializer = JsonBackupSerializer(),
            transactionRunner = RoomTransactionRunner(database),
        )
    }
}
