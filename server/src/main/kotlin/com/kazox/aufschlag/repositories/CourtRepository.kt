package com.kazox.aufschlag.repositories

import kotlin.uuid.Uuid

data class CourtRow(
    val id: Uuid,
    val clubId: Uuid,
    val name: String,
    val surface: String,
    val indoor: Boolean,
    val active: Boolean,
    val slotMinutes: Int,
    val defaultPriceCents: Int,
    val memberDiscountPct: Int,
    val pausedDiscountPct: Int,
)

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface CourtRepository {
    fun create(
        clubId: Uuid,
        name: String,
        surface: String,
        indoor: Boolean,
        slotMinutes: Int,
        defaultPriceCents: Int,
        memberDiscountPct: Int,
        pausedDiscountPct: Int,
    ): Uuid

    fun findByIdAndClub(id: Uuid, clubId: Uuid): CourtRow?

    /** Scoped to [clubId] — an admin of club A must not update club B's court by guessing its id. */
    fun update(
        id: Uuid,
        clubId: Uuid,
        name: String,
        surface: String,
        indoor: Boolean,
        active: Boolean,
        slotMinutes: Int,
        defaultPriceCents: Int,
        memberDiscountPct: Int,
        pausedDiscountPct: Int,
    ): Int

    /** Keyset pagination ordered by id; [cursor] is the last id seen on the previous page. */
    fun listByClub(clubId: Uuid, limit: Int, cursor: Uuid?): List<CourtRow>
}
