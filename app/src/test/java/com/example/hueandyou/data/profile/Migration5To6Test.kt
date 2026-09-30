package com.example.hueandyou.data.profile

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

/** Asserts on the SQL [MIGRATION_5_6] issues, for the same reason as [Migration3To4Test]. */
class Migration5To6Test {

    @Test
    fun migrate_addsWardrobeOffAndNullCategoryColumns() {
        val executedSql = mutableListOf<String>()

        MIGRATION_5_6.migrate(recordingDatabase(executedSql))

        assertEquals(
            listOf(
                "ALTER TABLE history_entries ADD COLUMN inWardrobe INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE history_entries ADD COLUMN category TEXT",
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
