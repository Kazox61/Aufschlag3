package com.kazox.aufschlag.repositories

import com.kazox.aufschlag.api.club.ClubStatus
import java.time.Instant
import kotlin.uuid.Uuid

data class ClubRow(
    val id: Uuid,
    val name: String,
    val slug: String,
    val plan: String,
    val status: ClubStatus,
    val timezone: String,
    val settingsJson: String,
    val billingRef: String?,
    val address: String?,
    val contactEmail: String?,
    val phone: String?,
    val website: String?,
    val logoUrl: String?,
    val createdAt: Instant,
) {
    val keyset: Keyset get() = Keyset(createdAt, id)
}

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface ClubRepository {
    fun create(name: String, slug: String, timezone: String, settingsJson: String): Uuid
    fun findById(id: Uuid): ClubRow?
    fun findBySlug(slug: String): ClubRow?

    /** Keyset pagination ordered `(created_at, id)`; [cursor] is the last row of the previous
     *  page. [excludeStatuses] filters at the DB level so limit/cursor stay pagination-correct
     *  (the caller sources this from [com.kazox.aufschlag.services.EntitlementService] —
     *  ARCHIVED clubs are invisible in the public directory). */
    fun search(query: String?, excludeStatuses: Set<ClubStatus>, limit: Int, cursor: Keyset?): List<ClubRow>

    /** The "Vereinsdaten" admin settings form — plain profile fields, not [ClubRow.settingsJson]. */
    fun updateProfile(
        id: Uuid,
        name: String,
        address: String?,
        contactEmail: String?,
        phone: String?,
        website: String?,
        logoUrl: String?,
    )
}
