package com.kazox.aufschlag.repositories

import java.time.Instant
import kotlin.uuid.Uuid

data class BookingRow(
    val id: Uuid,
    val clubId: Uuid,
    val courtId: Uuid,
    val userId: Uuid?,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: String,
    val note: String?,
    val basePriceCents: Int,
    val discountPct: Int,
    val finalPriceCents: Int,
    val paymentStatus: String,
)

/** [BookingRow] joined with its club/court display names — GET /me/bookings. */
data class BookingWithNamesRow(
    val booking: BookingRow,
    val clubName: String,
    val clubTimezone: String,
    val courtName: String,
)

/** [BookingRow] joined with the booker's display name — the admin day schedule.
 *  [userName] is null for blocks (no owner) and anonymized/deleted accounts. */
data class BookingWithUserRow(
    val booking: BookingRow,
    val userName: String?,
)

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface BookingRepository {
    /** ACTIVE or BLOCKED bookings for [courtId] whose range intersects `[from, to)` — used to
     *  render slot availability (occupied vs. free). */
    fun findOccupyingInRange(courtId: Uuid, from: Instant, to: Instant): List<BookingRow>

    /** [findOccupyingInRange] joined (left, blocks have no owner) with the booker's name,
     *  ordered by start — the admin day schedule (GET …/courts/{courtId}/bookings?date=). */
    fun findOccupyingInRangeWithUser(courtId: Uuid, from: Instant, to: Instant): List<BookingWithUserRow>

    /** Inserts a booking row — [userId] is null and [status] is `BLOCKED` for an admin block,
     *  otherwise a normal caller-owned booking. Relies on the DB exclusion constraint (SQLState
     *  `23P01`) to reject a conflicting slot — callers must not pre-check for conflicts themselves. */
    fun create(
        clubId: Uuid,
        courtId: Uuid,
        userId: Uuid?,
        startsAt: Instant,
        endsAt: Instant,
        status: String,
        note: String?,
        basePriceCents: Int,
        discountPct: Int,
        finalPriceCents: Int,
        paymentStatus: String,
    ): Uuid

    fun findByIdAndClub(id: Uuid, clubId: Uuid): BookingRow?

    /** ACTIVE bookings owned by [userId] in [clubId] that haven't ended yet — the "open bookings"
     *  count the per-status limit is checked against. */
    fun countOpenForUser(userId: Uuid, clubId: Uuid, now: Instant): Int

    /** Cancels [id] iff it's ACTIVE and owned by [ownerId] — never someone else's booking.
     *  Returns rows updated (0 if not found/not owned/not ACTIVE). */
    fun cancel(id: Uuid, clubId: Uuid, ownerId: Uuid, paymentStatus: String): Int

    /** Admin override of [cancel]: any ACTIVE or BLOCKED booking in the club, regardless of
     *  owner — also how an admin un-blocks a slot. Returns rows updated. */
    fun cancelAny(id: Uuid, clubId: Uuid, paymentStatus: String): Int

    /** Admin marks a real booking's fee PAID/WAIVED (blocks carry no fee, so [id] must have a
     *  non-null owner). Returns rows updated. */
    fun updatePayment(id: Uuid, clubId: Uuid, paymentStatus: String): Int

    /** Cancels every ACTIVE, not-yet-ended booking owned by [userId], across all clubs — the
     *  GDPR-deletion step that stops a deleted account's bookings from holding courts forever
     *  (docs/milestone-1-auth.md). A DUE fee is waived, same as any other cancellation. */
    fun cancelAllFutureForUser(userId: Uuid, now: Instant): Int

    /** ACTIVE bookings owned by [userId] across every club, not yet ended, ordered `startsAt ASC,
     *  id ASC` — the my-bookings screen (GET /me/bookings). [cursorStartsAt]/[cursorId] together
     *  form a keyset cursor (`(startsAt > cursorStartsAt) OR (startsAt = cursorStartsAt AND id >
     *  cursorId)`) — booking ids are random UUIDv4s, so unlike other list endpoints' id-only
     *  cursor, ordering by id alone here wouldn't be chronological. */
    fun listUpcomingForUser(
        userId: Uuid,
        now: Instant,
        limit: Int,
        cursorStartsAt: Instant?,
        cursorId: Uuid?,
    ): List<BookingWithNamesRow>

    /** `pg_advisory_xact_lock` scoped to [userId] alone (not per-club) — held for the rest of the
     *  current transaction. Serializes concurrent booking attempts by the same caller (across all
     *  their clubs) so the open-bookings-limit count-then-insert isn't racy
     *  (docs/milestone-1-auth.md), and — because it's per-user rather than per-(user, club) — also
     *  mutually excludes against account deletion (AuthService.deleteAccount takes the same lock
     *  before its cancel-scan-then-delete), so a booking created in that gap can't survive
     *  deletion as an anonymized "ghost" booking. */
    fun lockForBookingLimitCheck(userId: Uuid)
}
