package com.example.hueandyou.data.profile

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the nullable pick-location columns history entries gained for re-pick support. */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_entries ADD COLUMN sampleX REAL")
        db.execSQL("ALTER TABLE history_entries ADD COLUMN sampleY REAL")
    }
}
