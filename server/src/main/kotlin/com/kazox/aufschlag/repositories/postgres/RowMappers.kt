package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.ClubsTable
import com.kazox.aufschlag.repositories.ClubRow
import org.jetbrains.exposed.v1.core.ResultRow

/** Shared by [PostgresClubRepository] and the club join in [PostgresMembershipRepository]. */
internal fun ResultRow.toClubRow() = ClubRow(
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
    createdAt = this[ClubsTable.createdAt],
)
