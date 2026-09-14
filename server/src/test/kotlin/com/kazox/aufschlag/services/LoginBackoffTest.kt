package com.kazox.aufschlag.services

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoginBackoffTest {

    private var now: Instant = Instant.parse("2026-07-16T12:00:00Z")
    private val backoff = LoginBackoff(
        clock = { now },
        threshold = 3,
        baseDelay = Duration.ofSeconds(1),
        maxDelay = Duration.ofMinutes(5),
    )

    private fun advance(seconds: Long) {
        now = now.plusSeconds(seconds)
    }

    @Test
    fun `no delay below the failure threshold`() {
        repeat(2) { backoff.recordFailure("a@x.de") }
        assertNull(backoff.blockedForSeconds("a@x.de"))
    }

    @Test
    fun `delay kicks in at the threshold and grows exponentially, capped at max`() {
        repeat(3) { backoff.recordFailure("b@x.de") }
        assertEquals(1, backoff.blockedForSeconds("b@x.de")) // base delay

        advance(2)
        assertNull(backoff.blockedForSeconds("b@x.de")) // delay elapsed → attempt allowed again

        backoff.recordFailure("b@x.de") // 4th failure → 2s
        assertEquals(2, backoff.blockedForSeconds("b@x.de"))

        repeat(20) { backoff.recordFailure("b@x.de") }
        val blocked = assertNotNull(backoff.blockedForSeconds("b@x.de"))
        assertEquals(300, blocked) // capped at maxDelay — never a permanent lock
    }

    @Test
    fun `success clears the counter`() {
        repeat(5) { backoff.recordFailure("c@x.de") }
        backoff.recordSuccess("c@x.de")
        assertNull(backoff.blockedForSeconds("c@x.de"))
    }

    @Test
    fun `emails are tracked independently`() {
        repeat(5) { backoff.recordFailure("d@x.de") }
        assertNull(backoff.blockedForSeconds("e@x.de"))
    }

    @Test
    fun `stale entries are forgotten after an hour`() {
        repeat(10) { backoff.recordFailure("f@x.de") }
        assertNotNull(backoff.blockedForSeconds("f@x.de"))

        advance(3601)
        assertNull(backoff.blockedForSeconds("f@x.de"))
    }
}
