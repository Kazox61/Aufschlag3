package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.club.ApplyToClubRequest
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.club.MembershipStatus
import com.kazox.aufschlag.api.club.MyMembershipResponse
import com.kazox.aufschlag.api.club.UpdateMemberRequest
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.mail.Mailer
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.ClubRow
import com.kazox.aufschlag.repositories.MembershipRepository
import com.kazox.aufschlag.repositories.MembershipRow
import com.kazox.aufschlag.repositories.MembershipWithClubRow
import com.kazox.aufschlag.repositories.MembershipWithUserRow
import com.kazox.aufschlag.repositories.UserRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import java.sql.SQLException
import kotlin.uuid.Uuid

class MembershipService(
    private val db: Database,
    private val clubs: ClubRepository,
    private val memberships: MembershipRepository,
    private val users: UserRepository,
    private val entitlements: EntitlementService,
    private val mailer: Mailer,
    /** Outlives the request: mail sends must not block (or fail) the response. */
    private val mailScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** Mitgliedsantrag: any authenticated user may apply. Re-apply after ENDED is always
     *  possible (partial unique index); applying while a non-ENDED row already exists is 409. */
    suspend fun apply(callerUserId: Uuid, clubId: Uuid, request: ApplyToClubRequest): MembershipResponse {
        val applicationDataJson = request.applicationData?.let { json.encodeToString(JsonObject.serializer(), it) }
        return try {
            withTransaction(db) {
                val club = requireClub(clubId)
                entitlements.requireAcceptingApplications(club)
                if (memberships.findNonEnded(callerUserId, clubId) != null) throw alreadyMember()
                val id = memberships.create(
                    userId = callerUserId,
                    clubId = clubId,
                    role = ROLE_MEMBER,
                    status = STATUS_PENDING,
                    applicationDataJson = applicationDataJson,
                )
                memberships.findByIdAndClub(id, clubId)!!.toResponse()
            }
        } catch (e: Exception) {
            // concurrent apply loses the partial-unique-index race
            if (isUniqueViolation(e)) throw alreadyMember() else throw e
        }
    }

    /** Pending applications for a club — ADMIN+ only, enforced by
     *  [com.kazox.aufschlag.routes.withClubRole] at the route layer. */
    suspend fun listApplications(clubId: Uuid, limit: Int, cursor: String?): Page<MembershipResponse> =
        list(clubId, status = STATUS_PENDING, limit = limit, cursor = cursor)

    /** Current (non-pending, non-ended) members of a club — ADMIN+ only, enforced by
     *  [com.kazox.aufschlag.routes.withClubRole] at the route layer. */
    suspend fun listMembers(clubId: Uuid, limit: Int, cursor: String?): Page<MembershipResponse> =
        list(clubId, excludeStatuses = setOf(STATUS_PENDING, STATUS_ENDED), limit = limit, cursor = cursor)

    /** GET /me/memberships — every club the caller has a relationship with (the club switcher).
     *  PENDING is kept (shown as "awaiting approval"); only ENDED (pure history) is dropped. */
    suspend fun myMemberships(callerUserId: Uuid, limit: Int, cursor: String?): Page<MyMembershipResponse> {
        val cursorId = parseCursor(cursor)
        val pageSize = limit.coerceIn(1, MAX_PAGE_SIZE)
        val rows = withTransaction(db) {
            memberships.listByUser(callerUserId, excludeStatuses = setOf(STATUS_ENDED), limit = pageSize + 1, cursor = cursorId)
        }
        val page = rows.take(pageSize)
        return Page(
            items = page.map { it.toResponse() },
            nextCursor = if (rows.size > pageSize) page.last().membership.id.toString() else null,
        )
    }

    /** ADMIN+ only (enforced by [com.kazox.aufschlag.routes.withClubRole] at the route layer);
     *  tenant-scoped (the application must belong to [clubId], not just exist). */
    suspend fun approve(clubId: Uuid, membershipId: Uuid): MembershipResponse {
        val (club, response) = withTransaction(db) {
            val club = requireClub(clubId)
            entitlements.requireWritable(club)
            val application = requirePendingApplication(clubId, membershipId)
            memberships.approve(application.id)
            club to memberships.findByIdAndClubWithUser(application.id, clubId)!!.toResponse()
        }
        notifyDecision(club, response.userId, approved = true)
        return response
    }

    /** Deletes the PENDING row — no rejection audit trail in v1 (re-applying is always
     *  possible). ADMIN+ only, enforced by [com.kazox.aufschlag.routes.withClubRole]. */
    suspend fun reject(clubId: Uuid, membershipId: Uuid) {
        val (club, applicantUserId) = withTransaction(db) {
            val club = requireClub(clubId)
            entitlements.requireWritable(club)
            val application = requirePendingApplication(clubId, membershipId)
            memberships.deletePending(application.id)
            club to application.userId
        }
        notifyDecision(club, applicantUserId.toString(), approved = false)
    }

    /** Admin member management (PUT /clubs/{clubId}/members/{membershipId}, docs/admin-webapp.md
     *  §1.2). Route-gated to ADMIN+; the rules below need the *target's* role too, which the
     *  route gate can't express, so the caller id is passed through:
     *  - Status: ACTIVE/PAUSED/SUSPENDED freely, ENDED = "remove member" (the row stays as
     *    history; re-applying stays possible via the partial unique index). PENDING rows are
     *    handled exclusively by the application-decision endpoint → 404 here; an ENDED row is
     *    immutable history → 404.
     *  - Role: MEMBER ↔ ADMIN only. OWNER is never grantable here, and an OWNER row is never
     *    modifiable here (suspending/ending the OWNER would orphan the club; reassignment is a
     *    super-admin operator duty per PLANNING.md).
     *  - A target ADMIN can only be modified by the OWNER (no admin-vs-admin sabotage).
     *  - Callers cannot target their own row (self-suspension / last-admin-demotes-self
     *    foot-guns; own status changes go through pause/resume). */
    suspend fun updateMember(
        callerUserId: Uuid,
        clubId: Uuid,
        membershipId: Uuid,
        request: UpdateMemberRequest,
    ): MembershipResponse {
        if (request.role == null && request.status == null) {
            throw ApiException.validation("Nothing to update — set role and/or status")
        }
        if (request.role == MembershipRole.OWNER) {
            throw ApiException.validation("OWNER cannot be assigned through this endpoint")
        }
        if (request.status == MembershipStatus.PENDING) {
            throw ApiException.validation("PENDING is not a settable status")
        }
        return withTransaction(db) {
            val club = requireClub(clubId)
            entitlements.requireWritable(club)
            val target = memberships.findByIdAndClub(membershipId, clubId)
                ?.takeIf { it.status != STATUS_PENDING && it.status != STATUS_ENDED }
                ?: throw ApiException.notFound("Member not found")
            if (target.userId == callerUserId) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.CONFLICT, "Cannot modify your own membership")
            }
            if (target.role == ROLE_OWNER) {
                throw ApiException.forbidden("The club OWNER cannot be modified through this endpoint")
            }
            if (target.role == ROLE_ADMIN) {
                val callerRole = memberships.findActive(callerUserId, clubId)?.role
                if (callerRole != ROLE_OWNER) {
                    throw ApiException.forbidden("Only the OWNER can modify an ADMIN's membership")
                }
            }
            val updated = memberships.updateMember(membershipId, request.role?.name, request.status?.name)
            if (updated == 0) throw ApiException.notFound("Member not found")
            memberships.findByIdAndClubWithUser(membershipId, clubId)!!.toResponse()
        }
    }

    /** Voluntary "skipping a year" — the caller pauses their own ACTIVE membership. */
    suspend fun pause(callerUserId: Uuid, clubId: Uuid): MembershipResponse =
        transitionOwnMembership(callerUserId, clubId, from = STATUS_ACTIVE, to = STATUS_PAUSED)

    /** Resumes a previously PAUSED membership back to ACTIVE. */
    suspend fun resume(callerUserId: Uuid, clubId: Uuid): MembershipResponse =
        transitionOwnMembership(callerUserId, clubId, from = STATUS_PAUSED, to = STATUS_ACTIVE)

    private suspend fun transitionOwnMembership(callerUserId: Uuid, clubId: Uuid, from: String, to: String): MembershipResponse =
        withTransaction(db) {
            val club = requireClub(clubId)
            entitlements.requireWritable(club)
            val updated = memberships.transitionStatus(callerUserId, clubId, from, to)
            if (updated == 0) {
                memberships.findNonEnded(callerUserId, clubId)
                    ?: throw ApiException.forbidden("Not a member of this club")
                throw ApiException(
                    HttpStatusCode.Conflict,
                    ErrorCode.CONFLICT,
                    "Membership must be $from to transition to $to",
                )
            }
            memberships.findNonEnded(callerUserId, clubId)!!.toResponse()
        }

    /** Fire-and-forget like the password-reset mail: awaiting the provider call would couple
     *  this endpoint's latency to the mail provider. */
    private fun notifyDecision(club: ClubRow, applicantUserId: String, approved: Boolean) {
        mailScope.launch {
            val email = withTransaction(db) { users.findById(Uuid.parse(applicantUserId)) }?.email ?: return@launch
            mailer.sendApplicationDecision(email, club.name, approved)
        }
    }

    private suspend fun list(
        clubId: Uuid,
        status: String? = null,
        excludeStatuses: Set<String> = emptySet(),
        limit: Int,
        cursor: String?,
    ): Page<MembershipResponse> {
        val cursorId = parseCursor(cursor)
        val pageSize = limit.coerceIn(1, MAX_PAGE_SIZE)
        val rows = withTransaction(db) {
            memberships.listByClub(clubId, status, excludeStatuses, pageSize + 1, cursorId)
        }
        val page = rows.take(pageSize)
        return Page(
            items = page.map { it.toResponse() },
            nextCursor = if (rows.size > pageSize) page.last().membership.id.toString() else null,
        )
    }

    private fun requireClub(clubId: Uuid): ClubRow = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")

    /** Scoped to [clubId] on purpose — an admin of club A must not act on club B's application
     *  by guessing its id (tenant isolation, see CLAUDE.md). */
    private fun requirePendingApplication(clubId: Uuid, membershipId: Uuid): MembershipRow =
        memberships.findByIdAndClub(membershipId, clubId)
            ?.takeIf { it.status == STATUS_PENDING }
            ?: throw ApiException.notFound("Pending application not found")

    private fun parseCursor(cursor: String?): Uuid? =
        cursor?.let { runCatching { Uuid.parse(it) }.getOrNull() ?: throw ApiException.validation("Invalid cursor") }

    private fun alreadyMember() =
        ApiException(HttpStatusCode.Conflict, ErrorCode.ALREADY_MEMBER, "Already applied to or a member of this club")

    private fun isUniqueViolation(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it is SQLException && it.sqlState == "23505" }

    companion object {
        private const val ROLE_MEMBER = "MEMBER"
        private const val ROLE_ADMIN = "ADMIN"
        private const val ROLE_OWNER = "OWNER"
        private const val STATUS_PENDING = "PENDING"
        private const val STATUS_ACTIVE = "ACTIVE"
        private const val STATUS_PAUSED = "PAUSED"
        private const val STATUS_ENDED = "ENDED"
        private const val MAX_PAGE_SIZE = 50
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private fun MembershipRow.toResponse() = MembershipResponse(
    id = id.toString(),
    userId = userId.toString(),
    clubId = clubId.toString(),
    role = MembershipRole.valueOf(role),
    status = MembershipStatus.valueOf(status),
    applicationData = applicationDataJson?.let {
        Json { ignoreUnknownKeys = true }.decodeFromString(JsonObject.serializer(), it)
    },
)

private fun MembershipWithUserRow.toResponse() =
    membership.toResponse().copy(userName = userName, userEmail = userEmail)

private fun MembershipWithClubRow.toResponse() = MyMembershipResponse(
    membershipId = membership.id.toString(),
    role = MembershipRole.valueOf(membership.role),
    status = MembershipStatus.valueOf(membership.status),
    club = club.toResponse(),
)
