package com.kazox.aufschlag.pricing

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Matching-relevant shape of a court's price rule (PLANNING.md "Booking pricing"). Deliberately
 * has no `id`/`courtId` — those are wire/DB concerns; this is the pure value the engine matches
 * against. Half-open [startTime, endTime); non-overlap within overlapping validity is a
 * write-time invariant enforced by the wholesale-replace endpoint, not by this type.
 */
data class PriceRule(
    val daysOfWeek: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val priceCents: Int,
)

/**
 * The rule containing the slot's START time wins — rules are admin-entered arbitrary times, so
 * a slot CAN straddle a rule boundary; start-time matching is the defined tiebreak, not an edge
 * case. Relies on the write-time non-overlap invariant for a well-defined result: a correctly
 * validated rule set has at most one match for any (date, time).
 */
fun matchPriceRule(rules: List<PriceRule>, slotDate: LocalDate, slotStartTime: LocalTime): PriceRule? {
    val dayOfWeek = slotDate.dayOfWeek
    return rules.firstOrNull { rule ->
        dayOfWeek in rule.daysOfWeek &&
            slotStartTime >= rule.startTime && slotStartTime < rule.endTime &&
            (rule.validFrom == null || slotDate >= rule.validFrom) &&
            (rule.validTo == null || slotDate <= rule.validTo)
    }
}

/**
 * True if any two rules in the set share a day-of-week, an overlapping time range, and an
 * overlapping validity window. [matchPriceRule] only returns a well-defined single match when
 * this is false — the wholesale-replace endpoint validates the invariant with this before
 * writing a court's rule set.
 */
fun hasOverlap(rules: List<PriceRule>): Boolean {
    for (i in rules.indices) {
        for (j in i + 1 until rules.size) {
            val a = rules[i]
            val b = rules[j]
            val sharedDay = a.daysOfWeek.any { it in b.daysOfWeek }
            val timeOverlap = a.startTime < b.endTime && b.startTime < a.endTime
            val validityOverlap = validityRangesOverlap(a.validFrom, a.validTo, b.validFrom, b.validTo)
            if (sharedDay && timeOverlap && validityOverlap) return true
        }
    }
    return false
}

private fun validityRangesOverlap(aFrom: LocalDate?, aTo: LocalDate?, bFrom: LocalDate?, bTo: LocalDate?): Boolean {
    val aStartsBeforeBEnds = aFrom == null || bTo == null || aFrom <= bTo
    val bStartsBeforeAEnds = bFrom == null || aTo == null || bFrom <= aTo
    return aStartsBeforeBEnds && bStartsBeforeAEnds
}

/** ISO day number, 1=Monday..7=Sunday — matches the `price_rules.days_of_week` DB column
 *  convention. Plain functions rather than an extension property to avoid any ambiguity with
 *  a same-named member on a given target's [DayOfWeek] implementation. */
fun isoDayNumberOf(day: DayOfWeek): Int = day.ordinal + 1

fun dayOfWeekFromIsoNumber(isoDayNumber: Int): DayOfWeek {
    require(isoDayNumber in 1..7) { "isoDayNumber must be 1-7, was $isoDayNumber" }
    return DayOfWeek.entries[isoDayNumber - 1]
}
