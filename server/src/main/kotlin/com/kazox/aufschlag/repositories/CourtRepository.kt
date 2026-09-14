package com.kazox.aufschlag.repositories

import com.kazox.aufschlag.api.court.CourtSurface
import java.time.Instant
import kotlin.uuid.Uuid

data class CourtRow(
    val id: Uuid,
    val clubId: Uuid,
    val name: String,
    val surface: CourtSurface,
    val indoor: Boolean,
    val active: Boolean,
    val slotMinutes: Int,
    val defaultPriceCents: Int,
    val memberDiscountPct: Int,
    val pausedDiscountPct: Int,
    val createdAt: Instant,
) {
    val keyset: Keyset get() = Keyset(createdAt, id)
}

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface CourtRepository {
    fun create(
        clubId: Uuid,
        name: String,
        surface: CourtSurface,
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
        surface: CourtSurface,
        indoor: Boolean,
        active: Boolean,
        slotMinutes: Int,
        defaultPriceCents: Int,
        memberDiscountPct: Int,
        pausedDiscountPct: Int,
    ): Int

    /** Keyset pagination ordered `(created_at, id)` — the order courts were added, which is
     *  what an admin expects; [cursor] is the last row of the previous page. */
    fun listByClub(clubId: Uuid, limit: Int, cursor: Keyset?): List<CourtRow>
}
