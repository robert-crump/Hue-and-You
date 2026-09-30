package com.example.hueandyou.data.profile

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

/** Asserts on the SQL [MIGRATION_6_7] issues, for the same reason as [Migration3To4Test]. */
class Migration6To7Test {

    @Test
    fun migrate_createsOutfitTablesWhoseItemsCascadeWithTheirOutfitOrEntry() {
        val executedSql = mutableListOf<String>()

        MIGRATION_6_7.migrate(recordingDatabase(executedSql))

        assertEquals(
            listOf(
                "CREATE TABLE IF NOT EXISTS `outfits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `outfit_items` (`outfitId` INTEGER NOT NULL, " +
                    "`entryId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`outfitId`, `entryId`), " +
                    "FOREIGN KEY(`outfitId`) REFERENCES `outfits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`entryId`) REFERENCES `history_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_outfit_items_entryId` ON `outfit_items` (`entryId`)",
            ),
            executedSql,
        )
    }

    private fun recordingDatabase(executedSql: MutableList<String>): SupportSQLiteDatabase {
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args?.size == 1 && args[0] is String) {
                executedSql += args[0] as String
                null
            } else {
                throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        }
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler,
        ) as SupportSQLiteDatabase
    }
}
