package com.kazox.aufschlag.db

import com.kazox.aufschlag.TestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationTest {

    @Test
    fun `auth tables exist after migration`() {
        TestDatabase.dataSource.connection.use { connection ->
            val tables = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) add(rs.getString(1))
                    }
                }
            }
            connection.rollback()

            listOf("users", "auth_identities", "refresh_tokens", "password_reset_tokens").forEach {
                assertTrue(it in tables, "expected table '$it' to exist, found: $tables")
            }
        }
    }

    @Test
    fun `users email is citext - lookup is case-insensitive`() {
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO users (email, name) VALUES (?, ?)").use {
                it.setString(1, "Casing@Example.com")
                it.setString(2, "Case Test")
                it.executeUpdate()
            }
            val count = connection.prepareStatement("SELECT count(*) FROM users WHERE email = ?").use {
                it.setString(1, "casing@example.com")
                it.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            // pool runs with autoCommit=false; roll back so tests leave no data behind
            connection.rollback()

            assertEquals(1, count)
        }
    }
}
