package com.kazox.aufschlag.api.booking

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class BookingStatus { ACTIVE, CANCELLED, BLOCKED }

@Serializable
enum class PaymentStatus { NONE, DUE, PAID, WAIVED }

/** [endsAt] is derived server-side from the court's slotMinutes — only the start is chosen. */
@Serializable
data class CreateBookingRequest(
    val courtId: String,
    val startsAt: Instant,
)

@Serializable
data class BookingResponse(
    val id: String,
    val clubId: String,
    val courtId: String,
    val userId: String? = null,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: BookingStatus,
    val note: String? = null,
    val basePriceCents: Int,
    val discountPct: Int,
    val finalPriceCents: Int,
    val paymentStatus: PaymentStatus,
)

/** Admin-only (PLANNING.md "Bookings": "admins block slots (with note)"). A block is a booking
 *  with `status = BLOCKED`, no [userId], and no price — it just occupies the slot so members
 *  can't book it (court maintenance, training, ...). Single-slot like a normal booking; [endsAt]
 *  is derived server-side the same way. */
@Serializable
data class CreateBlockRequest(
    val courtId: String,
    val startsAt: Instant,
    val note: String? = null,
)

/** Admin-only: records that a booking's fee was collected off-app (cash/transfer) or is being
 *  forgiven — payment is recorded, not processed in v1 (PLANNING.md "Booking pricing"). */
@Serializable
data class UpdateBookingPaymentRequest(
    val paymentStatus: PaymentStatus,
)

/** GET /clubs/{clubId}/courts/{courtId}/bookings?date= (admin) — the day's occupying bookings
 *  (ACTIVE and BLOCKED) *with their ids*, unlike the public slots endpoint, so an admin can
 *  cancel/un-block/mark-paid from the schedule (docs/admin-webapp.md §1.4). [userName] is the
 *  booker's display name; null for admin blocks (no owner) or a since-deleted account. Plain
 *  list, no pagination — a single day is bounded by the slot grid. */
@Serializable
data class CourtDayBookingResponse(
    val booking: BookingResponse,
    val userName: String? = null,
)

/** GET /me/bookings — joined with club/court display names (like [com.kazox.aufschlag.api.club.MyMembershipResponse])
 *  so the my-bookings screen can render a row without a separate lookup per booking. [clubTimezone]
 *  (IANA zone id) lets the screen show each booking's time club-local — bookings here span multiple
 *  clubs, which may each be in a different zone, so there's no single "current club" timezone to
 *  fall back on the way the day/slot view does. */
@Serializable
data class MyBookingResponse(
    val booking: BookingResponse,
    val clubName: String,
    val clubTimezone: String,
    val courtName: String,
)
