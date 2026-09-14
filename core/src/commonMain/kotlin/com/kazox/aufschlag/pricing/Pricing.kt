package com.kazox.aufschlag.pricing

import com.kazox.aufschlag.api.club.BookingTier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** The price breakdown snapshotted onto a booking — receipts stay explainable even if rules
 *  change later (PLANNING.md "Booking pricing"). */
@Serializable
data class ResolvedPrice(
    val baseCents: Int,
    val discountPct: Int,
    val finalCents: Int,
)

/** `round(baseCents × (100 − discountPct) / 100)`, half-up. Defined once so server and client
 *  previews can never disagree by a cent. */
fun roundedPriceCents(baseCents: Int, discountPct: Int): Int {
    require(discountPct in 0..100) { "discountPct must be 0-100, was $discountPct" }
    require(baseCents >= 0) { "baseCents must be >= 0, was $baseCents" }
    val numerator = baseCents.toLong() * (100 - discountPct)
    // half-up rounding of a non-negative numerator/100 via floor((numerator + 50) / 100)
    return ((numerator + 50) / 100).toInt()
}

/** Guest tier carries no stored discount — clubs price guests at 100% of the base rate. */
fun discountPctForTier(tier: BookingTier, memberDiscountPct: Int, pausedDiscountPct: Int): Int = when (tier) {
    BookingTier.MEMBER -> memberDiscountPct
    BookingTier.PAUSED -> pausedDiscountPct
    BookingTier.GUEST -> 0
}

/**
 * `slot price = base price (court, weekday, hour) × (1 − status discount %)`, resolved for one
 * slot and one caller. [rules] should already be scoped to a single court.
 */
fun resolveSlotPrice(
    tier: BookingTier,
    memberDiscountPct: Int,
    pausedDiscountPct: Int,
    rules: List<PriceRule>,
    defaultPriceCents: Int,
    slotDate: LocalDate,
    slotStartTime: LocalTime,
): ResolvedPrice {
    val baseCents = matchPriceRule(rules, slotDate, slotStartTime)?.priceCents ?: defaultPriceCents
    val discountPct = discountPctForTier(tier, memberDiscountPct, pausedDiscountPct)
    return ResolvedPrice(baseCents, discountPct, roundedPriceCents(baseCents, discountPct))
}
