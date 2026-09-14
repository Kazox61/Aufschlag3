package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.booking.DayAvailabilityResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.court.CourtResponse
import com.kazox.aufschlag.api.court.PriceRuleRequest
import com.kazox.aufschlag.api.court.ReplacePriceRulesRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotAvailabilityTest {

    // 2026-08-03 is a Monday.
    private val monday = "2026-08-03"

    private suspend fun HttpClient.newClubWithCourt(
        defaultPriceCents: Int = 1200,
        memberDiscountPct: Int = 100,
    ): Triple<ClubResponse, String, CourtResponse> {
        val adminEmail = uniqueEmail("admin")
        val admin = register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = register(ownerEmail).body<TokenPairResponse>()
        val club = createClub(admin.accessToken, "Slots Club", uniqueSlug(), ownerEmail).body<ClubResponse>()
        val court = createCourt(
            owner.accessToken,
            club.id,
            defaultPriceCents = defaultPriceCents,
            memberDiscountPct = memberDiscountPct,
        ).body<CourtResponse>()
        return Triple(club, owner.accessToken, court)
    }

    @Test
    fun `default opening hours produce 14 hourly slots, free for the owner (100pct member discount)`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()

        val response = client.getSlots(ownerToken, club.id, court.id, monday)
        assertEquals(HttpStatusCode.OK, response.status)
        val day = response.body<DayAvailabilityResponse>()
        assertTrue(day.canBook)
        assertEquals(14, day.slots.size) // 08:00-22:00 in 60-minute slots
        assertTrue(day.slots.all { it.available })
        assertTrue(day.slots.all { it.priceCents == 0 }) // 100% member discount
    }

    @Test
    fun `a non-member guest sees the same slots at the full undiscounted price`() = authTestApp { client ->
        val (club, _, court) = client.newClubWithCourt(defaultPriceCents = 1200, memberDiscountPct = 100)
        val guest = client.register(uniqueEmail("guest")).body<TokenPairResponse>()

        val response = client.getSlots(guest.accessToken, club.id, court.id, monday)
        assertEquals(HttpStatusCode.OK, response.status)
        val day = response.body<DayAvailabilityResponse>()
        assertTrue(day.canBook)
        assertTrue(day.slots.all { it.priceCents == 1200 }) // guest tier: 0% discount, always
    }

    @Test
    fun `an occupied slot is marked unavailable, adjacent slots stay free`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        // 10:00-11:00 booked
        insertBooking(club.id, court.id, "${monday}T08:00:00Z", "${monday}T09:00:00Z")

        val day = client.getSlots(ownerToken, club.id, court.id, monday).body<DayAvailabilityResponse>()
        val bookedSlot = day.slots.first { it.startsAt.toString() == "${monday}T08:00:00Z" }
        assertFalse(bookedSlot.available)
        val neighbours = day.slots.filter { it.startsAt.toString() != "${monday}T08:00:00Z" }
        assertTrue(neighbours.all { it.available })
    }

    @Test
    fun `a BLOCKED admin booking also occupies the slot`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        insertBooking(club.id, court.id, "${monday}T09:00:00Z", "${monday}T10:00:00Z", status = "BLOCKED")

        val day = client.getSlots(ownerToken, club.id, court.id, monday).body<DayAvailabilityResponse>()
        val blockedSlot = day.slots.first { it.startsAt.toString() == "${monday}T09:00:00Z" }
        assertFalse(blockedSlot.available)
    }

    @Test
    fun `a CANCELLED booking does not occupy the slot`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        insertBooking(club.id, court.id, "${monday}T09:00:00Z", "${monday}T10:00:00Z", status = "CANCELLED")

        val day = client.getSlots(ownerToken, club.id, court.id, monday).body<DayAvailabilityResponse>()
        val slot = day.slots.first { it.startsAt.toString() == "${monday}T09:00:00Z" }
        assertTrue(slot.available)
    }

    @Test
    fun `a closed day yields an empty slot list`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        // Monday closed, every other day open 08:00-22:00 (mirrors ClubSettings defaults otherwise).
        val settingsJson = """
            {"openingHours":{"MONDAY":null,"TUESDAY":{"opensAt":"08:00:00","closesAt":"22:00:00"},
            "WEDNESDAY":{"opensAt":"08:00:00","closesAt":"22:00:00"},"THURSDAY":{"opensAt":"08:00:00","closesAt":"22:00:00"},
            "FRIDAY":{"opensAt":"08:00:00","closesAt":"22:00:00"},"SATURDAY":{"opensAt":"08:00:00","closesAt":"22:00:00"},
            "SUNDAY":{"opensAt":"08:00:00","closesAt":"22:00:00"}}}
        """.trimIndent()
        setClubSettings(club.id, settingsJson)

        val day = client.getSlots(ownerToken, club.id, court.id, monday).body<DayAvailabilityResponse>()
        assertTrue(day.slots.isEmpty())
    }

    @Test
    fun `a price rule overrides the default price for the slots it covers`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt(defaultPriceCents = 1200, memberDiscountPct = 0)
        client.replacePriceRules(
            ownerToken,
            club.id,
            court.id,
            ReplacePriceRulesRequest(
                listOf(
                    PriceRuleRequest(
                        daysOfWeek = setOf(DayOfWeek.MONDAY),
                        startTime = LocalTime(18, 0),
                        endTime = LocalTime(22, 0),
                        priceCents = 2400,
                    ),
                ),
            ),
        )

        val day = client.getSlots(ownerToken, club.id, court.id, monday).body<DayAvailabilityResponse>()
        val eveningSlot = day.slots.first { it.startsAt.toString() == "${monday}T18:00:00Z" }
        val morningSlot = day.slots.first { it.startsAt.toString() == "${monday}T08:00:00Z" }
        assertEquals(2400, eveningSlot.priceCents)
        assertEquals(1200, morningSlot.priceCents)
    }

    @Test
    fun `a malformed date is a 400, not a 500`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val response = client.getSlots(ownerToken, club.id, court.id, "2026-13-45")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ApiError>().code)
    }

    @Test
    fun `unknown court returns 404`() = authTestApp { client ->
        val (club, ownerToken, _) = client.newClubWithCourt()
        val response = client.getSlots(ownerToken, club.id, kotlin.uuid.Uuid.random().toString(), monday)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `suspended membership can still view the schedule but canBook is false`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)
        // no admin endpoint to suspend a member yet — flip it directly like other tests do
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE memberships SET status = 'SUSPENDED' WHERE id = ?::uuid").use {
                it.setString(1, application.id)
                it.executeUpdate()
            }
            connection.commit()
        }

        val day = client.getSlots(member.accessToken, club.id, court.id, monday).body<DayAvailabilityResponse>()
        assertFalse(day.canBook)
        assertTrue(day.slots.isNotEmpty())
    }
}
