package com.kazox.aufschlag

import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.club.MembershipStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class MembershipPauseResumeTest {

    private suspend fun HttpClient.activeMember(): Triple<ClubResponse, String, String> {
        val adminEmail = uniqueEmail("admin")
        val admin = register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = register(ownerEmail).body<TokenPairResponse>()
        val club = createClub(admin.accessToken, "Pause Club", uniqueSlug(), ownerEmail).body<ClubResponse>()
        val member = register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        decideApplication(owner.accessToken, club.id, application.id, approve = true)
        return Triple(club, owner.accessToken, member.accessToken)
    }

    @Test
    fun `active member can pause then resume their own membership`() = authTestApp { client ->
        val (club, _, memberToken) = client.activeMember()

        val paused = client.pauseMembership(memberToken, club.id)
        assertEquals(HttpStatusCode.OK, paused.status)
        assertEquals(MembershipStatus.PAUSED, paused.body<MembershipResponse>().status)

        val resumed = client.resumeMembership(memberToken, club.id)
        assertEquals(HttpStatusCode.OK, resumed.status)
        assertEquals(MembershipStatus.ACTIVE, resumed.body<MembershipResponse>().status)
    }

    @Test
    fun `pausing an already-paused membership is a conflict`() = authTestApp { client ->
        val (club, _, memberToken) = client.activeMember()
        client.pauseMembership(memberToken, club.id)

        val response = client.pauseMembership(memberToken, club.id)
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `resuming an ACTIVE (not paused) membership is a conflict`() = authTestApp { client ->
        val (club, _, memberToken) = client.activeMember()

        val response = client.resumeMembership(memberToken, club.id)
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `non-member pausing a club they never joined is forbidden`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        client.register(ownerEmail)
        val club = client.createClub(admin.accessToken, "Empty Club", uniqueSlug(), ownerEmail).body<ClubResponse>()
        val outsider = client.register(uniqueEmail("outsider")).body<TokenPairResponse>()

        val response = client.pauseMembership(outsider.accessToken, club.id)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `approving an application sends a decision email to the applicant`() {
        val mailer = RecordingMailer()
        authTestApp(mailer = mailer) { client ->
            val adminEmail = uniqueEmail("admin")
            val admin = client.register(adminEmail).body<TokenPairResponse>()
            makeSuperAdmin(adminEmail)
            val ownerEmail = uniqueEmail("owner")
            val owner = client.register(ownerEmail).body<TokenPairResponse>()
            val club = client.createClub(admin.accessToken, "Mail Club", uniqueSlug(), ownerEmail).body<ClubResponse>()
            val applicantEmail = uniqueEmail("applicant")
            val applicant = client.register(applicantEmail).body<TokenPairResponse>()
            val application = client.applyToClub(applicant.accessToken, club.id).body<MembershipResponse>()

            client.decideApplication(owner.accessToken, club.id, application.id, approve = true)

            val decision = mailer.awaitDecisionFor(applicantEmail)
            assertEquals("Mail Club", decision.clubName)
            assertEquals(true, decision.approved)
        }
    }

    @Test
    fun `rejecting an application sends a decision email to the applicant`() {
        val mailer = RecordingMailer()
        authTestApp(mailer = mailer) { client ->
            val adminEmail = uniqueEmail("admin")
            val admin = client.register(adminEmail).body<TokenPairResponse>()
            makeSuperAdmin(adminEmail)
            val ownerEmail = uniqueEmail("owner")
            val owner = client.register(ownerEmail).body<TokenPairResponse>()
            val club = client.createClub(admin.accessToken, "Mail Club Reject", uniqueSlug(), ownerEmail).body<ClubResponse>()
            val applicantEmail = uniqueEmail("applicant")
            val applicant = client.register(applicantEmail).body<TokenPairResponse>()
            val application = client.applyToClub(applicant.accessToken, club.id).body<MembershipResponse>()

            client.decideApplication(owner.accessToken, club.id, application.id, approve = false)

            val decision = mailer.awaitDecisionFor(applicantEmail)
            assertEquals(false, decision.approved)
        }
    }
}
