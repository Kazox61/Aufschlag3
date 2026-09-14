package com.kazox.aufschlag.pricing

import com.kazox.aufschlag.api.club.BookingTier
import com.kazox.aufschlag.api.club.MembershipStatus
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PricingTest {

    private fun rule(
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        start: LocalTime = LocalTime(8, 0),
        end: LocalTime = LocalTime(16, 0),
        validFrom: LocalDate? = null,
        validTo: LocalDate? = null,
        priceCents: Int = 1200,
    ) = PriceRule(days, start, end, validFrom, validTo, priceCents)

    // 2026-01-05 is a Monday.
    private val monday = LocalDate(2026, 1, 5)

    @Test
    fun `matches a rule covering the slot's day and start time`() {
        val matched = matchPriceRule(listOf(rule()), monday, LocalTime(9, 0))
        assertEquals(1200, matched?.priceCents)
    }

    @Test
    fun `start time is inclusive, end time is exclusive (half-open range)`() {
        val r = rule(start = LocalTime(8, 0), end = LocalTime(16, 0))
        assertEquals(r, matchPriceRule(listOf(r), monday, LocalTime(8, 0)))
        assertNull(matchPriceRule(listOf(r), monday, LocalTime(16, 0)))
    }

    @Test
    fun `slot outside the rule's day of week does not match`() {
        val tuesday = LocalDate(2026, 1, 6) // the day after `monday`
        val r = rule(days = setOf(DayOfWeek.MONDAY))
        assertNull(matchPriceRule(listOf(r), tuesday, LocalTime(9, 0)))
    }

    @Test
    fun `validFrom and validTo bounds are inclusive`() {
        val r = rule(validFrom = LocalDate(2026, 1, 1), validTo = LocalDate(2026, 1, 5))
        assertEquals(r, matchPriceRule(listOf(r), LocalDate(2026, 1, 5), LocalTime(9, 0)))
        assertNull(matchPriceRule(listOf(r), LocalDate(2026, 1, 6), LocalTime(9, 0)))
        assertNull(matchPriceRule(listOf(r), LocalDate(2025, 12, 31), LocalTime(9, 0)))
    }

    @Test
    fun `open-ended validity (null validFrom-validTo) matches any date`() {
        val r = rule(days = DayOfWeek.entries.toSet(), validFrom = null, validTo = null)
        assertEquals(r, matchPriceRule(listOf(r), LocalDate(2030, 6, 1), LocalTime(9, 0)))
    }

    @Test
    fun `no matching rule returns null`() {
        val r = rule(days = setOf(DayOfWeek.SATURDAY))
        assertNull(matchPriceRule(listOf(r), monday, LocalTime(9, 0)))
    }

    @Test
    fun `first matching rule wins when the write-time non-overlap invariant holds`() {
        val morning = rule(start = LocalTime(8, 0), end = LocalTime(12, 0), priceCents = 1000)
        val afternoon = rule(start = LocalTime(12, 0), end = LocalTime(20, 0), priceCents = 2000)
        assertEquals(1000, matchPriceRule(listOf(morning, afternoon), monday, LocalTime(9, 0))?.priceCents)
        assertEquals(2000, matchPriceRule(listOf(morning, afternoon), monday, LocalTime(12, 0))?.priceCents)
    }

    @Test
    fun `rounding is half-up`() {
        assertEquals(900, roundedPriceCents(1200, 25)) // exact: 900.0
        assertEquals(7, roundedPriceCents(13, 50)) // 6.5 -> 7
        assertEquals(1200, roundedPriceCents(1200, 0)) // no discount
        assertEquals(0, roundedPriceCents(1200, 100)) // free
    }

    @Test
    fun `rounding rejects an out-of-range discount`() {
        assertFailsWith<IllegalArgumentException> { roundedPriceCents(1200, 101) }
        assertFailsWith<IllegalArgumentException> { roundedPriceCents(1200, -1) }
    }

    @Test
    fun `discount pct for tier - guest is always 0 regardless of court config`() {
        assertEquals(100, discountPctForTier(BookingTier.MEMBER, memberDiscountPct = 100, pausedDiscountPct = 30))
        assertEquals(30, discountPctForTier(BookingTier.PAUSED, memberDiscountPct = 100, pausedDiscountPct = 30))
        assertEquals(0, discountPctForTier(BookingTier.GUEST, memberDiscountPct = 100, pausedDiscountPct = 30))
    }

    @Test
    fun `resolveSlotPrice falls back to the court default when no rule matches`() {
        val resolved = resolveSlotPrice(
            tier = BookingTier.MEMBER,
            memberDiscountPct = 50,
            pausedDiscountPct = 20,
            rules = listOf(rule(days = setOf(DayOfWeek.SATURDAY))),
            defaultPriceCents = 800,
            slotDate = monday,
            slotStartTime = LocalTime(9, 0),
        )
        assertEquals(ResolvedPrice(baseCents = 800, discountPct = 50, finalCents = 400), resolved)
    }

    @Test
    fun `resolveSlotPrice uses the matched rule's price as the base`() {
        val resolved = resolveSlotPrice(
            tier = BookingTier.PAUSED,
            memberDiscountPct = 100,
            pausedDiscountPct = 30,
            rules = listOf(rule(priceCents = 2400)),
            defaultPriceCents = 800,
            slotDate = monday,
            slotStartTime = LocalTime(9, 0),
        )
        assertEquals(ResolvedPrice(baseCents = 2400, discountPct = 30, finalCents = 1680), resolved)
    }

    @Test
    fun `no overlap - disjoint time ranges on the same day`() {
        val morning = rule(start = LocalTime(8, 0), end = LocalTime(12, 0))
        val afternoon = rule(start = LocalTime(12, 0), end = LocalTime(20, 0)) // back-to-back, not overlapping
        assertEquals(false, hasOverlap(listOf(morning, afternoon)))
    }

    @Test
    fun `no overlap - same time range but disjoint days`() {
        val mon = rule(days = setOf(DayOfWeek.MONDAY))
        val tue = rule(days = setOf(DayOfWeek.TUESDAY))
        assertEquals(false, hasOverlap(listOf(mon, tue)))
    }

    @Test
    fun `no overlap - same day and time but disjoint validity windows`() {
        val season1 = rule(validFrom = LocalDate(2026, 1, 1), validTo = LocalDate(2026, 3, 31))
        val season2 = rule(validFrom = LocalDate(2026, 4, 1), validTo = LocalDate(2026, 6, 30))
        assertEquals(false, hasOverlap(listOf(season1, season2)))
    }

    @Test
    fun `overlap - shared day, overlapping time, both open-ended validity`() {
        val a = rule(start = LocalTime(8, 0), end = LocalTime(14, 0))
        val b = rule(start = LocalTime(13, 0), end = LocalTime(20, 0))
        assertEquals(true, hasOverlap(listOf(a, b)))
    }

    @Test
    fun `overlap - one rule open-ended, the other seasonal, windows intersect`() {
        val alwaysOn = rule(validFrom = null, validTo = null)
        val summerOnly = rule(validFrom = LocalDate(2026, 6, 1), validTo = LocalDate(2026, 8, 31))
        assertEquals(true, hasOverlap(listOf(alwaysOn, summerOnly)))
    }

    @Test
    fun `overlap - multi-day rules sharing only one day still count`() {
        val weekdays = rule(days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY))
        val weekend = rule(days = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))
        assertEquals(true, hasOverlap(listOf(weekdays, weekend)))
    }

    @Test
    fun `empty and single-rule sets never overlap`() {
        assertEquals(false, hasOverlap(emptyList()))
        assertEquals(false, hasOverlap(listOf(rule())))
    }

    @Test
    fun `iso day number round-trips for every day of the week`() {
        DayOfWeek.entries.forEach { day ->
            assertEquals(day, dayOfWeekFromIsoNumber(isoDayNumberOf(day)))
        }
        assertEquals(1, isoDayNumberOf(DayOfWeek.MONDAY))
        assertEquals(7, isoDayNumberOf(DayOfWeek.SUNDAY))
    }

    @Test
    fun `booking eligibility - exhaustive status to tier mapping`() {
        assertEquals(
            BookingEligibility.Eligible(BookingTier.MEMBER),
            MembershipStatus.ACTIVE.toBookingEligibility(),
        )
        assertEquals(
            BookingEligibility.Eligible(BookingTier.PAUSED),
            MembershipStatus.PAUSED.toBookingEligibility(),
        )
        assertEquals(
            BookingEligibility.Eligible(BookingTier.GUEST),
            MembershipStatus.PENDING.toBookingEligibility(),
        )
        assertEquals(
            BookingEligibility.Eligible(BookingTier.GUEST),
            MembershipStatus.ENDED.toBookingEligibility(),
        )
        assertEquals(BookingEligibility.Eligible(BookingTier.GUEST), null.toBookingEligibility())
        assertEquals(BookingEligibility.CannotBook, MembershipStatus.SUSPENDED.toBookingEligibility())
    }

    @Test
    fun `slot start minutes - default hours produce 14 hourly slots`() {
        assertEquals((480..1260 step 60).toList(), slotStartMinutes(openMinute = 480, closeMinute = 1320, slotMinutes = 60))
    }

    @Test
    fun `slot start minutes - a slot that would run past closing is excluded`() {
        assertEquals(listOf(480, 510), slotStartMinutes(openMinute = 480, closeMinute = 555, slotMinutes = 30))
    }

    @Test
    fun `slot start minutes - a window shorter than one slot yields no slots`() {
        assertEquals(emptyList(), slotStartMinutes(openMinute = 480, closeMinute = 500, slotMinutes = 60))
    }
}
