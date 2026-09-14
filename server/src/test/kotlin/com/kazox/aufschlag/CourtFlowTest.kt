package com.kazox.aufschlag

import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.court.CourtResponse
import com.kazox.aufschlag.api.court.CourtSurface
import com.kazox.aufschlag.api.court.UpdateCourtRequest
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourtFlowTest {

    private suspend fun io.ktor.client.HttpClient.newClub(): Pair<ClubResponse, String> {
        val adminEmail = uniqueEmail("admin")
        val admin = register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = register(ownerEmail).body<TokenPairResponse>()
        val club = createClub(admin.accessToken, "Court Club", uniqueSlug(), ownerEmail).body<ClubResponse>()
        return club to owner.accessToken
    }

    @Test
    fun `member can create a court and it appears in the list`() = authTestApp { client ->
        val (club, ownerToken) = client.newClub()

        val created = client.createCourt(ownerToken, club.id, name = "Center Court", surface = CourtSurface.HARD)
        assertEquals(HttpStatusCode.Created, created.status)
        val court = created.body<CourtResponse>()
        assertEquals("Center Court", court.name)
        assertEquals(CourtSurface.HARD, court.surface)
        assertTrue(court.active)

        val listed = client.listCourts(ownerToken, club.id).body<com.kazox.aufschlag.api.Page<CourtResponse>>()
        assertTrue(listed.items.any { it.id == court.id })
    }

    @Test
    fun `non-member cannot create a court`() = authTestApp { client ->
        val (club, _) = client.newClub()
        val outsider = client.register(uniqueEmail("outsider")).body<TokenPairResponse>()

        val response = client.createCourt(outsider.accessToken, club.id)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `plain member cannot create a court, only admin can`() = authTestApp { client ->
        val (club, ownerToken) = client.newClub()
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = client.applyToClub(member.accessToken, club.id).body<com.kazox.aufschlag.api.club.MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)

        val response = client.createCourt(member.accessToken, club.id)
        assertEquals(HttpStatusCode.Forbidden, response.status)

        // but a plain member CAN list courts
        assertEquals(HttpStatusCode.OK, client.listCourts(member.accessToken, club.id).status)
    }

    @Test
    fun `admin can update a court`() = authTestApp { client ->
        val (club, ownerToken) = client.newClub()
        val court = client.createCourt(ownerToken, club.id).body<CourtResponse>()

        val updated = client.updateCourt(
            ownerToken,
            club.id,
            court.id,
            UpdateCourtRequest(
                name = "Renamed Court",
                surface = CourtSurface.GRASS,
                indoor = true,
                active = false,
                slotMinutes = 90,
                defaultPriceCents = 1500,
                memberDiscountPct = 50,
                pausedDiscountPct = 10,
            ),
        )
        assertEquals(HttpStatusCode.OK, updated.status)
        val body = updated.body<CourtResponse>()
        assertEquals("Renamed Court", body.name)
        assertEquals(CourtSurface.GRASS, body.surface)
        assertEquals(false, body.active)
        assertEquals(90, body.slotMinutes)
    }

    @Test
    fun `admin of one club cannot update another club's court by guessing its id`() = authTestApp { client ->
        val (clubA, ownerAToken) = client.newClub()
        val (clubB, ownerBToken) = client.newClub()
        val courtB = client.createCourt(ownerBToken, clubB.id).body<CourtResponse>()

        // ownerA IS an admin — just not of clubB, so the court lookup (scoped to clubA) 404s
        // rather than leaking a 403 that would confirm the court exists elsewhere.
        val response = client.updateCourt(
            ownerAToken,
            clubA.id,
            courtB.id,
            UpdateCourtRequest(
                name = "Hijacked",
                surface = CourtSurface.CLAY,
                indoor = false,
                active = true,
                slotMinutes = 60,
                defaultPriceCents = 0,
                memberDiscountPct = 100,
                pausedDiscountPct = 0,
            ),
        )
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
