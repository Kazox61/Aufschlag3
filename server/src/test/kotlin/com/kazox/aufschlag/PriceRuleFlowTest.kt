package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.court.CourtResponse
import com.kazox.aufschlag.api.court.PriceRuleRequest
import com.kazox.aufschlag.api.court.PriceRuleResponse
import com.kazox.aufschlag.api.court.ReplacePriceRulesRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PriceRuleFlowTest {

    private suspend fun HttpClient.newClubWithCourt(): Triple<ClubResponse, String, CourtResponse> {
        val adminEmail = uniqueEmail("admin")
        val admin = register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = register(ownerEmail).body<TokenPairResponse>()
        val club = createClub(admin.accessToken, "Pricing Club", uniqueSlug(), ownerEmail).body<ClubResponse>()
        val court = createCourt(owner.accessToken, club.id).body<CourtResponse>()
        return Triple(club, owner.accessToken, court)
    }

    private fun morningRule(priceCents: Int = 1000) = PriceRuleRequest(
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        startTime = LocalTime(8, 0),
        endTime = LocalTime(16, 0),
        priceCents = priceCents,
    )

    private fun eveningRule(priceCents: Int = 2000) = PriceRuleRequest(
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        startTime = LocalTime(16, 0),
        endTime = LocalTime(22, 0),
        priceCents = priceCents,
    )

    @Test
    fun `admin replaces the rule set and it round-trips`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()

        val replaced = client.replacePriceRules(
            ownerToken,
            club.id,
            court.id,
            ReplacePriceRulesRequest(listOf(morningRule(), eveningRule())),
        )
        assertEquals(HttpStatusCode.OK, replaced.status)
        val rules = replaced.body<List<PriceRuleResponse>>()
        assertEquals(2, rules.size)
        assertTrue(rules.any { it.priceCents == 1000 })
        assertTrue(rules.any { it.priceCents == 2000 })

        val listed = client.listPriceRules(ownerToken, club.id, court.id).body<List<PriceRuleResponse>>()
        assertEquals(2, listed.size)
    }

    @Test
    fun `wholesale replace drops rules that are no longer submitted`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        client.replacePriceRules(ownerToken, club.id, court.id, ReplacePriceRulesRequest(listOf(morningRule(), eveningRule())))

        val second = client.replacePriceRules(ownerToken, club.id, court.id, ReplacePriceRulesRequest(listOf(morningRule(500))))
        assertEquals(HttpStatusCode.OK, second.status)

        val listed = client.listPriceRules(ownerToken, club.id, court.id).body<List<PriceRuleResponse>>()
        assertEquals(1, listed.size)
        assertEquals(500, listed.single().priceCents)
    }

    @Test
    fun `overlapping rules are rejected and nothing is written`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val overlapping = PriceRuleRequest(
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            startTime = LocalTime(15, 0), // overlaps morningRule's 08:00-16:00
            endTime = LocalTime(18, 0),
            priceCents = 999,
        )

        val response = client.replacePriceRules(
            ownerToken,
            club.id,
            court.id,
            ReplacePriceRulesRequest(listOf(morningRule(), overlapping)),
        )
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ApiError>().code)

        // rejected write must not clobber whatever was there before (nothing, in this case)
        val listed = client.listPriceRules(ownerToken, club.id, court.id).body<List<PriceRuleResponse>>()
        assertTrue(listed.isEmpty())
    }

    @Test
    fun `endTime before startTime is a validation error`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val backwards = PriceRuleRequest(
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            startTime = LocalTime(16, 0),
            endTime = LocalTime(8, 0),
            priceCents = 500,
        )

        val response = client.replacePriceRules(ownerToken, club.id, court.id, ReplacePriceRulesRequest(listOf(backwards)))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `seasonal rules with non-overlapping validity windows are both accepted`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val winter = PriceRuleRequest(
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            startTime = LocalTime(8, 0),
            endTime = LocalTime(16, 0),
            validFrom = LocalDate(2026, 10, 1),
            validTo = LocalDate(2027, 3, 31),
            priceCents = 1500,
        )
        val summer = winter.copy(
            validFrom = LocalDate(2026, 4, 1),
            validTo = LocalDate(2026, 9, 30),
            priceCents = 1000,
        )

        val response = client.replacePriceRules(ownerToken, club.id, court.id, ReplacePriceRulesRequest(listOf(winter, summer)))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, response.body<List<PriceRuleResponse>>().size)
    }

    @Test
    fun `plain member cannot replace price rules but can read them`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = client.applyToClub(member.accessToken, club.id)
            .body<com.kazox.aufschlag.api.club.MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)

        val response = client.replacePriceRules(
            member.accessToken,
            club.id,
            court.id,
            ReplacePriceRulesRequest(listOf(morningRule())),
        )
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(HttpStatusCode.OK, client.listPriceRules(member.accessToken, club.id, court.id).status)
    }
}
