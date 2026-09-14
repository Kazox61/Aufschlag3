package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.club.MembershipStatus
import com.kazox.aufschlag.api.club.MyMembershipResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MembershipFlowTest {

    private suspend fun HttpClient.newClub(superAdminAccessToken: String): Pair<ClubResponse, String> {
        val ownerEmail = uniqueEmail("owner")
        val owner = register(ownerEmail).body<TokenPairResponse>()
        val club = createClub(superAdminAccessToken, "Club ${uniqueSlug()}", uniqueSlug(), ownerEmail)
            .body<ClubResponse>()
        return club to owner.accessToken
    }

    private suspend fun HttpClient.superAdminToken(): String {
        val email = uniqueEmail("admin")
        val token = register(email).body<TokenPairResponse>().accessToken
        makeSuperAdmin(email)
        return token
    }

    @Test
    fun `apply then approve moves the membership to ACTIVE`() = authTestApp { client ->
        val (club, ownerToken) = client.newClub(client.superAdminToken())
        val applicant = client.register(uniqueEmail("applicant")).body<TokenPairResponse>()

        val applied = client.applyToClub(applicant.accessToken, club.id)
        assertEquals(HttpStatusCode.Created, applied.status)
        val application = applied.body<MembershipResponse>()
        assertEquals(MembershipStatus.PENDING, application.status)

        val listed = client.listApplications(ownerToken, club.id).body<Page<MembershipResponse>>()
        assertTrue(listed.items.any { it.id == application.id })

        val approved = client.decideApplication(ownerToken, club.id, application.id, approve = true)
        assertEquals(HttpStatusCode.OK, approved.status)
        assertEquals(MembershipStatus.ACTIVE, approved.body<MembershipResponse>().status)
    }

    @Test
    fun `apply then reject deletes the row and re-apply works`() = authTestApp { client ->
        val (club, ownerToken) = client.newClub(client.superAdminToken())
        val applicant = client.register(uniqueEmail("applicant")).body<TokenPairResponse>()

        val application = client.applyToClub(applicant.accessToken, club.id).body<MembershipResponse>()
        val rejected = client.decideApplication(ownerToken, club.id, application.id, approve = false)
        assertEquals(HttpStatusCode.NoContent, rejected.status)

        val listed = client.listApplications(ownerToken, club.id).body<Page<MembershipResponse>>()
        assertTrue(listed.items.none { it.id == application.id })

        val reapplied = client.applyToClub(applicant.accessToken, club.id)
        assertEquals(HttpStatusCode.Created, reapplied.status)
        assertEquals(1, membershipRowCount(applicant.userId(client), club.id))
    }

    @Test
    fun `ended membership can re-apply and the history row survives`() = authTestApp { client ->
        val (club, ownerToken) = client.newClub(client.superAdminToken())
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()

        val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)
        endMembership(application.id)

        val reapplied = client.applyToClub(member.accessToken, club.id)
        assertEquals(HttpStatusCode.Created, reapplied.status)
        val newApplication = reapplied.body<MembershipResponse>()
        assertEquals(MembershipStatus.PENDING, newApplication.status)
        assertTrue(newApplication.id != application.id)
        // the ENDED row stays as history alongside the new PENDING row
        assertEquals(2, membershipRowCount(member.userId(client), club.id))
    }

    @Test
    fun `applying while already a non-ended member returns 409 ALREADY_MEMBER`() = authTestApp { client ->
        val (club, _) = client.newClub(client.superAdminToken())
        val applicant = client.register(uniqueEmail("applicant")).body<TokenPairResponse>()

        client.applyToClub(applicant.accessToken, club.id)
        val duplicate = client.applyToClub(applicant.accessToken, club.id)
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals(ErrorCode.ALREADY_MEMBER, duplicate.body<ApiError>().code)
    }

    @Test
    fun `admin of one club gets 403 or 404 on another club's routes`() = authTestApp { client ->
        val superAdmin = client.superAdminToken()
        val (clubA, ownerAToken) = client.newClub(superAdmin)
        val (clubB, _) = client.newClub(superAdmin)
        val applicantB = client.register(uniqueEmail("applicantB")).body<TokenPairResponse>()
        val applicationB = client.applyToClub(applicantB.accessToken, clubB.id).body<MembershipResponse>()

        // club A's owner is not a member of club B at all
        assertEquals(HttpStatusCode.Forbidden, client.listApplications(ownerAToken, clubB.id).status)
        assertEquals(HttpStatusCode.Forbidden, client.listMembers(ownerAToken, clubB.id).status)

        // club A's owner can't approve club B's application even by guessing its id under club A's path
        val crossClubDecision = client.decideApplication(ownerAToken, clubA.id, applicationB.id, approve = true)
        assertEquals(HttpStatusCode.NotFound, crossClubDecision.status)
    }

    @Test
    fun `suspended club rejects new applications but archived club still lists existing ones`() = authTestApp { client ->
        val superAdmin = client.superAdminToken()
        val (club, ownerToken) = client.newClub(superAdmin)
        val applicant = client.register(uniqueEmail("applicant")).body<TokenPairResponse>()
        val application = client.applyToClub(applicant.accessToken, club.id).body<MembershipResponse>()

        setClubStatus(club.id, "SUSPENDED")
        val blockedApplicant = client.register(uniqueEmail("blocked")).body<TokenPairResponse>()
        val suspendedApply = client.applyToClub(blockedApplicant.accessToken, club.id)
        assertEquals(HttpStatusCode.Forbidden, suspendedApply.status)

        setClubStatus(club.id, "ARCHIVED")
        val archivedApply = client.applyToClub(blockedApplicant.accessToken, club.id)
        assertEquals(HttpStatusCode.Forbidden, archivedApply.status)

        // reads still work for admins on an archived club...
        val stillListed = client.listApplications(ownerToken, club.id).body<Page<MembershipResponse>>()
        assertTrue(stillListed.items.any { it.id == application.id })

        // ...but it's read-only: no approving/rejecting
        val blockedDecision = client.decideApplication(ownerToken, club.id, application.id, approve = true)
        assertEquals(HttpStatusCode.Forbidden, blockedDecision.status)
    }

    /** Decodes the userId this token belongs to, via /me. */
    private suspend fun TokenPairResponse.userId(client: HttpClient): String =
        client.me(accessToken).body<com.kazox.aufschlag.api.auth.UserResponse>().id

    @Test
    fun `GET me-memberships is empty for a fresh user`() = authTestApp { client ->
        val user = client.register(uniqueEmail("fresh")).body<TokenPairResponse>()
        val page = client.myMemberships(user.accessToken).body<Page<MyMembershipResponse>>()
        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `GET me-memberships shows a PENDING application, then ACTIVE after approval, with club info joined`() = authTestApp { client ->
        val (club, ownerToken) = client.newClub(client.superAdminToken())
        val applicant = client.register(uniqueEmail("applicant")).body<TokenPairResponse>()

        val application = client.applyToClub(applicant.accessToken, club.id).body<MembershipResponse>()
        val pendingPage = client.myMemberships(applicant.accessToken).body<Page<MyMembershipResponse>>()
        val pendingRow = pendingPage.items.single()
        assertEquals(application.id, pendingRow.membershipId)
        assertEquals(MembershipStatus.PENDING, pendingRow.status)
        assertEquals(club.id, pendingRow.club.id)
        assertEquals(club.name, pendingRow.club.name)

        client.decideApplication(ownerToken, club.id, application.id, approve = true)
        val activePage = client.myMemberships(applicant.accessToken).body<Page<MyMembershipResponse>>()
        assertEquals(MembershipStatus.ACTIVE, activePage.items.single().status)
    }

    @Test
    fun `GET me-memberships excludes ENDED rows but not other clubs' memberships`() = authTestApp { client ->
        val superAdmin = client.superAdminToken()
        val (clubA, ownerAToken) = client.newClub(superAdmin)
        val (clubB, ownerBToken) = client.newClub(superAdmin)
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()

        val applicationA = client.applyToClub(member.accessToken, clubA.id).body<MembershipResponse>()
        client.decideApplication(ownerAToken, clubA.id, applicationA.id, approve = true)
        endMembership(applicationA.id)

        val applicationB = client.applyToClub(member.accessToken, clubB.id).body<MembershipResponse>()
        client.decideApplication(ownerBToken, clubB.id, applicationB.id, approve = true)

        val page = client.myMemberships(member.accessToken).body<Page<MyMembershipResponse>>()
        assertEquals(listOf(clubB.id), page.items.map { it.club.id })
    }

    @Test
    fun `GET me-memberships paginates via limit and cursor`() = authTestApp { client ->
        val superAdmin = client.superAdminToken()
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        repeat(3) {
            val (club, ownerToken) = client.newClub(superAdmin)
            val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
            client.decideApplication(ownerToken, club.id, application.id, approve = true)
        }

        val firstPage = client.myMemberships(member.accessToken, limit = 2).body<Page<MyMembershipResponse>>()
        assertEquals(2, firstPage.items.size)
        assertTrue(firstPage.nextCursor != null)

        val secondPage = client.myMemberships(member.accessToken, limit = 2, cursor = firstPage.nextCursor)
            .body<Page<MyMembershipResponse>>()
        assertEquals(1, secondPage.items.size)
        assertNull(secondPage.nextCursor)
    }
}
