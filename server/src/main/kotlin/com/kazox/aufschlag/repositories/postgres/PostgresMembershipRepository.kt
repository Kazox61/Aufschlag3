package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.api.club.ClubStatus
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.club.MembershipStatus
import com.kazox.aufschlag.db.ClubsTable
import com.kazox.aufschlag.db.MembershipsTable
import com.kazox.aufschlag.db.UsersTable
import com.kazox.aufschlag.repositories.Keyset
import com.kazox.aufschlag.repositories.MembershipRepository
import com.kazox.aufschlag.repositories.MembershipRow
import com.kazox.aufschlag.repositories.MembershipWithClubRow
import com.kazox.aufschlag.repositories.MembershipWithUserRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class PostgresMembershipRepository : MembershipRepository {

    override fun create(
        userId: Uuid,
        clubId: Uuid,
        role: MembershipRole,
        status: MembershipStatus,
        applicationDataJson: String?,
    ): Uuid {
        val id = Uuid.random()
        MembershipsTable.insert {
            it[MembershipsTable.id] = id
            it[MembershipsTable.userId] = userId
            it[MembershipsTable.clubId] = clubId
            it[MembershipsTable.role] = role
            it[MembershipsTable.status] = status
            it[MembershipsTable.applicationData] = applicationDataJson
        }
        return id
    }

    override fun findNonEnded(userId: Uuid, clubId: Uuid): MembershipRow? =
        MembershipsTable.selectAll()
            .where {
                (MembershipsTable.userId eq userId) and
                    (MembershipsTable.clubId eq clubId) and
                    (MembershipsTable.status neq MembershipStatus.ENDED)
            }
            .singleOrNull()
            ?.toMembershipRow()

    override fun findActive(userId: Uuid, clubId: Uuid): MembershipRow? =
        MembershipsTable.selectAll()
            .where {
                (MembershipsTable.userId eq userId) and
                    (MembershipsTable.clubId eq clubId) and
                    (MembershipsTable.status eq MembershipStatus.ACTIVE)
            }
            .singleOrNull()
            ?.toMembershipRow()

    override fun findByIdAndClub(id: Uuid, clubId: Uuid): MembershipRow? =
        MembershipsTable.selectAll()
            .where { (MembershipsTable.id eq id) and (MembershipsTable.clubId eq clubId) }
            .singleOrNull()
            ?.toMembershipRow()

    override fun findByIdAndClubWithUser(id: Uuid, clubId: Uuid): MembershipWithUserRow? =
        MembershipsTable
            .innerJoin(UsersTable) { MembershipsTable.userId eq UsersTable.id }
            .selectAll()
            .where { (MembershipsTable.id eq id) and (MembershipsTable.clubId eq clubId) }
            .singleOrNull()
            ?.toMembershipWithUserRow()

    override fun updateMember(id: Uuid, role: MembershipRole?, status: MembershipStatus?): Int =
        MembershipsTable.update({
            (MembershipsTable.id eq id) and (MembershipsTable.status neq MembershipStatus.ENDED)
        }) {
            if (role != null) it[MembershipsTable.role] = role
            if (status != null) it[MembershipsTable.status] = status
        }

    override fun approve(id: Uuid): Int =
        MembershipsTable.update({
            (MembershipsTable.id eq id) and (MembershipsTable.status eq MembershipStatus.PENDING)
        }) {
            it[status] = MembershipStatus.ACTIVE
        }

    override fun deletePending(id: Uuid): Int =
        MembershipsTable.deleteWhere {
            (MembershipsTable.id eq id) and (MembershipsTable.status eq MembershipStatus.PENDING)
        }

    override fun transitionStatus(userId: Uuid, clubId: Uuid, from: MembershipStatus, to: MembershipStatus): Int =
        MembershipsTable.update({
            (MembershipsTable.userId eq userId) and (MembershipsTable.clubId eq clubId) and
                (MembershipsTable.status eq from)
        }) {
            it[MembershipsTable.status] = to
        }

    override fun listByClub(
        clubId: Uuid,
        status: MembershipStatus?,
        excludeStatuses: Set<MembershipStatus>,
        limit: Int,
        cursor: Keyset?,
    ): List<MembershipWithUserRow> =
        MembershipsTable
            .innerJoin(UsersTable) { MembershipsTable.userId eq UsersTable.id }
            .selectAll()
            .where {
                val clubCondition = MembershipsTable.clubId eq clubId
                val statusCondition = status?.let { MembershipsTable.status eq it } ?: Op.TRUE
                val excludeCondition = if (excludeStatuses.isNotEmpty()) {
                    MembershipsTable.status notInList excludeStatuses
                } else {
                    Op.TRUE
                }
                val cursorCondition = keysetAfter(MembershipsTable.createdAt, MembershipsTable.id, cursor)
                clubCondition and statusCondition and excludeCondition and cursorCondition
            }
            .orderBy(*keysetOrder(MembershipsTable.createdAt, MembershipsTable.id))
            .limit(limit)
            .map { it.toMembershipWithUserRow() }

    override fun isSoleActiveOwner(userId: Uuid): Boolean {
        val ownedClubIds = MembershipsTable
            .innerJoin(ClubsTable) { MembershipsTable.clubId eq ClubsTable.id }
            .selectAll()
            .where {
                (MembershipsTable.userId eq userId) and
                    (MembershipsTable.role eq MembershipRole.OWNER) and
                    (MembershipsTable.status neq MembershipStatus.ENDED) and
                    (ClubsTable.status neq ClubStatus.ARCHIVED)
            }
            .map { it[MembershipsTable.clubId] }

        return ownedClubIds.any { clubId ->
            MembershipsTable.selectAll()
                .where {
                    (MembershipsTable.clubId eq clubId) and
                        (MembershipsTable.role eq MembershipRole.OWNER) and
                        (MembershipsTable.status neq MembershipStatus.ENDED) and
                        (MembershipsTable.userId neq userId)
                }
                .empty()
        }
    }

    override fun listByUser(
        userId: Uuid,
        excludeStatuses: Set<MembershipStatus>,
        limit: Int,
        cursor: Keyset?,
    ): List<MembershipWithClubRow> =
        MembershipsTable
            .innerJoin(ClubsTable) { MembershipsTable.clubId eq ClubsTable.id }
            .selectAll()
            .where {
                val userCondition = MembershipsTable.userId eq userId
                val excludeCondition = if (excludeStatuses.isNotEmpty()) {
                    MembershipsTable.status notInList excludeStatuses
                } else {
                    Op.TRUE
                }
                val cursorCondition = keysetAfter(MembershipsTable.createdAt, MembershipsTable.id, cursor)
                userCondition and excludeCondition and cursorCondition
            }
            .orderBy(*keysetOrder(MembershipsTable.createdAt, MembershipsTable.id))
            .limit(limit)
            .map { MembershipWithClubRow(it.toMembershipRow(), it.toClubRow()) }

    private fun ResultRow.toMembershipWithUserRow() = MembershipWithUserRow(
        membership = toMembershipRow(),
        userName = this[UsersTable.name],
        userEmail = this[UsersTable.email],
    )

    private fun ResultRow.toMembershipRow() = MembershipRow(
        id = this[MembershipsTable.id],
        userId = this[MembershipsTable.userId],
        clubId = this[MembershipsTable.clubId],
        role = this[MembershipsTable.role],
        status = this[MembershipsTable.status],
        applicationDataJson = this[MembershipsTable.applicationData],
        createdAt = this[MembershipsTable.createdAt],
    )
}
