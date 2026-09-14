package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.api.club.ClubStatus
import com.kazox.aufschlag.db.ClubsTable
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.ClubRow
import com.kazox.aufschlag.repositories.Keyset
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class PostgresClubRepository : ClubRepository {

    override fun create(name: String, slug: String, timezone: String, settingsJson: String): Uuid {
        val id = Uuid.random()
        ClubsTable.insert {
            it[ClubsTable.id] = id
            it[ClubsTable.name] = name
            it[ClubsTable.slug] = slug
            it[ClubsTable.timezone] = timezone
            it[ClubsTable.settings] = settingsJson
        }
        return id
    }

    override fun findById(id: Uuid): ClubRow? =
        ClubsTable.selectAll().where { ClubsTable.id eq id }.singleOrNull()?.toClubRow()

    override fun findBySlug(slug: String): ClubRow? =
        ClubsTable.selectAll().where { ClubsTable.slug eq slug }.singleOrNull()?.toClubRow()

    override fun search(query: String?, excludeStatuses: Set<ClubStatus>, limit: Int, cursor: Keyset?): List<ClubRow> =
        ClubsTable.selectAll()
            .where {
                val cursorCondition = keysetAfter(ClubsTable.createdAt, ClubsTable.id, cursor)
                val trimmed = query?.trim()
                val searchCondition = if (trimmed.isNullOrEmpty()) {
                    Op.TRUE
                } else {
                    // ofLiteral escapes %, _ and the escape char so a search for "50%" or "a_b"
                    // matches those characters literally instead of acting as wildcards
                    val pattern = LikePattern("%", LIKE_ESCAPE) + LikePattern.ofLiteral(trimmed.lowercase(), LIKE_ESCAPE) + "%"
                    (ClubsTable.name.lowerCase() like pattern) or (ClubsTable.slug.lowerCase() like pattern)
                }
                val visibilityCondition = if (excludeStatuses.isNotEmpty()) {
                    ClubsTable.status notInList excludeStatuses
                } else {
                    Op.TRUE
                }
                cursorCondition and searchCondition and visibilityCondition
            }
            .orderBy(*keysetOrder(ClubsTable.createdAt, ClubsTable.id))
            .limit(limit)
            .map { it.toClubRow() }

    override fun updateProfile(
        id: Uuid,
        name: String,
        address: String?,
        contactEmail: String?,
        phone: String?,
        website: String?,
        logoUrl: String?,
    ) {
        ClubsTable.update({ ClubsTable.id eq id }) {
            it[ClubsTable.name] = name
            it[ClubsTable.address] = address
            it[ClubsTable.contactEmail] = contactEmail
            it[ClubsTable.phone] = phone
            it[ClubsTable.website] = website
            it[ClubsTable.logoUrl] = logoUrl
        }
    }

    private companion object {
        const val LIKE_ESCAPE = '\\'
    }
}
