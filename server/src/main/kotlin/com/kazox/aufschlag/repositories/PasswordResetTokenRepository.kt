package com.kazox.aufschlag.repositories

import java.time.Instant
import kotlin.uuid.Uuid

data class PasswordResetTokenRow(
    val id: Uuid,
    val userId: Uuid,
    val expiresAt: Instant,
    val usedAt: Instant?,
)

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface PasswordResetTokenRepository {
    fun insert(userId: Uuid, tokenHash: String, expiresAt: Instant)

    /**
     * Atomic single-use claim: sets used_at = [now] iff the row is not yet used, and
     * returns the claimed row. Two concurrent confirms with the same token get at
     * most one non-null result (single UPDATE .. WHERE used_at IS NULL).
     */
    fun claim(tokenHash: String, now: Instant): PasswordResetTokenRow?

    /** Deletes rows whose `expires_at` is before [cutoff] (used or not). Returns rows deleted. */
    fun deleteExpiredBefore(cutoff: Instant): Int
}
