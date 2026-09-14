package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.ClubsTable
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.ClubRow
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

    override fun search(query: String?, excludeStatuses: Set<String>, limit: Int, cursor: Uuid?): List<ClubRow> =
        ClubsTable.selectAll()
            .where {
                val cursorCondition = cursor?.let { ClubsTable.id greater it } ?: Op.TRUE
                val trimmed = query?.trim()
                val searchCondition = if (trimmed.isNullOrEmpty()) {
                    Op.TRUE
                } else {
                    val pattern = "%${trimmed.lowercase()}%"
                    (ClubsTable.name.lowerCase() like pattern) or (ClubsTable.slug.lowerCase() like pattern)
                }
                val visibilityCondition = if (excludeStatuses.isNotEmpty()) {
                    ClubsTable.status notInList excludeStatuses
                } else {
                    Op.TRUE
                }
                cursorCondition and searchCondition and visibilityCondition
            }
            .orderBy(ClubsTable.id)
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
}
