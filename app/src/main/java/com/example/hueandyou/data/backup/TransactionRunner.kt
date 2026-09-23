package com.example.hueandyou.data.backup

import androidx.room.withTransaction
import com.example.hueandyou.data.profile.HueAndYouDatabase

/**
 * Runs a block of DAO calls atomically. An indirection over [androidx.room.withTransaction] so
 * [DefaultBackupRepository] can be unit-tested against fake DAOs, which have no real transaction.
 */
interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

class RoomTransactionRunner(private val database: HueAndYouDatabase) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
