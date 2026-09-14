package com.kazox.aufschlag.api.club

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** Pricing/booking-rule tier a membership status resolves to (PLANNING.md "Booking pricing"). */
@Serializable
enum class BookingTier { MEMBER, PAUSED, GUEST }

/** Club-local time-of-day range, half-open [opensAt, closesAt). */
@Serializable
data class DayOpeningHours(
    val opensAt: LocalTime,
    val closesAt: LocalTime,
)

/**
 * Serialized as-is into `clubs.settings` (jsonb) — this class IS the schema, validated on
 * every write via [validate]. Slot length lives on courts (per-court, not here).
 */
@Serializable
data class ClubSettings(
    /** Null value = closed that day. */
    val openingHours: Map<DayOfWeek, DayOpeningHours?> = defaultOpeningHours(),
    val cancellationWindowHours: Int = 24,
    val guestBookingEnabled: Boolean = true,
    val advanceBookingDays: Map<BookingTier, Int> = defaultAdvanceBookingDays(),
    val maxOpenBookings: Map<BookingTier, Int> = defaultMaxOpenBookings(),
) {
    /** Throws [IllegalArgumentException] describing the first violation found. */
    fun validate() {
        require(cancellationWindowHours in 0..168) {
            "cancellationWindowHours must be 0-168, was $cancellationWindowHours"
        }
        openingHours.forEach { (day, hours) ->
            hours?.let {
                require(it.closesAt > it.opensAt) {
                    "closesAt (${it.closesAt}) must be after opensAt (${it.opensAt}) on $day"
                }
            }
        }
        advanceBookingDays.values.forEach {
            require(it in 0..90) { "advanceBookingDays must be 0-90, was $it" }
        }
        maxOpenBookings.values.forEach {
            require(it in 0..100) { "maxOpenBookings must be 0-100, was $it" }
        }
    }
}

private fun defaultOpeningHours(): Map<DayOfWeek, DayOpeningHours?> =
    DayOfWeek.entries.associateWith { DayOpeningHours(LocalTime(8, 0), LocalTime(22, 0)) }

private fun defaultAdvanceBookingDays(): Map<BookingTier, Int> =
    mapOf(BookingTier.MEMBER to 7, BookingTier.PAUSED to 7, BookingTier.GUEST to 2)

private fun defaultMaxOpenBookings(): Map<BookingTier, Int> =
    mapOf(BookingTier.MEMBER to 10, BookingTier.PAUSED to 5, BookingTier.GUEST to 2)
