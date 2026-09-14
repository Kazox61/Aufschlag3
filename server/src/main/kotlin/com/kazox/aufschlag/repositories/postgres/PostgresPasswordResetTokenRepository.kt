package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.PasswordResetTokensTable
import com.kazox.aufschlag.repositories.PasswordResetTokenRepository
import com.kazox.aufschlag.repositories.PasswordResetTokenRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.updateReturning
import java.time.Instant
import kotlin.uuid.Uuid

class PostgresPasswordResetTokenRepository : PasswordResetTokenRepository {

    override fun insert(userId: Uuid, tokenHash: String, expiresAt: Instant) {
        PasswordResetTokensTable.insert {
            it[id] = Uuid.random()
            it[PasswordResetTokensTable.userId] = userId
            it[PasswordResetTokensTable.tokenHash] = tokenHash
            it[PasswordResetTokensTable.expiresAt] = expiresAt
        }
    }

    override fun claim(tokenHash: String, now: Instant): PasswordResetTokenRow? =
        PasswordResetTokensTable.updateReturning(
            where = {
                (PasswordResetTokensTable.tokenHash eq tokenHash) and
                    PasswordResetTokensTable.usedAt.isNull()
            },
        ) {
            it[usedAt] = now
        }.singleOrNull()?.let {
            PasswordResetTokenRow(
                id = it[PasswordResetTokensTable.id],
                userId = it[PasswordResetTokensTable.userId],
                expiresAt = it[PasswordResetTokensTable.expiresAt],
                usedAt = it[PasswordResetTokensTable.usedAt],
            )
        }
}
