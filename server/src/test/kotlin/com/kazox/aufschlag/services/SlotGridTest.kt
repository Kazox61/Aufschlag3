package com.kazox.aufschlag.services

import com.kazox.aufschlag.api.club.DayOpeningHours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class SlotGridTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val open01to05 = DayOpeningHours(LocalTime(1, 0), LocalTime(5, 0))

    @Test
    fun `an ordinary day yields one slot per grid step, each exactly slotMinutes long`() {
        val slots = slotRangesFor(LocalDate(2026, 8, 3), open01to05, 60, berlin)
        assertEquals(listOf(1, 2, 3, 4), slots.map { it.startTime.hour })
        assertTrue(slots.all { it.endsAt - it.startsAt == 60.minutes })
    }

    @Test
    fun `spring-forward day drops the skipped hour instead of a zero-length slot`() {
        // 2026-03-29: 02:00 → 03:00 in Europe/Berlin; local 02:00-03:00 collapses to one instant.
        val slots = slotRangesFor(LocalDate(2026, 3, 29), open01to05, 60, berlin)
        assertEquals(listOf(1, 3, 4), slots.map { it.startTime.hour })
        assertTrue(slots.all { it.endsAt - it.startsAt == 60.minutes })
        assertEquals(slots.map { it.startsAt }.distinct(), slots.map { it.startsAt }) // no duplicate instants
    }

    @Test
    fun `fall-back day drops the repeated hour instead of a double-length slot`() {
        // 2026-10-25: 03:00 → 02:00 in Europe/Berlin; local 02:00-03:00 spans two real hours.
        val slots = slotRangesFor(LocalDate(2026, 10, 25), open01to05, 60, berlin)
        assertEquals(listOf(1, 3, 4), slots.map { it.startTime.hour })
        assertTrue(slots.all { it.endsAt - it.startsAt == 60.minutes })
    }
}
