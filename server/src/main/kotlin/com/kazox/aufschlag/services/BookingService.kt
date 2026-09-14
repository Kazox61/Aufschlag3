package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.booking.BookingResponse
import com.kazox.aufschlag.api.booking.BookingStatus
import com.kazox.aufschlag.api.booking.CourtDayBookingResponse
import com.kazox.aufschlag.api.booking.CreateBlockRequest
import com.kazox.aufschlag.api.booking.CreateBookingRequest
import com.kazox.aufschlag.api.booking.MyBookingResponse
import com.kazox.aufschlag.api.booking.PaymentStatus
import com.kazox.aufschlag.api.club.BookingTier
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.db.isExclusionViolation
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.pricing.BookingEligibility
import com.kazox.aufschlag.pricing.PriceRule
import com.kazox.aufschlag.pricing.resolveSlotPrice
import com.kazox.aufschlag.pricing.toBookingEligibility
import com.kazox.aufschlag.repositories.BookingRepository
import com.kazox.aufschlag.repositories.BookingRow
import com.kazox.aufschlag.repositories.BookingWithNamesRow
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.ClubRow
import com.kazox.aufschlag.repositories.CourtRepository
import com.kazox.aufschlag.repositories.CourtRow
import com.kazox.aufschlag.repositories.MembershipRepository
import com.kazox.aufschlag.repositories.PriceRuleRepository
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Duration
import kotlin.uuid.Uuid

/**
 * Booking create/cancel, admin blocks and payment marking:
 * conflict-free by DB exclusion constraint, per-status advance-booking window and open-booking
 * limit (the latter advisory-locked — a plain count-then-insert would be racy under concurrency),
 * pricing snapshotted at booking time so later rule changes never affect an existing booking.
 */
