package com.kazox.aufschlag.repositories

import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.club.MembershipStatus
import java.time.Instant
import kotlin.uuid.Uuid

data class MembershipRow(
    val id: Uuid,
    val userId: Uuid,
    val clubId: Uuid,
    val role: MembershipRole,
    val status: MembershipStatus,
    val applicationDataJson: String?,
    val createdAt: Instant,
) {
    val keyset: Keyset get() = Keyset(createdAt, id)
}

data class MembershipWithClubRow(
    val membership: MembershipRow,
    val club: ClubRow,
)

/** [MembershipRow] joined with the member's display identity — the admin-facing lists and
 *  responses (applications/members), which would otherwise render bare user ids. */
data class MembershipWithUserRow(
    val membership: MembershipRow,
    val userName: String,
    val userEmail: String,
)

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface MembershipRepository {
    fun create(userId: Uuid, clubId: Uuid, role: MembershipRole, status: MembershipStatus, applicationDataJson: String?): Uuid

    /** The caller's non-ENDED row for this club, if any — re-apply eligibility (§ partial unique index). */
    fun findNonEnded(userId: Uuid, clubId: Uuid): MembershipRow?

    /** ACTIVE membership only — the row that grants role-based authorization. */
    fun findActive(userId: Uuid, clubId: Uuid): MembershipRow?

    fun findByIdAndClub(id: Uuid, clubId: Uuid): MembershipRow?

    /** Sets status = ACTIVE (application approved). Returns rows updated (0 if not PENDING/not found). */
    fun approve(id: Uuid): Int

    /** Deletes a PENDING row (application rejected — no audit trail in v1). Returns rows deleted. */
    fun deletePending(id: Uuid): Int

    /** Atomic compare-and-set: updates the caller's non-ENDED row for this club from [from] to
     *  [to] (e.g. pause/resume ACTIVE<->PAUSED) only if it's still in status [from]. Returns rows
     *  updated (0 if not a member here, or the status already moved on). */
    fun transitionStatus(userId: Uuid, clubId: Uuid, from: MembershipStatus, to: MembershipStatus): Int

    /** [findByIdAndClub] joined with the member's name/email — the admin decision/update
     *  responses (same join as [listByClub]). */
    fun findByIdAndClubWithUser(id: Uuid, clubId: Uuid): MembershipWithUserRow?

    /** Sets role and/or status (null = unchanged) on membership [id]. The `status != ENDED`
     *  guard lives in the WHERE clause so an ENDED history row can never be resurrected, even
     *  in a race with a concurrent end. Returns rows updated. */
    fun updateMember(id: Uuid, role: MembershipRole?, status: MembershipStatus?): Int

    /** Keyset pagination ordered `(created_at, id)` — application/join order; [cursor] is the
     *  last row of the previous page. [status] restricts to exactly that status (e.g. pending applications); [excludeStatuses]
     *  filters at the DB level so limit/cursor stay pagination-correct. Joined with the member's
     *  name/email — this list only feeds admin UIs (applications/members). */
    fun listByClub(
        clubId: Uuid,
        status: MembershipStatus?,
        excludeStatuses: Set<MembershipStatus> = emptySet(),
        limit: Int,
        cursor: Keyset?,
    ): List<MembershipWithUserRow>

    /** True if [userId] is OWNER of a non-archived club with no other live OWNER — GDPR delete guard. */
    fun isSoleActiveOwner(userId: Uuid): Boolean

    /** Keyset pagination ordered `(created_at, id)`, joined with each row's club — GET
     *  /me/memberships (the club switcher). [excludeStatuses] filters at the DB level, same
     *  convention as [listByClub]. */
    fun listByUser(
        userId: Uuid,
        excludeStatuses: Set<MembershipStatus> = emptySet(),
        limit: Int,
        cursor: Keyset?,
    ): List<MembershipWithClubRow>
}
