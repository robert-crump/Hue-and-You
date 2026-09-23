package com.example.hueandyou.data.profile

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ProfileEntity::class, PaletteColorEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HueAndYouDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
