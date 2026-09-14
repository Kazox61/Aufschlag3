package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.AppJson
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.club.ApplyToClubRequest
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.club.MembershipStatus
import com.kazox.aufschlag.api.club.MyMembershipResponse
import com.kazox.aufschlag.api.club.UpdateMemberRequest
import com.kazox.aufschlag.db.isUniqueViolation
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.mail.Mailer
import com.kazox.aufschlag.repositories.BookingRepository
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.ClubRow
import com.kazox.aufschlag.repositories.MembershipRepository
import com.kazox.aufschlag.repositories.MembershipRow
import com.kazox.aufschlag.repositories.MembershipWithClubRow
import com.kazox.aufschlag.repositories.MembershipWithUserRow
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.Uuid

class MembershipService(
    private val db: Database,
    private val clubs: ClubRepository,
    private val memberships: MembershipRepository,
    private val bookings: BookingRepository,
    private val entitlements: EntitlementService,
    private val mailer: Mailer,
    /** Outlives the request: mail sends must not block (or fail) the response. */
    private val mailScope: CoroutineScope,
) {
    /** Mitgliedsantrag: any authenticated user may apply. Re-apply after ENDED is always
     *  possible (partial unique index); applying while a non-ENDED row already exists is 409. */
    suspend fun apply(callerUserId: Uuid, clubId: Uuid, request: ApplyToClubRequest): MembershipResponse {
        val applicationDataJson = request.applicationData?.let { AppJson.encodeToString(JsonObject.serializer(), it) }
        return try {
            withTransaction(db) {
                val club = requireClub(clubId)
                entitlements.requireAcceptingApplications(club)
                if (memberships.findNonEnded(callerUserId, clubId) != null) throw alreadyMember()
                val id = memberships.create(
                    userId = callerUserId,
                    clubId = clubId,
                    role = MembershipRole.MEMBER,
                    status = MembershipStatus.PENDING,
                    applicationDataJson = applicationDataJson,
                )
                memberships.findByIdAndClub(id, clubId)!!.toResponse()
            }
        } catch (e: Exception) {
            // concurrent apply loses the partial-unique-index race
            if (e.isUniqueViolation()) throw alreadyMember() else throw e
        }
    }

    /** Pending applications for a club — ADMIN+ only, enforced by
     *  [com.kazox.aufschlag.routes.withClubRole] at the route layer. */
    suspend fun listApplications(clubId: Uuid, limit: Int, cursor: String?): Page<MembershipResponse> =
        list(clubId, status = MembershipStatus.PENDING, limit = limit, cursor = cursor)

    /** Current (non-pending, non-ended) members of a club — ADMIN+ only, enforced by
     *  [com.kazox.aufschlag.routes.withClubRole] at the route layer. */
    suspend fun listMembers(clubId: Uuid, limit: Int, cursor: String?): Page<MembershipResponse> =
        list(clubId, excludeStatuses = setOf(MembershipStatus.PENDING, MembershipStatus.ENDED), limit = limit, cursor = cursor)

    /** GET /me/memberships — every club the caller has a relationship with (the club switcher).
     *  PENDING is kept (shown as "awaiting approval"); only ENDED (pure history) is dropped. */
    suspend fun myMemberships(callerUserId: Uuid, limit: Int, cursor: String?): Page<MyMembershipResponse> {
        val keyset = parseKeysetCursor(cursor)
        val pageSize = pageSizeOf(limit)
        val rows = withTransaction(db) {
            memberships.listByUser(callerUserId, excludeStatuses = setOf(MembershipStatus.ENDED), limit = pageSize + 1, cursor = keyset)
        }
        return rows.toPage(pageSize, { it.membership.keyset }, MembershipWithClubRow::toResponse)
    }

    /** ADMIN+ only (enforced by [com.kazox.aufschlag.routes.withClubRole] at the route layer);
     *  tenant-scoped (the application must belong to [clubId], not just exist). */
    suspend fun approve(clubId: Uuid, membershipId: Uuid): MembershipResponse {
        val (club, approved) = withTransaction(db) {
            val club = requireClub(clubId)
            entitlements.requireWritable(club)
            val application = requirePendingApplication(clubId, membershipId)
            // conditional on status = PENDING: a concurrent decision that committed between the
            // read above and this update leaves 0 rows — report it, don't mail a stale outcome
            if (memberships.approve(application.membership.id) == 0) throw pendingApplicationNotFound()
            club to memberships.findByIdAndClubWithUser(application.membership.id, clubId)!!
        }
        notifyDecision(club, approved.userEmail, approved = true)
        return approved.toResponse()
    }

    /** Deletes the PENDING row — no rejection audit trail in v1 (re-applying is always
     *  possible). ADMIN+ only, enforced by [com.kazox.aufschlag.routes.withClubRole]. */
    suspend fun reject(clubId: Uuid, membershipId: Uuid) {
        val (club, applicantEmail) = withTransaction(db) {
            val club = requireClub(clubId)
            entitlements.requireWritable(club)
            val application = requirePendingApplication(clubId, membershipId)
            if (memberships.deletePending(application.membership.id) == 0) throw pendingApplicationNotFound()
            club to application.userEmail
        }
        notifyDecision(club, applicantEmail, approved = false)
    }

    /** Admin member management (PUT /clubs/{clubId}/members/{membershipId}). Route-gated to
     *  ADMIN+; the rules below need the *target's* role too, which the route gate can't
     *  express, so the caller id is passed through:
     *  - Status: ACTIVE/PAUSED/SUSPENDED freely, ENDED = "remove member" (the row stays as
     *    history; re-applying stays possible via the partial unique index). PENDING rows are
     *    handled exclusively by the application-decision endpoint → 404 here; an ENDED row is
     *    immutable history → 404.
     *  - Role: MEMBER ↔ ADMIN only. OWNER is never grantable here, and an OWNER row is never
     *    modifiable here (suspending/ending the OWNER would orphan the club; reassignment is a
     *    a super-admin operator duty).
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
                ?.takeIf { it.status != MembershipStatus.PENDING && it.status != MembershipStatus.ENDED }
                ?: throw ApiException.notFound("Member not found")
            if (target.userId == callerUserId) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.CONFLICT, "Cannot modify your own membership")
            }
            if (target.role == MembershipRole.OWNER) {
                throw ApiException.forbidden("The club OWNER cannot be modified through this endpoint")
            }
            if (target.role == MembershipRole.ADMIN) {
                val callerRole = memberships.findActive(callerUserId, clubId)?.role
                if (callerRole != MembershipRole.OWNER) {
                    throw ApiException.forbidden("Only the OWNER can modify an ADMIN's membership")
                }
            }
            // A status change alters the target's booking tier/limits, so it takes the target's
            // per-user lock — the one BookingService.create holds from its status read through
            // its insert — so a booking can't be priced against a status that is being
            // changed concurrently. Role-only updates don't affect booking and stay lock-free.
            if (request.status != null) bookings.lockForBookingLimitCheck(target.userId)
            val updated = memberships.updateMember(membershipId, request.role, request.status)
            if (updated == 0) throw ApiException.notFound("Member not found")
            memberships.findByIdAndClubWithUser(membershipId, clubId)!!.toResponse()
        }
    }

    /** Voluntary "skipping a year" — the caller pauses their own ACTIVE membership. */
    suspend fun pause(callerUserId: Uuid, clubId: Uuid): MembershipResponse =
        transitionOwnMembership(callerUserId, clubId, from = MembershipStatus.ACTIVE, to = MembershipStatus.PAUSED)

    /** Resumes a previously PAUSED membership back to ACTIVE. */
    suspend fun resume(callerUserId: Uuid, clubId: Uuid): MembershipResponse =
        transitionOwnMembership(callerUserId, clubId, from = MembershipStatus.PAUSED, to = MembershipStatus.ACTIVE)

    private suspend fun transitionOwnMembership(
        callerUserId: Uuid,
        clubId: Uuid,
        from: MembershipStatus,
        to: MembershipStatus,
    ): MembershipResponse =
        withTransaction(db) {
            val club = requireClub(clubId)
            entitlements.requireWritable(club)
            // same per-user lock as updateMember: pause/resume changes the caller's booking tier
            bookings.lockForBookingLimitCheck(callerUserId)
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
    private fun notifyDecision(club: ClubRow, applicantEmail: String, approved: Boolean) {
        mailScope.launch { mailer.sendApplicationDecision(applicantEmail, club.name, approved) }
    }

    private suspend fun list(
        clubId: Uuid,
        status: MembershipStatus? = null,
        excludeStatuses: Set<MembershipStatus> = emptySet(),
        limit: Int,
        cursor: String?,
    ): Page<MembershipResponse> {
        val keyset = parseKeysetCursor(cursor)
        val pageSize = pageSizeOf(limit)
        val rows = withTransaction(db) {
            memberships.listByClub(clubId, status, excludeStatuses, pageSize + 1, keyset)
        }
        return rows.toPage(pageSize, { it.membership.keyset }, MembershipWithUserRow::toResponse)
    }

    private fun requireClub(clubId: Uuid): ClubRow = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")

    /** Scoped to [clubId] on purpose — an admin of club A must not act on club B's application
     *  by guessing its id (tenant isolation). Joined with the applicant so the decision mail
     *  needs no second lookup. */
    private fun requirePendingApplication(clubId: Uuid, membershipId: Uuid): MembershipWithUserRow =
        memberships.findByIdAndClubWithUser(membershipId, clubId)
            ?.takeIf { it.membership.status == MembershipStatus.PENDING }
            ?: throw pendingApplicationNotFound()

    private fun pendingApplicationNotFound() = ApiException.notFound("Pending application not found")

    private fun alreadyMember() =
        ApiException(HttpStatusCode.Conflict, ErrorCode.ALREADY_MEMBER, "Already applied to or a member of this club")

}

private fun MembershipRow.toResponse() = MembershipResponse(
    id = id.toString(),
    userId = userId.toString(),
    clubId = clubId.toString(),
    role = role,
    status = status,
    applicationData = applicationDataJson?.let { AppJson.decodeFromString(JsonObject.serializer(), it) },
)

private fun MembershipWithUserRow.toResponse() =
    membership.toResponse().copy(userName = userName, userEmail = userEmail)

private fun MembershipWithClubRow.toResponse() = MyMembershipResponse(
    membershipId = membership.id.toString(),
    role = membership.role,
    status = membership.status,
    club = club.toResponse(),
)