class BookingService(
    private val db: Database,
    private val clubs: ClubRepository,
    private val courts: CourtRepository,
    private val priceRules: PriceRuleRepository,
    private val memberships: MembershipRepository,
    private val bookings: BookingRepository,
    private val entitlements: EntitlementService,
    private val clock: Clock = Clock.System,
) {
    private data class CourtSlot(
        val club: ClubRow,
        val court: CourtRow,
        val rules: List<PriceRule>,
        val range: SlotRange,
        val timeZone: TimeZone,
    )

    /** Loads the club/court/price rules and validates [startsAt] against that court's club-local
     *  slot grid — shared by [create] and [createBlock], which otherwise diverge (eligibility,
     *  pricing and the booking limits only apply to a member/guest booking, not an admin block).
     *  Called inside the caller's transaction so the insert sees the same club/court state it
     *  was validated against. */
    private fun resolveCourtSlot(clubId: Uuid, courtId: Uuid, startsAt: Instant): CourtSlot {
        val club = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
        entitlements.requireWritable(club)
        val court = courts.findByIdAndClub(courtId, clubId) ?: throw ApiException.notFound("Court not found")
        val rules = priceRules.findByCourt(courtId).map { it.toEngineRule() }
        if (!court.active) throw ApiException.validation("Court is not accepting bookings")

        val timeZone = TimeZone.of(club.timezone)
        val slotDate = startsAt.toLocalDateTime(timeZone).date
        val opening = club.decodeSettings().openingHours[slotDate.dayOfWeek]
            ?: throw ApiException.validation("Club is closed that day")
        val range = slotRangesFor(slotDate, opening, court.slotMinutes, timeZone)
            .firstOrNull { it.startsAt == startsAt }
            ?: throw ApiException.validation("startsAt does not align with this court's slot grid")

        return CourtSlot(club, court, rules, range, timeZone)
    }

    suspend fun create(callerUserId: Uuid, clubId: Uuid, request: CreateBookingRequest): BookingResponse {
        val courtId = parseCourtId(request.courtId)

        return try {
            withTransaction(db) {
                // Held for the rest of this transaction: serializes concurrent booking attempts
                // by this same caller in this same club, and keeps the membership-status read
                // below adjacent to the insert instead of stale — otherwise a status change
                // (e.g. pause/resume) landing between a separate earlier read and this insert
                // could book at a tier/price/limit that no longer matches the caller's status.
                bookings.lockForBookingLimitCheck(callerUserId)

                val slot = resolveCourtSlot(clubId, courtId, request.startsAt)
                val settings = slot.club.decodeSettings()

                val eligibility = memberships.findNonEnded(callerUserId, clubId)?.status.toBookingEligibility()
                val tier = (eligibility as? BookingEligibility.Eligible)?.tier
                    ?: throw ApiException.forbidden("This membership status cannot book")

                if (tier == BookingTier.GUEST && !settings.guestBookingEnabled) {
                    throw ApiException.forbidden("Guest booking is disabled for this club")
                }

                val now = clock.now()
                if (slot.range.startsAt < now) throw ApiException.validation("Cannot book a slot in the past")
                val today = now.toLocalDateTime(slot.timeZone).date
                val slotDate = slot.range.startsAt.toLocalDateTime(slot.timeZone).date
                val advanceDays = settings.advanceBookingDays[tier] ?: 0
                val daysAhead = slotDate.toEpochDays() - today.toEpochDays()
                if (daysAhead > advanceDays) {
                    throw ApiException(
                        HttpStatusCode.BadRequest,
                        ErrorCode.ADVANCE_WINDOW_EXCEEDED,
                        "$tier can only book up to $advanceDays day(s) in advance",
                    )
                }

                val price = resolveSlotPrice(
                    tier = tier,
                    memberDiscountPct = slot.court.memberDiscountPct,
                    pausedDiscountPct = slot.court.pausedDiscountPct,
                    rules = slot.rules,
                    defaultPriceCents = slot.court.defaultPriceCents,
                    slotDate = slotDate,
                    slotStartTime = slot.range.startTime,
                )

                val maxOpen = settings.maxOpenBookings[tier] ?: 0
                val openCount = bookings.countOpenForUser(callerUserId, clubId, now.toJavaInstant())
                if (openCount >= maxOpen) {
                    throw ApiException.conflict(ErrorCode.BOOKING_LIMIT_EXCEEDED, "Open booking limit reached ($maxOpen)")
                }
                val id = bookings.create(
                    clubId = clubId,
                    courtId = courtId,
                    userId = callerUserId,
                    startsAt = slot.range.startsAt.toJavaInstant(),
                    endsAt = slot.range.endsAt.toJavaInstant(),
                    status = BookingStatus.ACTIVE,
                    note = null,
                    basePriceCents = price.baseCents,
                    discountPct = price.discountPct,
                    finalPriceCents = price.finalCents,
                    paymentStatus = if (price.finalCents > 0) PaymentStatus.DUE else PaymentStatus.NONE,
                )
                bookings.findByIdAndClub(id, clubId)!!.toResponse()
            }
        } catch (e: Exception) {
            if (e.isExclusionViolation()) throw bookingConflict() else throw e
        }
    }

    /** Admin-only (route-gated): occupies one slot with no owner and no fee so members can't
     *  book it — court maintenance, training, etc. */
    suspend fun createBlock(clubId: Uuid, request: CreateBlockRequest): BookingResponse {
        val courtId = parseCourtId(request.courtId)
        val note = request.note?.trim()?.takeIf { it.isNotEmpty() }

        return try {
            withTransaction(db) {
                val slot = resolveCourtSlot(clubId, courtId, request.startsAt)
                val id = bookings.create(
                    clubId = clubId,
                    courtId = courtId,
                    userId = null,
                    startsAt = slot.range.startsAt.toJavaInstant(),
                    endsAt = slot.range.endsAt.toJavaInstant(),
                    status = BookingStatus.BLOCKED,
                    note = note,
                    basePriceCents = 0,
                    discountPct = 0,
                    finalPriceCents = 0,
                    paymentStatus = PaymentStatus.NONE,
                )
                bookings.findByIdAndClub(id, clubId)!!.toResponse()
            }
        } catch (e: Exception) {
            if (e.isExclusionViolation()) throw bookingConflict() else throw e
        }
    }

    /** Cancels a booking. A club ADMIN/OWNER may cancel (or un-block) any booking in their club,
     *  bypassing the cancellation window and always waiving a DUE fee; anyone else may only
     *  cancel their own ACTIVE booking, inside the club's cancellation window. */
    suspend fun cancel(callerUserId: Uuid, clubId: Uuid, bookingId: Uuid) {
        withTransaction(db) {
            val club = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
            val booking = bookings.findByIdAndClub(bookingId, clubId)
                ?.takeIf { it.status == BookingStatus.ACTIVE || it.status == BookingStatus.BLOCKED }
                ?: throw ApiException.notFound("Booking not found")

            val callerRole = memberships.findActive(callerUserId, clubId)?.role
            if (callerRole == MembershipRole.ADMIN || callerRole == MembershipRole.OWNER) {
                val newPaymentStatus = if (booking.paymentStatus == PaymentStatus.DUE) PaymentStatus.WAIVED else booking.paymentStatus
                val updated = bookings.cancelAny(bookingId, clubId, newPaymentStatus)
                if (updated == 0) throw ApiException.notFound("Booking not found")
                return@withTransaction
            }

            if (booking.status != BookingStatus.ACTIVE || booking.userId != callerUserId) {
                throw ApiException.notFound("Booking not found")
            }
            val settings = club.decodeSettings()
            val deadline = booking.startsAt.minus(Duration.ofHours(settings.cancellationWindowHours.toLong()))
            if (clock.now().toJavaInstant().isAfter(deadline)) {
                throw ApiException.conflict(ErrorCode.CANCELLATION_WINDOW_PASSED, "Too late to cancel this booking")
            }
            val newPaymentStatus = if (booking.paymentStatus == PaymentStatus.DUE) PaymentStatus.WAIVED else booking.paymentStatus
            val updated = bookings.cancel(bookingId, clubId, callerUserId, newPaymentStatus)
            if (updated == 0) throw ApiException.notFound("Booking not found")
        }
    }

    /** Admin-only (route-gated): records that a booking's fee was collected off-app or is being
     *  forgiven — payment is recorded, not processed, in v1. */
    suspend fun updatePayment(clubId: Uuid, bookingId: Uuid, paymentStatus: PaymentStatus): BookingResponse {
        if (paymentStatus != PaymentStatus.PAID && paymentStatus != PaymentStatus.WAIVED) {
            throw ApiException.validation("paymentStatus must be PAID or WAIVED")
        }
        return withTransaction(db) {
            bookings.findByIdAndClub(bookingId, clubId)
                ?.takeIf { it.userId != null } // a block carries no fee
                ?: throw ApiException.notFound("Booking not found")
            val updated = bookings.updatePayment(bookingId, clubId, paymentStatus)
            if (updated == 0) throw ApiException.notFound("Booking not found")
            bookings.findByIdAndClub(bookingId, clubId)!!.toResponse()
        }
    }

    /** Admin day schedule (route-gated to ADMIN): the day's occupying bookings (ACTIVE and
     *  BLOCKED) with their ids and booker names, so an admin can cancel/un-block/mark-paid —
     *  the public slots endpoint deliberately exposes none of that. The day boundary is
     *  club-local midnight-to-midnight, half-open `[from, to)`. */
    suspend fun listForCourtDay(clubId: Uuid, courtId: Uuid, date: LocalDate): List<CourtDayBookingResponse> =
        withTransaction(db) {
            val club = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
            courts.findByIdAndClub(courtId, clubId) ?: throw ApiException.notFound("Court not found")
            val timeZone = TimeZone.of(club.timezone)
            val from = LocalDateTime(date, LocalTime(0, 0)).toInstant(timeZone)
            val to = LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(timeZone)
            bookings.findOccupyingInRangeWithUser(courtId, from.toJavaInstant(), to.toJavaInstant())
                .map { CourtDayBookingResponse(booking = it.booking.toResponse(), userName = it.userName) }
        }

    /** GET /me/bookings — upcoming ACTIVE bookings owned by the caller across every club, soonest
     *  first. */
    suspend fun myBookings(callerUserId: Uuid, limit: Int, cursor: String?): Page<MyBookingResponse> {
        val keyset = parseKeysetCursor(cursor)
        val pageSize = pageSizeOf(limit)
        val rows = withTransaction(db) {
            bookings.listUpcomingForUser(callerUserId, now = clock.now().toJavaInstant(), limit = pageSize + 1, cursor = keyset)
        }
        return rows.toPage(pageSize, { it.booking.keyset }, BookingWithNamesRow::toResponse)
    }

    private fun parseCourtId(raw: String): Uuid =
        runCatching { Uuid.parse(raw) }.getOrNull() ?: throw ApiException.validation("Invalid courtId")

    private fun bookingConflict() =
        ApiException(HttpStatusCode.Conflict, ErrorCode.BOOKING_CONFLICT, "This slot was just booked")

}

private fun BookingWithNamesRow.toResponse() = MyBookingResponse(
    booking = booking.toResponse(),
    clubName = clubName,
    clubTimezone = clubTimezone,
    courtName = courtName,
)

private fun BookingRow.toResponse() = BookingResponse(
    id = id.toString(),
    clubId = clubId.toString(),
    courtId = courtId.toString(),
    userId = userId?.toString(),
    startsAt = startsAt.toKotlinInstant(),
    endsAt = endsAt.toKotlinInstant(),
    status = status,
    note = note,
    basePriceCents = basePriceCents,
    discountPct = discountPct,
    finalPriceCents = finalPriceCents,
    paymentStatus = paymentStatus,
)
