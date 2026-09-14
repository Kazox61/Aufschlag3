package com.kazox.aufschlag.repositories

import java.time.Instant
import kotlin.uuid.Uuid

data class RefreshTokenRow(
    val id: Uuid,
    val userId: Uuid,
    val familyId: Uuid,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val replacedById: Uuid?,
)

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface RefreshTokenRepository {
    fun insert(id: Uuid, userId: Uuid, familyId: Uuid, tokenHash: String, expiresAt: Instant)

    /**
     * Atomic rotation claim: sets revoked_at = [now] iff the row is not yet revoked,
     * and returns the claimed row. Two concurrent refreshes with the same token get
     * at most one non-null result (single UPDATE .. WHERE revoked_at IS NULL).
     */
    fun claimForRotation(tokenHash: String, now: Instant): RefreshTokenRow?

    fun setReplacedBy(id: Uuid, replacedById: Uuid)
    fun findById(id: Uuid): RefreshTokenRow?
    fun findByHash(tokenHash: String): RefreshTokenRow?
    fun revokeFamily(familyId: Uuid, now: Instant)
    fun revokeAllForUser(userId: Uuid, now: Instant)
}
