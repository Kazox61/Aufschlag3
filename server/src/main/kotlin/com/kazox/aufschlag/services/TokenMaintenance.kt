package com.kazox.aufschlag.services

import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.repositories.PasswordResetTokenRepository
import com.kazox.aufschlag.repositories.RefreshTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Deletes refresh and password-reset tokens past their `expires_at`. Every rotation inserts a
 * refresh-token row (one per client per access-token TTL), so without this the table grows
 * without bound. An expired token is rejected before any family check ([AuthService.refresh]
 * claims only non-revoked, unexpired rows), so removing it changes nothing a client can
 * observe — a rotated token keeps its original `expires_at`, so reuse detection still covers
 * the token's full lifetime.
 */
class TokenMaintenance(
    private val db: Database,
    private val refreshTokens: RefreshTokenRepository,
    private val resetTokens: PasswordResetTokenRepository,
    private val clock: () -> Instant = Instant::now,
) {
    private val log = LoggerFactory.getLogger(TokenMaintenance::class.java)

    /** One pass; returns rows deleted. */
    suspend fun pruneExpired(): Int {
        val now = clock()
        return withTransaction(db) {
            refreshTokens.deleteExpiredBefore(now) + resetTokens.deleteExpiredBefore(now)
        }
    }

    /** Runs [pruneExpired] every [interval] until the returned job is cancelled; a failed pass
     *  is logged and retried next interval rather than ending the loop. */
    fun start(scope: CoroutineScope, interval: Duration = 1.hours): Job = scope.launch {
        while (isActive) {
            runCatching { pruneExpired() }
                .onSuccess { if (it > 0) log.info("Pruned {} expired tokens", it) }
                .onFailure { log.error("Token maintenance failed", it) }
            delay(interval)
        }
    }
}
