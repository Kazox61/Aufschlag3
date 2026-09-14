package com.kazox.aufschlag

import com.kazox.aufschlag.config.DatabaseConfig
import com.kazox.aufschlag.db.createDataSource
import com.kazox.aufschlag.db.runMigrations
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

/**
 * One Postgres container + migrated schema shared by every test in the JVM.
 * Testcontainers' Ryuk reaper removes the container after the test run.
 * Real Postgres only — the schema needs citext/gist, so no H2 anywhere.
 */
object TestDatabase {
    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:17-alpine").also { it.start() }
    }

    val dataSource: DataSource by lazy {
        createDataSource(
            DatabaseConfig(
                url = container.jdbcUrl,
                username = container.username,
                password = container.password,
                maxPoolSize = 5,
            ),
        ).also { runMigrations(it) }
    }

    val database: Database by lazy { Database.connect(dataSource) }
}

/** Test-only DB shortcut — flips the row directly since there's no admin endpoint for this yet. */
fun makeSuperAdmin(email: String) {
    TestDatabase.dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE users SET is_super_admin = true WHERE email = ?").use {
            it.setString(1, email.lowercase())
            it.executeUpdate()
        }
        connection.commit()
    }
}
