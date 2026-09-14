package com.kazox.aufschlag.api.club

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClubSettingsTest {

    @Test
    fun `defaults are valid`() {
        ClubSettings().validate()
    }

    @Test
    fun `a closed day is a present null entry, not a missing one`() {
        ClubSettings(openingHours = ClubSettings().openingHours + (DayOfWeek.SUNDAY to null)).validate()

        val error = assertFailsWith<IllegalArgumentException> {
            ClubSettings(openingHours = ClubSettings().openingHours - DayOfWeek.SUNDAY).validate()
        }
        assertTrue("SUNDAY" in error.message!!, error.message)
    }

    @Test
    fun `every booking tier needs an advance-days and open-bookings entry`() {
        assertFailsWith<IllegalArgumentException> {
            ClubSettings(advanceBookingDays = mapOf(BookingTier.MEMBER to 7)).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            ClubSettings(maxOpenBookings = emptyMap()).validate()
        }
    }

    @Test
    fun `value ranges are still checked`() {
        assertFailsWith<IllegalArgumentException> {
            ClubSettings(openingHours = ClubSettings().openingHours + (DayOfWeek.MONDAY to DayOpeningHours(LocalTime(20, 0), LocalTime(8, 0)))).validate()
        }
        assertFailsWith<IllegalArgumentException> { ClubSettings(cancellationWindowHours = 200).validate() }
    }
}
