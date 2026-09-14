package com.kazox.aufschlag.api.booking

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class SlotResponse(
    val startsAt: Instant,
    val endsAt: Instant,
    val available: Boolean,
    val priceCents: Int,
)

/** [canBook] is false when the caller's own membership is SUSPENDED, or when they'd book as a
 *  GUEST and the club has guest booking disabled — they may still see the schedule, just not
 *  book (PLANNING.md "Booking pricing"). */
@Serializable
data class DayAvailabilityResponse(
    val date: LocalDate,
    val canBook: Boolean,
    val slots: List<SlotResponse>,
)
