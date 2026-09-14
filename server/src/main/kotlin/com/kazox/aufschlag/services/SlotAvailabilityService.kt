package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.booking.DayAvailabilityResponse
import com.kazox.aufschlag.api.booking.SlotResponse
import com.kazox.aufschlag.api.club.BookingTier
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.pricing.BookingEligibility
import com.kazox.aufschlag.pricing.resolveSlotPrice
import com.kazox.aufschlag.pricing.toBookingEligibility
import com.kazox.aufschlag.repositories.BookingRepository
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.CourtRepository
import com.kazox.aufschlag.repositories.MembershipRepository
import com.kazox.aufschlag.repositories.PriceRuleRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.toJavaInstant
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.Uuid

/**
 * Day view per court: free/taken slots with the price personalized to
 * the caller's own membership tier in this club (member/paused/guest — resolved fresh per
 * request, never cached, since a status change must be reflected immediately).
 */
class SlotAvailabilityService(
    private val db: Database,
    private val clubs: ClubRepository,
    private val courts: CourtRepository,
    private val priceRules: PriceRuleRepository,
    private val memberships: MembershipRepository,
    private val bookings: BookingRepository,
) {
    suspend fun slots(callerUserId: Uuid, clubId: Uuid, courtId: Uuid, date: LocalDate): DayAvailabilityResponse =
        withTransaction(db) {
            val club = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
            val court = courts.findByIdAndClub(courtId, clubId) ?: throw ApiException.notFound("Court not found")
            val rules = priceRules.findByCourt(courtId).map { it.toEngineRule() }
            val eligibility = memberships.findNonEnded(callerUserId, clubId)?.status.toBookingEligibility()

            // SUSPENDED members still see the schedule (read-only) — GUEST pricing is a reasonable
            // display fallback since they can't act on it either way.
            val tier = (eligibility as? BookingEligibility.Eligible)?.tier ?: BookingTier.GUEST

            val settings = club.decodeSettings()
            val canBook = eligibility != BookingEligibility.CannotBook &&
                (tier != BookingTier.GUEST || settings.guestBookingEnabled)
            val opening = settings.openingHours[date.dayOfWeek]
                ?: return@withTransaction DayAvailabilityResponse(date, canBook, emptyList())

            val timeZone = TimeZone.of(club.timezone)
            val slotRanges = slotRangesFor(date, opening, court.slotMinutes, timeZone)
            if (slotRanges.isEmpty()) return@withTransaction DayAvailabilityResponse(date, canBook, emptyList())

            // Same transaction as the court/rules read above, so the grid and its occupancy
            // are one consistent snapshot.
            val occupying = bookings.findOccupyingInRange(
                courtId,
                slotRanges.first().startsAt.toJavaInstant(),
                slotRanges.last().endsAt.toJavaInstant(),
            )

            val slots = slotRanges.map { range ->
                val startsAtJava = range.startsAt.toJavaInstant()
                val endsAtJava = range.endsAt.toJavaInstant()
                // An inactive court never offers slots — same rule BookingService enforces at
                // booking time ("Court is not accepting bookings").
                val unavailable = !court.active || occupying.any { it.startsAt < endsAtJava && it.endsAt > startsAtJava }
                val price = resolveSlotPrice(
                    tier = tier,
                    memberDiscountPct = court.memberDiscountPct,
                    pausedDiscountPct = court.pausedDiscountPct,
                    rules = rules,
                    defaultPriceCents = court.defaultPriceCents,
                    slotDate = date,
                    slotStartTime = range.startTime,
                )
                SlotResponse(startsAt = range.startsAt, endsAt = range.endsAt, available = !unavailable, priceCents = price.finalCents)
            }

            DayAvailabilityResponse(date, canBook, slots)
        }
}
