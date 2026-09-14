package com.kazox.aufschlag.services

import com.kazox.aufschlag.TestDatabase
import com.kazox.aufschlag.repositories.postgres.PostgresPasswordResetTokenRepository
import com.kazox.aufschlag.repositories.postgres.PostgresRefreshTokenRepository
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class TokenMaintenanceTest {

    private val now: Instant = Instant.parse("2026-07-16T12:00:00Z")
    private val maintenance = TokenMaintenance(
        db = TestDatabase.database,
        refreshTokens = PostgresRefreshTokenRepository(),
        resetTokens = PostgresPasswordResetTokenRepository(),
        clock = { now },
    )

    @Test
    fun `prunes expired refresh and reset tokens, keeps live ones`() = runBlocking {
        val userId = insertUser()
        val expiredRefresh = insertRefreshToken(userId, expiresAt = now.minusSeconds(1))
        val liveRefresh = insertRefreshToken(userId, expiresAt = now.plusSeconds(3600))
        // a live predecessor pointing at an expired successor (TTL shortened in between) must
        // not block the delete — the FK is ON DELETE SET NULL
        val liveWithExpiredSuccessor = insertRefreshToken(userId, expiresAt = now.plusSeconds(3600), replacedById = expiredRefresh)
        val expiredReset = insertResetToken(userId, expiresAt = now.minusSeconds(1))
        val liveReset = insertResetToken(userId, expiresAt = now.plusSeconds(60))

        val deleted = maintenance.pruneExpired()

        assertEquals(2, deleted)
        assertEquals(setOf(liveRefresh, liveWithExpiredSuccessor), refreshTokenIds(userId))
        assertEquals(setOf(liveReset), resetTokenIds(userId))
        assertEquals(null, replacedById(liveWithExpiredSuccessor))
    }

    private fun insertUser(): String {
        val id = Uuid.random().toString()
        exec("INSERT INTO users (id, email, name) VALUES (?::uuid, ?, 'Maint')", id, "maint-$id@example.test")
        return id
    }

    private fun insertRefreshToken(userId: String, expiresAt: Instant, replacedById: String? = null): String {
        val id = Uuid.random().toString()
        exec(
            "INSERT INTO refresh_tokens (id, user_id, family_id, token_hash, expires_at, replaced_by_id) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?::timestamptz, ?::uuid)",
            id, userId, id, "hash-$id", expiresAt.toString(), replacedById,
        )
        return id
    }

    private fun insertResetToken(userId: String, expiresAt: Instant): String {
        val id = Uuid.random().toString()
        exec(
            "INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at) VALUES (?::uuid, ?::uuid, ?, ?::timestamptz)",
            id, userId, "hash-$id", expiresAt.toString(),
        )
        return id
    }

    private fun refreshTokenIds(userId: String) = queryIds("SELECT id FROM refresh_tokens WHERE user_id = ?::uuid", userId)
    private fun resetTokenIds(userId: String) = queryIds("SELECT id FROM password_reset_tokens WHERE user_id = ?::uuid", userId)

    private fun replacedById(id: String): String? =
        queryIds("SELECT replaced_by_id FROM refresh_tokens WHERE id = ?::uuid", id).singleOrNull()

    private fun exec(sql: String, vararg params: String?) {
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { i, p -> statement.setString(i + 1, p) }
                statement.executeUpdate()
            }
            connection.commit()
        }
    }

    private fun queryIds(sql: String, param: String): Set<String> =
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, param)
                statement.executeQuery().use { rs ->
                    buildSet { while (rs.next()) rs.getString(1)?.let { add(it) } }
                }
            }.also { connection.rollback() }
        }
}
