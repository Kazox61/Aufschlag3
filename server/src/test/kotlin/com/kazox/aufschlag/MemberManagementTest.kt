package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.auth.UserResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.club.MembershipStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** PUT /clubs/{clubId}/members/{membershipId} — admin member management: role MEMBER↔ADMIN, status ACTIVE/PAUSED/SUSPENDED/ENDED, OWNER rows untouchable,
 *  ADMIN targets require an OWNER caller, self-targeting forbidden. */
class MemberManagementTest {

    private data class Fixture(
        val club: ClubResponse,
        val ownerToken: String,
        val memberToken: String,
        val memberEmail: String,
        val membershipId: String,
    )

    /** Club with an OWNER and one approved ACTIVE member. */
    private suspend fun HttpClient.newClubWithMember(): Fixture {
        val superAdminEmail = uniqueEmail("super")
        val superAdmin = register(superAdminEmail).body<TokenPairResponse>()
        makeSuperAdmin(superAdminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = register(ownerEmail).body<TokenPairResponse>()
        val club = createClub(superAdmin.accessToken, "Club ${uniqueSlug()}", uniqueSlug(), ownerEmail)
            .body<ClubResponse>()
        val memberEmail = uniqueEmail("member")
        val member = register(memberEmail).body<TokenPairResponse>()
        val application = applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        decideApplication(owner.accessToken, club.id, application.id, approve = true)
        return Fixture(club, owner.accessToken, member.accessToken, memberEmail, application.id)
    }

    @Test
    fun `owner promotes a member to ADMIN and back, response carries the member's identity`() =
        authTestApp { client ->
            val fixture = client.newClubWithMember()

            val promoted = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, role = MembershipRole.ADMIN)
            assertEquals(HttpStatusCode.OK, promoted.status)
            val promotedBody = promoted.body<MembershipResponse>()
            assertEquals(MembershipRole.ADMIN, promotedBody.role)
            assertEquals(fixture.memberEmail, promotedBody.userEmail)
            assertNotNull(promotedBody.userName)

            val demoted = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, role = MembershipRole.MEMBER)
            assertEquals(HttpStatusCode.OK, demoted.status)
            assertEquals(MembershipRole.MEMBER, demoted.body<MembershipResponse>().role)
        }

    @Test
    fun `an admin cannot modify another admin - only the owner can`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        // Promote the member to ADMIN, then add a second ADMIN.
        client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, role = MembershipRole.ADMIN)
        val second = client.register(uniqueEmail("second")).body<TokenPairResponse>()
        val secondApplication = client.applyToClub(second.accessToken, fixture.club.id).body<MembershipResponse>()
        client.decideApplication(fixture.ownerToken, fixture.club.id, secondApplication.id, approve = true)
        client.updateMember(fixture.ownerToken, fixture.club.id, secondApplication.id, role = MembershipRole.ADMIN)

        // First admin tries to demote the second → 403; the owner succeeds.
        val sabotage = client.updateMember(fixture.memberToken, fixture.club.id, secondApplication.id, role = MembershipRole.MEMBER)
        assertEquals(HttpStatusCode.Forbidden, sabotage.status)
        val byOwner = client.updateMember(fixture.ownerToken, fixture.club.id, secondApplication.id, role = MembershipRole.MEMBER)
        assertEquals(HttpStatusCode.OK, byOwner.status)
    }

    @Test
    fun `suspend and reactivate a member`() = authTestApp { client ->
        val fixture = client.newClubWithMember()

        val suspended = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, status = MembershipStatus.SUSPENDED)
        assertEquals(MembershipStatus.SUSPENDED, suspended.body<MembershipResponse>().status)

        val reactivated = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, status = MembershipStatus.ACTIVE)
        assertEquals(MembershipStatus.ACTIVE, reactivated.body<MembershipResponse>().status)
    }

    @Test
    fun `ending a membership keeps the history row and the ex-member can re-apply`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        val memberId = client.me(fixture.memberToken).body<UserResponse>().id

        val ended = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, status = MembershipStatus.ENDED)
        assertEquals(HttpStatusCode.OK, ended.status)
        assertEquals(MembershipStatus.ENDED, ended.body<MembershipResponse>().status)

        val reapplied = client.applyToClub(fixture.memberToken, fixture.club.id)
        assertEquals(HttpStatusCode.Created, reapplied.status)
        assertEquals(2, membershipRowCount(memberId, fixture.club.id))

        // The ENDED row is immutable history — even after re-applying, targeting it 404s.
        val resurrect = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, status = MembershipStatus.ACTIVE)
        assertEquals(HttpStatusCode.NotFound, resurrect.status)
    }

    @Test
    fun `the OWNER's own row cannot be modified`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        // Promote the member to ADMIN so they pass the route gate, then have them target the owner.
        client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, role = MembershipRole.ADMIN)
        val ownerMembershipId = client.listMembers(fixture.memberToken, fixture.club.id)
            .body<Page<MembershipResponse>>()
            .items.first { it.role == MembershipRole.OWNER }.id

        val response = client.updateMember(fixture.memberToken, fixture.club.id, ownerMembershipId, status = MembershipStatus.SUSPENDED)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `callers cannot modify their own membership`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, role = MembershipRole.ADMIN)
        // The (now-)admin targets their own row.
        val response = client.updateMember(fixture.memberToken, fixture.club.id, fixture.membershipId, status = MembershipStatus.PAUSED)
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ErrorCode.CONFLICT, response.body<ApiError>().code)
    }

    @Test
    fun `a PENDING application cannot be modified here`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        val applicant = client.register(uniqueEmail("applicant")).body<TokenPairResponse>()
        val application = client.applyToClub(applicant.accessToken, fixture.club.id).body<MembershipResponse>()

        val response = client.updateMember(fixture.ownerToken, fixture.club.id, application.id, status = MembershipStatus.ACTIVE)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `granting OWNER role is rejected`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        val response = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, role = MembershipRole.OWNER)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ApiError>().code)
    }

    @Test
    fun `setting PENDING status is rejected`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        val response = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, status = MembershipStatus.PENDING)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `an empty update is rejected`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        val response = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a plain member cannot use the endpoint`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        val other = client.register(uniqueEmail("other")).body<TokenPairResponse>()
        val application = client.applyToClub(other.accessToken, fixture.club.id).body<MembershipResponse>()
        client.decideApplication(fixture.ownerToken, fixture.club.id, application.id, approve = true)

        val response = client.updateMember(fixture.memberToken, fixture.club.id, application.id, status = MembershipStatus.SUSPENDED)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `an admin of club A cannot modify club B's member through club A's path`() = authTestApp { client ->
        val fixtureA = client.newClubWithMember()
        val fixtureB = client.newClubWithMember()

        // Under club A's path, naming club B's membership id — tenant-scoped lookup 404s.
        val crossPath = client.updateMember(fixtureA.ownerToken, fixtureA.club.id, fixtureB.membershipId, status = MembershipStatus.SUSPENDED)
        assertEquals(HttpStatusCode.NotFound, crossPath.status)

        // Under club B's own path — club A's owner isn't a member of B, the route gate 403s.
        val crossClub = client.updateMember(fixtureA.ownerToken, fixtureB.club.id, fixtureB.membershipId, status = MembershipStatus.SUSPENDED)
        assertEquals(HttpStatusCode.Forbidden, crossClub.status)
    }

    @Test
    fun `an ARCHIVED club rejects member updates`() = authTestApp { client ->
        val fixture = client.newClubWithMember()
        setClubStatus(fixture.club.id, "ARCHIVED")
        val response = client.updateMember(fixture.ownerToken, fixture.club.id, fixture.membershipId, status = MembershipStatus.SUSPENDED)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `members and applications lists carry the member's name and email`() = authTestApp { client ->
        val fixture = client.newClubWithMember()

        val members = client.listMembers(fixture.ownerToken, fixture.club.id).body<Page<MembershipResponse>>()
        val row = members.items.first { it.id == fixture.membershipId }
        assertEquals(fixture.memberEmail, row.userEmail)
        assertNotNull(row.userName)

        val applicant = client.register(uniqueEmail("applicant")).body<TokenPairResponse>()
        client.applyToClub(applicant.accessToken, fixture.club.id)
        val applications = client.listApplications(fixture.ownerToken, fixture.club.id).body<Page<MembershipResponse>>()
        assertNotNull(applications.items.single().userEmail)
    }
}
