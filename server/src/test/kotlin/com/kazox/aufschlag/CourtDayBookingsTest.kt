package com.kazox.aufschlag

import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.booking.BookingResponse
import com.kazox.aufschlag.api.booking.BookingStatus
import com.kazox.aufschlag.api.booking.CourtDayBookingResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.court.CourtResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** GET /clubs/{clubId}/courts/{courtId}/bookings?date= — the admin day schedule
 *  (docs/admin-webapp.md §1.4): unlike the public slots endpoint it exposes booking
 *  ids/owners/payment so an admin can cancel, un-block, and mark fees paid. */
class CourtDayBookingsTest {

    private val timeZone = TimeZone.of("Europe/Berlin")

    private suspend fun HttpClient.newClubWithCourt(): Triple<ClubResponse, String, CourtResponse> {
        val adminEmail = uniqueEmail("admin")
        val admin = register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = register(ownerEmail).body<TokenPairResponse>()
        val club = createClub(admin.accessToken, "Schedule Club", uniqueSlug(), ownerEmail, timezone = "Europe/Berlin")
            .body<ClubResponse>()
        val court = createCourt(owner.accessToken, club.id).body<CourtResponse>()
        return Triple(club, owner.accessToken, court)
    }

    @Test
    fun `admin sees the day's active bookings and blocks with ids and booker names`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val bookingStart = dynamicSlotStart(timeZone, daysFromToday = 1, hour = 10)
        val blockStart = dynamicSlotStart(timeZone, daysFromToday = 1, hour = 11)
        val booking = client.createBooking(ownerToken, club.id, court.id, bookingStart).body<BookingResponse>()
        val block = client.createBlock(ownerToken, club.id, court.id, blockStart, note = "Training").body<BookingResponse>()

        val date = bookingStart.toLocalDateTime(timeZone).date.toString()
        val response = client.courtDayBookings(ownerToken, club.id, court.id, date)
        assertEquals(HttpStatusCode.OK, response.status)
        val rows = response.body<List<CourtDayBookingResponse>>()

        val bookingRow = rows.single { it.booking.id == booking.id }
        assertEquals(BookingStatus.ACTIVE, bookingRow.booking.status)
        assertEquals("Test User", bookingRow.userName)

        val blockRow = rows.single { it.booking.id == block.id }
        assertEquals(BookingStatus.BLOCKED, blockRow.booking.status)
        assertEquals("Training", blockRow.booking.note)
        assertNull(blockRow.userName)

        // Ordered by start; bookings on other days don't leak in.
        assertEquals(listOf(booking.id, block.id), rows.map { it.booking.id })
    }

    @Test
    fun `an empty day returns an empty list`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val date = dynamicSlotStart(timeZone, daysFromToday = 5, hour = 10).toLocalDateTime(timeZone).date.toString()
        val rows = client.courtDayBookings(ownerToken, club.id, court.id, date).body<List<CourtDayBookingResponse>>()
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `a plain member cannot read the day schedule`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)

        val date = dynamicSlotStart(timeZone, 1, 10).toLocalDateTime(timeZone).date.toString()
        val response = client.courtDayBookings(member.accessToken, club.id, court.id, date)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `an admin of club A cannot read club B's court schedule`() = authTestApp { client ->
        val (clubA, ownerAToken, _) = client.newClubWithCourt()
        val (_, _, courtB) = client.newClubWithCourt()

        // Under club A's path, naming club B's court — tenant-scoped court lookup 404s.
        val date = dynamicSlotStart(timeZone, 1, 10).toLocalDateTime(timeZone).date.toString()
        val response = client.courtDayBookings(ownerAToken, clubA.id, courtB.id, date)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
