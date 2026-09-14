package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.booking.BookingResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.court.CourtResponse
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteAccountTest {

    @Test
    fun `delete with wrong password is rejected`() = authTestApp { client ->
        val email = uniqueEmail("delete")
        val tokens = client.register(email, password = "correct-horse-1").body<TokenPairResponse>()

        val response = client.deleteAccount(tokens.accessToken, "wrong-password-1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)

        // account must still exist
        assertEquals(HttpStatusCode.OK, client.me(tokens.accessToken).status)
    }

    @Test
    fun `delete with correct password removes the account`() = authTestApp { client ->
        val email = uniqueEmail("delete")
        val tokens = client.register(email, password = "correct-horse-1").body<TokenPairResponse>()

        val response = client.deleteAccount(tokens.accessToken, "correct-horse-1")
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(HttpStatusCode.Unauthorized, client.me(tokens.accessToken).status)

        // can register the same email again — the account is really gone, not just marked
        assertEquals(HttpStatusCode.Created, client.register(email, password = "correct-horse-1").status)
    }

    @Test
    fun `sole owner of a non-archived club cannot delete their account`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = client.register(ownerEmail, password = "correct-horse-1").body<TokenPairResponse>()
        client.createClub(admin.accessToken, "Sole Owner Club", uniqueSlug(), ownerEmail)

        val response = client.deleteAccount(owner.accessToken, "correct-horse-1")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ErrorCode.FORBIDDEN, response.body<ApiError>().code)
    }

    @Test
    fun `owner of an archived club can delete their account`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = client.register(ownerEmail, password = "correct-horse-1").body<TokenPairResponse>()
        val club = client.createClub(admin.accessToken, "Archived Owner Club", uniqueSlug(), ownerEmail)
            .body<ClubResponse>()
        setClubStatus(club.id, "ARCHIVED")

        val response = client.deleteAccount(owner.accessToken, "correct-horse-1")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `deleting an account cancels its future active bookings so they don't hold the court forever`() =
        authTestApp { client ->
            val timeZone = TimeZone.of("Europe/Berlin")
            val adminEmail = uniqueEmail("admin")
            val admin = client.register(adminEmail).body<TokenPairResponse>()
            makeSuperAdmin(adminEmail)
            val ownerEmail = uniqueEmail("owner")
            val owner = client.register(ownerEmail).body<TokenPairResponse>()
            val club = client.createClub(admin.accessToken, "Delete Booking Club", uniqueSlug(), ownerEmail, timezone = "Europe/Berlin")
                .body<ClubResponse>()
            val court = client.createCourt(owner.accessToken, club.id, defaultPriceCents = 1200, memberDiscountPct = 100)
                .body<CourtResponse>()

            val memberEmail = uniqueEmail("member")
            val member = client.register(memberEmail, password = "correct-horse-1").body<TokenPairResponse>()
            val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
            client.decideApplication(owner.accessToken, club.id, application.id, approve = true)
            val booking = client.createBooking(member.accessToken, club.id, court.id, dynamicSlotStart(timeZone, 3, 10))
                .body<BookingResponse>()

            val response = client.deleteAccount(member.accessToken, "correct-horse-1")
            assertEquals(HttpStatusCode.NoContent, response.status)

            assertEquals("CANCELLED" to "NONE", bookingState(booking.id))
        }
}
