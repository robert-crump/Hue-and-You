package com.example.hueandyou.data.profile

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MIGRATION_3_4] only ever calls [SupportSQLiteDatabase.execSQL] - a real Room instance would
 * need Robolectric/instrumentation (the gap this codebase otherwise avoids, see
 * DefaultBackupRepository's TransactionRunner), so this asserts on the exact SQL issued via a
 * recording [Proxy] instead.
 */
class Migration3To4Test {

    @Test
    fun migrate_addsNullableSampleColumns() {
        val executedSql = mutableListOf<String>()

        MIGRATION_3_4.migrate(recordingDatabase(executedSql))

        assertEquals(
            listOf(
                "ALTER TABLE history_entries ADD COLUMN sampleX REAL",
                "ALTER TABLE history_entries ADD COLUMN sampleY REAL",
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
