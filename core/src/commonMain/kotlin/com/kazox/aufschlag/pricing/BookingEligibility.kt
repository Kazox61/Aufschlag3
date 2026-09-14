package com.kazox.aufschlag.pricing

import com.kazox.aufschlag.api.club.BookingTier
import com.kazox.aufschlag.api.club.MembershipStatus

/**
 * Status → pricing tier mapping (exhaustive, PLANNING.md "Booking pricing"): ACTIVE → member,
 * PAUSED → paused, everything else (PENDING, ENDED, no membership) → guest. SUSPENDED cannot
 * book at all — modeled as a first-class outcome rather than an exception, since it's a normal
 * business state, not an error.
 */
sealed interface BookingEligibility {
    data class Eligible(val tier: BookingTier) : BookingEligibility
    data object CannotBook : BookingEligibility
}

fun MembershipStatus?.toBookingEligibility(): BookingEligibility = when (this) {
    MembershipStatus.ACTIVE -> BookingEligibility.Eligible(BookingTier.MEMBER)
    MembershipStatus.PAUSED -> BookingEligibility.Eligible(BookingTier.PAUSED)
    MembershipStatus.PENDING, MembershipStatus.ENDED, null -> BookingEligibility.Eligible(BookingTier.GUEST)
    MembershipStatus.SUSPENDED -> BookingEligibility.CannotBook
}
