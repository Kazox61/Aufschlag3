package com.kazox.aufschlag.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * The single transaction entry point. Services own transaction boundaries and compose
 * repository calls inside one block; repositories never open transactions themselves.
 * JDBC blocks the carrier thread, hence Dispatchers.IO. The block is intentionally
 * non-suspending: a transaction holds a pooled connection, so no network calls
 * (mail, HTTP) belong inside it.
 */
suspend fun <T> withTransaction(db: Database, block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction(db = db) { block() }
    }
