package com.kazox.aufschlag.services

import com.kazox.aufschlag.api.club.DayOpeningHours
import com.kazox.aufschlag.pricing.slotStartMinutes
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes

internal data class SlotRange(val startTime: LocalTime, val startsAt: Instant, val endsAt: Instant)

/** The club-local slot grid for one day — shared by [SlotAvailabilityService] (renders it) and
 *  [BookingService] (validates a chosen start against it), so the two can never disagree on
 *  where slot boundaries fall.
 *
 *  On DST transition days a wall-clock slot may not be [slotMinutes] long once resolved to
 *  instants: the skipped hour in spring collapses to zero length (its start and end map to the
 *  same instant — an `ends_at > starts_at` violation if booked), the repeated hour in autumn
 *  doubles. Those slots are dropped rather than offered at a length nobody asked for. */
internal fun slotRangesFor(date: LocalDate, opening: DayOpeningHours, slotMinutes: Int, timeZone: TimeZone): List<SlotRange> {
    val openMinute = opening.opensAt.hour * 60 + opening.opensAt.minute
    val closeMinute = opening.closesAt.hour * 60 + opening.closesAt.minute
    val slotLength = slotMinutes.minutes
    return slotStartMinutes(openMinute, closeMinute, slotMinutes).mapNotNull { startMinute ->
        val startTime = LocalTime(startMinute / 60, startMinute % 60)
        val endMinute = startMinute + slotMinutes
        val endTime = LocalTime(endMinute / 60, endMinute % 60)
        val startsAt = LocalDateTime(date, startTime).toInstant(timeZone)
        val endsAt = LocalDateTime(date, endTime).toInstant(timeZone)
        SlotRange(startTime, startsAt, endsAt).takeIf { endsAt - startsAt == slotLength }
    }
}
