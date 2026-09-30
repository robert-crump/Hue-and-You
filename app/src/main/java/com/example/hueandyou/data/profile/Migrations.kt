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

/** Adds the history entries' saved chip colors; existing rows start empty and are backfilled on open. */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_entries ADD COLUMN chipColorsArgb TEXT NOT NULL DEFAULT ''")
    }
}

/** Adds the clothing entries' wardrobe flag and category; existing rows start not owned and uncategorized. */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_entries ADD COLUMN inWardrobe INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE history_entries ADD COLUMN category TEXT")
    }
}

/**
 * Adds outfits and their ordered items. Items cascade away with their outfit or their Clothes
 * entry. The SQL matches what Room generates for OutfitEntity and OutfitItemEntity.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `outfits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `outfit_items` (`outfitId` INTEGER NOT NULL, " +
                "`entryId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`outfitId`, `entryId`), " +
                "FOREIGN KEY(`outfitId`) REFERENCES `outfits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`entryId`) REFERENCES `history_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_outfit_items_entryId` ON `outfit_items` (`entryId`)")
    }
}
