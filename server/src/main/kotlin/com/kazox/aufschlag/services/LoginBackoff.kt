package com.kazox.aufschlag.services

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-email exponential backoff for failed logins — deliberately NOT a hard block:
 * a hard per-email block would let an attacker lock a victim out of their own
 * account by spamming logins. In-memory, i.e. per server
 * instance; fine for the single-instance v1 deployment.
 */
class LoginBackoff(
    private val clock: () -> Instant = Instant::now,
    /** Failures before the first delay kicks in. */
    private val threshold: Int = 3,
    private val baseDelay: Duration = Duration.ofSeconds(1),
    private val maxDelay: Duration = Duration.ofMinutes(5),
    /** Hard cap on tracked emails. Failed logins against many distinct addresses would
     *  otherwise grow the map without bound; when it fills, the stalest half is dropped. */
    private val maxEntries: Int = 10_000,
) {
    private data class Entry(val failures: Int, val blockedUntil: Instant?, val lastFailureAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Seconds until the next attempt is allowed, or null if not currently delayed. */
    fun blockedForSeconds(email: String): Long? {
        val entry = entries[email] ?: return null
        val now = clock()
        if (Duration.between(entry.lastFailureAt, now) > FORGET_AFTER) {
            entries.remove(email, entry)
            return null
        }
        val until = entry.blockedUntil ?: return null
        val remaining = Duration.between(now, until)
        return if (remaining.isPositive) remaining.seconds.coerceAtLeast(1) else null
    }

    fun recordFailure(email: String) {
        val now = clock()
        if (entries.size >= maxEntries && !entries.containsKey(email)) prune(now)
        entries.compute(email) { _, prev ->
            val failures = (prev?.failures ?: 0) + 1
            val blockedUntil = if (failures >= threshold) {
                val exponent = (failures - threshold).coerceAtMost(MAX_EXPONENT)
                val delay = baseDelay.multipliedBy(1L shl exponent).coerceAtMost(maxDelay)
                now.plus(delay)
            } else {
                null
            }
            Entry(failures, blockedUntil, now)
        }
    }

    fun recordSuccess(email: String) {
        entries.remove(email)
    }

    /** Drops forgotten entries first; if that isn't enough, evicts the least recently failed
     *  until half the capacity is free. Losing an attacker's backoff state under this much
     *  pressure only shortens their delay, it never blocks a legitimate user. */
    private fun prune(now: Instant) {
        entries.entries.removeIf { Duration.between(it.value.lastFailureAt, now) > FORGET_AFTER }
        val excess = entries.size - maxEntries / 2
        if (excess <= 0) return
        entries.entries
            .sortedBy { it.value.lastFailureAt }
            .take(excess)
            .forEach { entries.remove(it.key, it.value) }
    }

    companion object {
        private val FORGET_AFTER: Duration = Duration.ofHours(1)
        private const val MAX_EXPONENT = 20
    }
}

private fun Duration.coerceAtMost(other: Duration): Duration = if (this > other) other else this

private val Duration.isPositive: Boolean get() = !isNegative && !isZero
