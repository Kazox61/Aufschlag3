package com.kazox.aufschlag.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

class HealthService(private val dataSource: DataSource) {
    suspend fun isDatabaseHealthy(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute("SELECT 1") }
            }
        }.isSuccess
    }
}
