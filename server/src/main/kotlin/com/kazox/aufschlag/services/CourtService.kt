package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.court.CourtResponse
import com.kazox.aufschlag.api.court.CourtSurface
import com.kazox.aufschlag.api.court.CreateCourtRequest
import com.kazox.aufschlag.api.court.UpdateCourtRequest
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.CourtRepository
import com.kazox.aufschlag.repositories.CourtRow
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.Uuid

/** ADMIN+ for create/update, MEMBER+ for reads — enforced by
 *  [com.kazox.aufschlag.routes.withClubRole] at the route layer. */
class CourtService(
    private val db: Database,
    private val clubs: ClubRepository,
    private val courts: CourtRepository,
    private val entitlements: EntitlementService,
) {
    suspend fun create(clubId: Uuid, request: CreateCourtRequest): CourtResponse {
        val name = request.name.trim()
        validate(name, request.slotMinutes, request.defaultPriceCents, request.memberDiscountPct, request.pausedDiscountPct)
        return withTransaction(db) {
            val club = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
            entitlements.requireWritable(club)
            val id = courts.create(
                clubId = clubId,
                name = name,
                surface = request.surface.name,
                indoor = request.indoor,
                slotMinutes = request.slotMinutes,
                defaultPriceCents = request.defaultPriceCents,
                memberDiscountPct = request.memberDiscountPct,
                pausedDiscountPct = request.pausedDiscountPct,
            )
            courts.findByIdAndClub(id, clubId)!!.toResponse()
        }
    }

    suspend fun update(clubId: Uuid, courtId: Uuid, request: UpdateCourtRequest): CourtResponse {
        val name = request.name.trim()
        validate(name, request.slotMinutes, request.defaultPriceCents, request.memberDiscountPct, request.pausedDiscountPct)
        return withTransaction(db) {
            val club = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
            entitlements.requireWritable(club)
            val updated = courts.update(
                id = courtId,
                clubId = clubId,
                name = name,
                surface = request.surface.name,
                indoor = request.indoor,
                active = request.active,
                slotMinutes = request.slotMinutes,
                defaultPriceCents = request.defaultPriceCents,
                memberDiscountPct = request.memberDiscountPct,
                pausedDiscountPct = request.pausedDiscountPct,
            )
            if (updated == 0) throw ApiException.notFound("Court not found")
            courts.findByIdAndClub(courtId, clubId)!!.toResponse()
        }
    }

    suspend fun list(clubId: Uuid, limit: Int, cursor: String?): Page<CourtResponse> {
        val cursorId = cursor?.let { runCatching { Uuid.parse(it) }.getOrNull() ?: throw ApiException.validation("Invalid cursor") }
        val pageSize = limit.coerceIn(1, MAX_PAGE_SIZE)
        val rows = withTransaction(db) { courts.listByClub(clubId, pageSize + 1, cursorId) }
        val page = rows.take(pageSize)
        return Page(
            items = page.map { it.toResponse() },
            nextCursor = if (rows.size > pageSize) page.last().id.toString() else null,
        )
    }

    private fun validate(name: String, slotMinutes: Int, defaultPriceCents: Int, memberDiscountPct: Int, pausedDiscountPct: Int) {
        if (name.isBlank() || name.length > 100) throw ApiException.validation("Name must be 1-100 characters")
        if (slotMinutes <= 0) throw ApiException.validation("slotMinutes must be > 0")
        if (defaultPriceCents < 0) throw ApiException.validation("defaultPriceCents must be >= 0")
        if (memberDiscountPct !in 0..100) throw ApiException.validation("memberDiscountPct must be 0-100")
        if (pausedDiscountPct !in 0..100) throw ApiException.validation("pausedDiscountPct must be 0-100")
    }

    companion object {
        private const val MAX_PAGE_SIZE = 50
    }
}

private fun CourtRow.toResponse() = CourtResponse(
    id = id.toString(),
    clubId = clubId.toString(),
    name = name,
    surface = CourtSurface.valueOf(surface),
    indoor = indoor,
    active = active,
    slotMinutes = slotMinutes,
    defaultPriceCents = defaultPriceCents,
    memberDiscountPct = memberDiscountPct,
    pausedDiscountPct = pausedDiscountPct,
)
