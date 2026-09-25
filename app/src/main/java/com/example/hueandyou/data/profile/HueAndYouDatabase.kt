package com.example.hueandyou.data.profile

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.hueandyou.data.history.HistoryConverters
import com.example.hueandyou.data.history.HistoryDao
import com.example.hueandyou.data.history.HistoryEntryEntity

@Database(
    entities = [ProfileEntity::class, PaletteColorEntity::class, HistoryEntryEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class, HistoryConverters::class)
abstract class HueAndYouDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun historyDao(): HistoryDao
}
