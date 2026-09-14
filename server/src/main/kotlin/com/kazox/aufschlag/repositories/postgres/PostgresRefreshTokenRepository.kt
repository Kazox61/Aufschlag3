package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.RefreshTokensTable
import com.kazox.aufschlag.repositories.RefreshTokenRepository
import com.kazox.aufschlag.repositories.RefreshTokenRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import java.time.Instant
import kotlin.uuid.Uuid

class PostgresRefreshTokenRepository : RefreshTokenRepository {

    override fun insert(id: Uuid, userId: Uuid, familyId: Uuid, tokenHash: String, expiresAt: Instant) {
        RefreshTokensTable.insert {
            it[RefreshTokensTable.id] = id
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.familyId] = familyId
            it[RefreshTokensTable.tokenHash] = tokenHash
            it[RefreshTokensTable.expiresAt] = expiresAt
        }
    }

    override fun claimForRotation(tokenHash: String, now: Instant): RefreshTokenRow? =
        RefreshTokensTable.updateReturning(
            where = {
                (RefreshTokensTable.tokenHash eq tokenHash) and RefreshTokensTable.revokedAt.isNull()
            },
        ) {
            it[revokedAt] = now
        }.singleOrNull()?.toRow()

    override fun setReplacedBy(id: Uuid, replacedById: Uuid) {
        RefreshTokensTable.update({ RefreshTokensTable.id eq id }) {
            it[RefreshTokensTable.replacedById] = replacedById
        }
    }

    override fun findById(id: Uuid): RefreshTokenRow? =
        RefreshTokensTable.selectAll()
            .where { RefreshTokensTable.id eq id }
            .singleOrNull()
            ?.toRow()

    override fun findByHash(tokenHash: String): RefreshTokenRow? =
        RefreshTokensTable.selectAll()
            .where { RefreshTokensTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?.toRow()

    override fun revokeFamily(familyId: Uuid, now: Instant) {
        RefreshTokensTable.update({
            (RefreshTokensTable.familyId eq familyId) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[revokedAt] = now
        }
    }

    override fun revokeAllForUser(userId: Uuid, now: Instant) {
        RefreshTokensTable.update({
            (RefreshTokensTable.userId eq userId) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[revokedAt] = now
        }
    }

    override fun deleteExpiredBefore(cutoff: Instant): Int =
        RefreshTokensTable.deleteWhere { RefreshTokensTable.expiresAt less cutoff }

    private fun ResultRow.toRow() = RefreshTokenRow(
        id = this[RefreshTokensTable.id],
        userId = this[RefreshTokensTable.userId],
        familyId = this[RefreshTokensTable.familyId],
        expiresAt = this[RefreshTokensTable.expiresAt],
        revokedAt = this[RefreshTokensTable.revokedAt],
        replacedById = this[RefreshTokensTable.replacedById],
    )
}
