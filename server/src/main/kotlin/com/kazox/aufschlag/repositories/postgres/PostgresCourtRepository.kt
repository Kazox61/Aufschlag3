package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.api.court.CourtSurface
import com.kazox.aufschlag.db.CourtsTable
import com.kazox.aufschlag.repositories.CourtRepository
import com.kazox.aufschlag.repositories.CourtRow
import com.kazox.aufschlag.repositories.Keyset
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class PostgresCourtRepository : CourtRepository {

    override fun create(
        clubId: Uuid,
        name: String,
        surface: CourtSurface,
        indoor: Boolean,
        slotMinutes: Int,
        defaultPriceCents: Int,
        memberDiscountPct: Int,
        pausedDiscountPct: Int,
    ): Uuid {
        val id = Uuid.random()
        CourtsTable.insert {
            it[CourtsTable.id] = id
            it[CourtsTable.clubId] = clubId
            it[CourtsTable.name] = name
            it[CourtsTable.surface] = surface
            it[CourtsTable.indoor] = indoor
            it[CourtsTable.slotMinutes] = slotMinutes
            it[CourtsTable.defaultPriceCents] = defaultPriceCents
            it[CourtsTable.memberDiscountPct] = memberDiscountPct
            it[CourtsTable.pausedDiscountPct] = pausedDiscountPct
        }
        return id
    }

    override fun findByIdAndClub(id: Uuid, clubId: Uuid): CourtRow? =
        CourtsTable.selectAll()
            .where { (CourtsTable.id eq id) and (CourtsTable.clubId eq clubId) }
            .singleOrNull()
            ?.toCourtRow()

    override fun update(
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
    ): Int =
        CourtsTable.update({ (CourtsTable.id eq id) and (CourtsTable.clubId eq clubId) }) {
            it[CourtsTable.name] = name
            it[CourtsTable.surface] = surface
            it[CourtsTable.indoor] = indoor
            it[CourtsTable.active] = active
            it[CourtsTable.slotMinutes] = slotMinutes
            it[CourtsTable.defaultPriceCents] = defaultPriceCents
            it[CourtsTable.memberDiscountPct] = memberDiscountPct
            it[CourtsTable.pausedDiscountPct] = pausedDiscountPct
        }

    override fun listByClub(clubId: Uuid, limit: Int, cursor: Keyset?): List<CourtRow> =
        CourtsTable.selectAll()
            .where {
                val clubCondition = CourtsTable.clubId eq clubId
                val cursorCondition = keysetAfter(CourtsTable.createdAt, CourtsTable.id, cursor)
                clubCondition and cursorCondition
            }
            .orderBy(*keysetOrder(CourtsTable.createdAt, CourtsTable.id))
            .limit(limit)
            .map { it.toCourtRow() }

    private fun ResultRow.toCourtRow() = CourtRow(
        id = this[CourtsTable.id],
        clubId = this[CourtsTable.clubId],
        name = this[CourtsTable.name],
        surface = this[CourtsTable.surface],
        indoor = this[CourtsTable.indoor],
        active = this[CourtsTable.active],
        slotMinutes = this[CourtsTable.slotMinutes],
        defaultPriceCents = this[CourtsTable.defaultPriceCents],
        memberDiscountPct = this[CourtsTable.memberDiscountPct],
        pausedDiscountPct = this[CourtsTable.pausedDiscountPct],
        createdAt = this[CourtsTable.createdAt],
    )
}
