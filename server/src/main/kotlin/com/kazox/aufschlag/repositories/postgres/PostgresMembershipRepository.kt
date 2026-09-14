package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.ClubsTable
import com.kazox.aufschlag.db.MembershipsTable
import com.kazox.aufschlag.db.UsersTable
import com.kazox.aufschlag.repositories.ClubRow
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
        role: String,
        status: String,
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
                    (MembershipsTable.status neq STATUS_ENDED)
            }
            .singleOrNull()
            ?.toMembershipRow()

    override fun findActive(userId: Uuid, clubId: Uuid): MembershipRow? =
        MembershipsTable.selectAll()
            .where {
                (MembershipsTable.userId eq userId) and
                    (MembershipsTable.clubId eq clubId) and
                    (MembershipsTable.status eq STATUS_ACTIVE)
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

    override fun updateMember(id: Uuid, role: String?, status: String?): Int =
        MembershipsTable.update({
            (MembershipsTable.id eq id) and (MembershipsTable.status neq STATUS_ENDED)
        }) {
            if (role != null) it[MembershipsTable.role] = role
            if (status != null) it[MembershipsTable.status] = status
        }

    override fun approve(id: Uuid): Int =
        MembershipsTable.update({
            (MembershipsTable.id eq id) and (MembershipsTable.status eq STATUS_PENDING)
        }) {
            it[status] = STATUS_ACTIVE
        }

    override fun deletePending(id: Uuid): Int =
        MembershipsTable.deleteWhere {
            (MembershipsTable.id eq id) and (MembershipsTable.status eq STATUS_PENDING)
        }

    override fun transitionStatus(userId: Uuid, clubId: Uuid, from: String, to: String): Int =
        MembershipsTable.update({
            (MembershipsTable.userId eq userId) and (MembershipsTable.clubId eq clubId) and
                (MembershipsTable.status eq from)
        }) {
            it[MembershipsTable.status] = to
        }

    override fun listByClub(
        clubId: Uuid,
        status: String?,
        excludeStatuses: Set<String>,
        limit: Int,
        cursor: Uuid?,
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
                val cursorCondition = cursor?.let { MembershipsTable.id greater it } ?: Op.TRUE
                clubCondition and statusCondition and excludeCondition and cursorCondition
            }
            .orderBy(MembershipsTable.id)
            .limit(limit)
            .map { it.toMembershipWithUserRow() }

    override fun isSoleActiveOwner(userId: Uuid): Boolean {
        val ownedClubIds = MembershipsTable
            .innerJoin(ClubsTable) { MembershipsTable.clubId eq ClubsTable.id }
            .selectAll()
            .where {
                (MembershipsTable.userId eq userId) and
                    (MembershipsTable.role eq ROLE_OWNER) and
                    (MembershipsTable.status neq STATUS_ENDED) and
                    (ClubsTable.status neq STATUS_ARCHIVED)
            }
            .map { it[MembershipsTable.clubId] }

        return ownedClubIds.any { clubId ->
            MembershipsTable.selectAll()
                .where {
                    (MembershipsTable.clubId eq clubId) and
                        (MembershipsTable.role eq ROLE_OWNER) and
                        (MembershipsTable.status neq STATUS_ENDED) and
                        (MembershipsTable.userId neq userId)
                }
                .empty()
        }
    }

    override fun listByUser(
        userId: Uuid,
        excludeStatuses: Set<String>,
        limit: Int,
        cursor: Uuid?,
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
                val cursorCondition = cursor?.let { MembershipsTable.id greater it } ?: Op.TRUE
                userCondition and excludeCondition and cursorCondition
            }
            .orderBy(MembershipsTable.id)
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
    )

    private fun ResultRow.toClubRow() = ClubRow(
        id = this[ClubsTable.id],
        name = this[ClubsTable.name],
        slug = this[ClubsTable.slug],
        plan = this[ClubsTable.plan],
        status = this[ClubsTable.status],
        timezone = this[ClubsTable.timezone],
        settingsJson = this[ClubsTable.settings],
        billingRef = this[ClubsTable.billingRef],
        address = this[ClubsTable.address],
        contactEmail = this[ClubsTable.contactEmail],
        phone = this[ClubsTable.phone],
        website = this[ClubsTable.website],
        logoUrl = this[ClubsTable.logoUrl],
    )

    companion object {
        const val ROLE_OWNER = "OWNER"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_ENDED = "ENDED"
        const val STATUS_ARCHIVED = "ARCHIVED"
    }
}
